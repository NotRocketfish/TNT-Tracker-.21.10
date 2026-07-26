package com.example.tnttracker;

import com.mojang.blaze3d.systems.RenderSystem;
import com.example.tnttracker.config.ModConfig;
import com.example.tnttracker.item.ModItems;
import com.example.tnttracker.network.ClientTriggerPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TntTrackerMod implements ClientModInitializer {

	public record TimedPosition(Vec3d pos, long tick) {}

	private static boolean scrollCallbackRegistered = false;

	// HUD dragging
	private static int hudX = 5, hudY = 5;
	private static boolean draggingHud = false;
	private static int dragStartMouseX, dragStartMouseY;
	private static int dragStartPanelX, dragStartPanelY;

	public static void recordPosition(net.minecraft.entity.TntEntity tnt, Vec3d pos) {
		if (TntTracker.activeRecordings.containsKey(tnt)) {
			TntTracker.activeRecordings.get(tnt).add(new TimedPosition(pos, tnt.getWorld().getTime()));
		}
	}

	public static void onPanelClosed() {
		// reset any panel tracking if needed
	}

	@Override
	public void onInitializeClient() {
		KeybindManager.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// Register scroll callback once on first tick
			if (!scrollCallbackRegistered && client.getWindow() != null) {
				scrollCallbackRegistered = true;
				long handle = client.getWindow().getHandle();
				AtomicReference<GLFWScrollCallbackI> oldCallbackRef = new AtomicReference<>();
				oldCallbackRef.set(GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
					boolean handled = false;
					if (ModConfig.replayActive && ReplayEngine.maxReplayTicks > 0) {
						boolean shiftDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
								|| InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
						if (shiftDown) {
							int step = (int) Math.signum(yoffset) * 2;
							int newTick = ReplayEngine.replayTick + step;
							if (newTick < 0) newTick = 0;
							if (newTick >= ReplayEngine.maxReplayTicks) newTick = ReplayEngine.maxReplayTicks - 1;
							ReplayEngine.replayTick = newTick;
							if (!ModConfig.replayPaused) {
								ReplayEngine.replayStartTime = System.currentTimeMillis() - (long)(newTick * 50.0 / ModConfig.replaySpeed);
							}
							handled = true;
						}
					}
					GLFWScrollCallbackI old = oldCallbackRef.get();
					if (!handled && old != null) {
						old.invoke(window, xoffset, yoffset);
					}
				}));
			}

			KeybindManager.handlePresses(client);

			if (ModConfig.showTntPositions && client.world != null) {
				TntTracker.tick(client.world.getEntities());
			}
		});

		// ========== WORLD RENDERING ==========
		WorldRenderEvents.LAST.register(context -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client.world == null) return;

			MatrixStack matrices = context.matrixStack();
			Camera camera = context.camera();
			Tessellator tessellator = Tessellator.getInstance();
			BufferBuilder buffer = tessellator.getBuffer();

			matrices.push();
			matrices.translate(-camera.getPos().x, -camera.getPos().y, -camera.getPos().z);

			RenderSystem.disableDepthTest();
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.setShader(GameRenderer::getPositionColorProgram);

			// Advance replay tick
			ReplayEngine.advanceTick();

			// Draw trails
			if (ModConfig.showTrails) {
				for (int trailIdx = 0; trailIdx < TntTracker.savedTrails.size(); trailIdx++) {
					List<TimedPosition> trail = TntTracker.savedTrails.get(trailIdx);
					if (trail.isEmpty()) continue;

					boolean isReplay = ModConfig.replayActive &&
							trailIdx >= ModConfig.replayTrailStart &&
							trailIdx <= ModConfig.replayTrailEnd;
					int drawCount = isReplay ? Math.min(ReplayEngine.replayTick + 1, trail.size()) : trail.size();
					float globalAlpha = isReplay ? 1.0f : (ModConfig.replayActive ? 0.2f : 1.0f);

					// --- DOTS MODE ---
					if (ModConfig.renderTrailAsDots) {
						if (!isReplay && trail.size() > 1) {
							Vec3d first = trail.get(0).pos();
							Vec3d last = trail.get(trail.size() - 1).pos();
							if (first.squaredDistanceTo(last) < ModConfig.groupingDistance * ModConfig.groupingDistance)
								continue;
						}

						// Hitboxes
						buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
						for (int i = 0; i < drawCount; i++) {
							Vec3d point = trail.get(i).pos();
							boolean isDuplicate = false;
							for (int j = 0; j < i; j++) {
								if (point.squaredDistanceTo(trail.get(j).pos()) <
										ModConfig.groupingDistance * ModConfig.groupingDistance) {
									isDuplicate = true;
									break;
								}
							}
							if (isDuplicate) continue;

							float size = 0.49f;
							Box exactBox = new Box(point.x - size, point.y, point.z - size,
									point.x + size, point.y + size * 2, point.z + size);
							float t = (float) i / Math.max(1, drawCount - 1);
							WorldRenderer.drawBox(matrices, buffer, exactBox, t, 1.0f - t, 0.0f, globalAlpha);
						}
						tessellator.draw();

						// Tick labels
						Set<Long> labeledTicks = new HashSet<>();
						Map<Integer, Long> salveStarts = computeSalveStartTicks();
						long startTick = salveStarts.getOrDefault(trailIdx, trail.get(0).tick());   // <-- einmalig pro Trail

						for (int i = 0; i < drawCount; i++) {
							TimedPosition tp = trail.get(i);
							Vec3d point = tp.pos();
							long tick = tp.tick();

							if (!labeledTicks.add(tick)) continue;
							boolean isDuplicate = false;
							for (int j = 0; j < i; j++) {
								TimedPosition earlier = trail.get(j);
								if (point.squaredDistanceTo(earlier.pos()) <
										ModConfig.groupingDistance * ModConfig.groupingDistance) {
									isDuplicate = true;
									break;
								}
							}
							if (isDuplicate) continue;

							matrices.push();
							matrices.translate(point.x, point.y + 1.5, point.z);
							matrices.multiply(client.gameRenderer.getCamera().getRotation());
							matrices.scale(-0.025f, -0.025f, 0.025f);

							Matrix4f matrix4f = matrices.peek().getPositionMatrix();
							float color = 1.0f - ((float) i / Math.max(1, drawCount - 1));
							int r = (int)(255 * (1.0f - color));
							int g = (int)(255 * color);
							int argb = ((int)(255 * globalAlpha) << 24) | (r << 16) | (g << 8);

							long relativeTick = tick - startTick;    // startTick ist jetzt der Salven-Start (oder Fallback)
							String label = "T+" + relativeTick;
							client.textRenderer.draw(label,
									-client.textRenderer.getWidth(label) / 2f, 0,
									argb, false, matrix4f,
									client.getBufferBuilders().getEntityVertexConsumers(),
									TextRenderer.TextLayerType.NORMAL, 0, 15728880);

							matrices.pop();
						}

						// Axis-aligned steps
						buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
						for (int i = 0; i < drawCount - 1; i++) {
							Vec3d from = trail.get(i).pos();
							Vec3d to = trail.get(i + 1).pos();
							if (from.squaredDistanceTo(to) < 0.0001) continue;

							double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
							String[] order;
							float[][] colors;

							switch (ModConfig.drawOrder) {
								case XYZ -> {
									order = new String[]{"X", "Y", "Z"};
									colors = new float[][]{{1f,0f,0f,0.8f*globalAlpha}, {0f,1f,0f,0.8f*globalAlpha}, {0f,0f,1f,0.8f*globalAlpha}};
								}
								case XZY -> {
									order = new String[]{"X", "Z", "Y"};
									colors = new float[][]{{1f,0f,0f,0.8f*globalAlpha}, {0f,0f,1f,0.8f*globalAlpha}, {0f,1f,0f,0.8f*globalAlpha}};
								}
								case YXZ -> {
									order = new String[]{"Y", "X", "Z"};
									colors = new float[][]{{0f,1f,0f,0.8f*globalAlpha}, {1f,0f,0f,0.8f*globalAlpha}, {0f,0f,1f,0.8f*globalAlpha}};
								}
								case YZX -> {
									order = new String[]{"Y", "Z", "X"};
									colors = new float[][]{{0f,1f,0f,0.8f*globalAlpha}, {0f,0f,1f,0.8f*globalAlpha}, {1f,0f,0f,0.8f*globalAlpha}};
								}
								case ZXY -> {
									order = new String[]{"Z", "X", "Y"};
									colors = new float[][]{{0f,0f,1f,0.8f*globalAlpha}, {1f,0f,0f,0.8f*globalAlpha}, {0f,1f,0f,0.8f*globalAlpha}};
								}
								case ZYX -> {
									order = new String[]{"Z", "Y", "X"};
									colors = new float[][]{{0f,0f,1f,0.8f*globalAlpha}, {0f,1f,0f,0.8f*globalAlpha}, {1f,0f,0f,0.8f*globalAlpha}};
								}
								default -> throw new IllegalStateException("Unexpected draw order: " + ModConfig.drawOrder);
							}

							Vec3d current = from;
							for (int j = 0; j < 3; j++) {
								Vec3d next = current;
								double move = 0;
								switch (order[j]) {
									case "X" -> { next = new Vec3d(to.x, current.y, current.z); move = dx; }
									case "Y" -> { next = new Vec3d(current.x, to.y, current.z); move = dy; }
									case "Z" -> { next = new Vec3d(current.x, current.y, to.z); move = dz; }
								}
								if (Math.abs(move) > 0.001) {
									buffer.vertex(matrices.peek().getPositionMatrix(),
													(float) current.x, (float) current.y, (float) current.z)
											.color(colors[j][0], colors[j][1], colors[j][2], colors[j][3]).next();
									buffer.vertex(matrices.peek().getPositionMatrix(),
													(float) next.x, (float) next.y, (float) next.z)
											.color(colors[j][0], colors[j][1], colors[j][2], colors[j][3]).next();
								}
								current = next;
							}
						}
						tessellator.draw();

						// Explosion marker
						if (drawCount > 0) {
							Vec3d exp = trail.get(drawCount - 1).pos();
							float x = (float) exp.x, y = (float) exp.y, z = (float) exp.z;
							float size = (float) ModConfig.explosionXSize;
							buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y - size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y + size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y + size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y - size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y, z - size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y, z + size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y, z + size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y, z - size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							tessellator.draw();
						}

						// Replay white box
						if (isReplay && ReplayEngine.replayTick < trail.size()) {
							Vec3d currentPos = trail.get(ReplayEngine.replayTick).pos();
							float boxSize = 0.6f;
							Box currentBox = new Box(
									currentPos.x - boxSize/2, currentPos.y, currentPos.z - boxSize/2,
									currentPos.x + boxSize/2, currentPos.y + boxSize, currentPos.z + boxSize/2
							);
							buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
							WorldRenderer.drawBox(matrices, buffer, currentBox, 1.0f, 1.0f, 1.0f, 1.0f);
							tessellator.draw();

							matrices.push();
							matrices.translate(currentPos.x, currentPos.y + boxSize + 0.3, currentPos.z);
							matrices.multiply(client.gameRenderer.getCamera().getRotation());
							matrices.scale(-0.025f, -0.025f, 0.025f);
							Matrix4f matrix4f = matrices.peek().getPositionMatrix();
							String trailLabel = "#" + (trailIdx + 1);
							client.textRenderer.draw(trailLabel,
									-client.textRenderer.getWidth(trailLabel) / 2f, 0,
									0xFFFFFFFF, false, matrix4f,
									client.getBufferBuilders().getEntityVertexConsumers(),
									TextRenderer.TextLayerType.NORMAL, 0, 15728880);
							matrices.pop();
						}

					} else { // LINES MODE
						List<Vec3d> positions = new ArrayList<>();
						for (int i = 0; i < drawCount; i++) positions.add(trail.get(i).pos());

						if (!positions.isEmpty()) {
							buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
							for (int i = 0; i < positions.size() - 1; i++) {
								Vec3d p1 = positions.get(i), p2 = positions.get(i + 1);
								float t = (float) i / Math.max(1, positions.size() - 1);
								buffer.vertex(matrices.peek().getPositionMatrix(),
										(float) p1.x, (float) p1.y, (float) p1.z).color(t, 1.0f - t, 0.0f, globalAlpha).next();
								buffer.vertex(matrices.peek().getPositionMatrix(),
										(float) p2.x, (float) p2.y, (float) p2.z).color(t, 1.0f - t, 0.0f, globalAlpha).next();
							}
							tessellator.draw();

							// Explosion marker
							Vec3d exp = positions.get(positions.size() - 1);
							float x = (float) exp.x, y = (float) exp.y, z = (float) exp.z;
							float size = (float) ModConfig.explosionXSize;
							buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y - size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y + size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y + size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y - size, z).color(1f,0f,0f,globalAlpha).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y, z - size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y, z + size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x - size, y, z + size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							buffer.vertex(matrices.peek().getPositionMatrix(), x + size, y, z - size).color(1f,0.3f,0f,globalAlpha*0.8f).next();
							tessellator.draw();

							// Replay white box
							if (isReplay && ReplayEngine.replayTick < trail.size()) {
								Vec3d currentPos = trail.get(ReplayEngine.replayTick).pos();
								float boxSize = 0.6f;
								Box currentBox = new Box(
										currentPos.x - boxSize/2, currentPos.y, currentPos.z - boxSize/2,
										currentPos.x + boxSize/2, currentPos.y + boxSize, currentPos.z + boxSize/2
								);
								buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
								WorldRenderer.drawBox(matrices, buffer, currentBox, 1.0f, 1.0f, 1.0f, 1.0f);
								tessellator.draw();

								matrices.push();
								matrices.translate(currentPos.x, currentPos.y + boxSize + 0.3, currentPos.z);
								matrices.multiply(client.gameRenderer.getCamera().getRotation());
								matrices.scale(-0.025f, -0.025f, 0.025f);
								Matrix4f matrix4f = matrices.peek().getPositionMatrix();
								String trailLabel = "#" + (trailIdx + 1);
								client.textRenderer.draw(trailLabel,
										-client.textRenderer.getWidth(trailLabel) / 2f, 0,
										0xFFFFFFFF, false, matrix4f,
										client.getBufferBuilders().getEntityVertexConsumers(),
										TextRenderer.TextLayerType.NORMAL, 0, 15728880);
								matrices.pop();
							}
						}
					}
				}
			}

			// Live red boxes
			if (ModConfig.showTntPositions) {
				buffer.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
				synchronized (TntTracker.activeRecordings) {
					for (net.minecraft.entity.TntEntity tnt : TntTracker.activeRecordings.keySet()) {
						if (tnt.isRemoved()) continue;
						double x = tnt.getX(), y = tnt.getY(), z = tnt.getZ();
						float size = 0.98f;
						Box box = new Box(x - size/2, y, z - size/2, x + size/2, y + size, z + size/2);
						WorldRenderer.drawBox(matrices, buffer, box, 1.0f, 0.0f, 0.0f, 1.0f);
					}
				}
				tessellator.draw();
			}

			matrices.pop();
			RenderSystem.disableBlend();
			RenderSystem.enableDepthTest();
		});

		// ========== HUD OVERLAY ==========
		HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (!ModConfig.showTrails || TntTracker.savedTrails.isEmpty()) return;

			// Replay info
			if (ModConfig.replayActive) {
				// "Replay: T+%d/%d"
				String progress = Text.translatable(
						"tnttracker.replay.scrub",
						ReplayEngine.replayTick,
						ReplayEngine.maxReplayTicks - 1
				).getString();
				int x = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(progress) - 10;
				int y = client.getWindow().getScaledHeight() - 30;
				drawContext.drawTextWithShadow(client.textRenderer, progress, x, y, 0xFFFF55);

				// "Speed: %.1fx"
				String speed = Text.translatable(
						"tnttracker.replay.speed",
						ModConfig.replaySpeed
				).getString();
				drawContext.drawTextWithShadow(client.textRenderer, speed, x, y + 12, 0xAAAAAA);
			}

			// Shot summary HUD
			if (ShotManager.isShotActive()) {
				// "Shot: %d blocks"
				String shotInfo = Text.translatable(
						"tnttracker.message.shot_hud",
						ShotManager.getCurrentTotal()
				).getString();
				int x = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(shotInfo) - 10;
				int y = client.getWindow().getScaledHeight() - 44;
				drawContext.drawTextWithShadow(client.textRenderer, shotInfo, x, y, 0x55FFFF);
			}

			int windowWidth = client.getWindow().getWidth();
			int windowHeight = client.getWindow().getHeight();
			int scaledWidth = client.getWindow().getScaledWidth();
			int scaledHeight = client.getWindow().getScaledHeight();

			double rawMouseX = client.mouse.getX();
			double rawMouseY = client.mouse.getY();
			int guiMouseX = (int)(rawMouseX * scaledWidth / (double)windowWidth);
			int guiMouseY = (int)(rawMouseY * scaledHeight / (double)windowHeight);

			boolean leftDown = GLFW.glfwGetMouseButton(client.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS;

			int padding = 4;
			int lineHeight = 12;
			int titleHeight = 12;
			int bgColor = 0xCC222222;
			int borderColor = 0xFFFFFFFF;
			int labelColor = 0xFFAAAAAA;

			String[][] lines = getStrings();

			int maxLineWidth = 0;
			for (String[] line : lines) {
				int w = client.textRenderer.getWidth(line[0] + line[1]);
				if (w > maxLineWidth) maxLineWidth = w;
			}
			int panelWidth = maxLineWidth + padding * 2;
			int panelHeight = lines.length * lineHeight + padding * 2;

			// --- Drag start detection ---
			if (!draggingHud && leftDown &&
					guiMouseX >= hudX && guiMouseX <= hudX + panelWidth &&
					guiMouseY >= hudY && guiMouseY <= hudY + titleHeight) {
				draggingHud = true;
				dragStartMouseX = guiMouseX;
				dragStartMouseY = guiMouseY;
				dragStartPanelX = hudX;
				dragStartPanelY = hudY;
			}

			// --- Drag handling ---
			if (draggingHud) {
				if (leftDown) {
					hudX = dragStartPanelX + (guiMouseX - dragStartMouseX);
					hudY = dragStartPanelY + (guiMouseY - dragStartMouseY);
				} else {
					draggingHud = false;
					// Clamp on release
					hudX = Math.max(0, Math.min(hudX, scaledWidth - panelWidth));
					hudY = Math.max(0, Math.min(hudY, scaledHeight - panelHeight));
				}
			}

			// Safety nudge
			if (hudX + panelWidth < 20 || hudX > scaledWidth - 20 ||
					hudY + titleHeight < 5 || hudY > scaledHeight - 5) {
				hudX = Math.max(0, Math.min(hudX, scaledWidth - panelWidth));
				hudY = Math.max(0, Math.min(hudY, scaledHeight - panelHeight));
			}

			int panelX = hudX;
			int panelY = hudY;

			// Draw panel background & border
			drawContext.fill(panelX + 2, panelY + 2, panelX + panelWidth + 2, panelY + panelHeight + 2, 0x88000000);
			drawContext.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, bgColor);
			drawContext.drawHorizontalLine(panelX, panelX + panelWidth - 1, panelY, borderColor);
			drawContext.drawHorizontalLine(panelX, panelX + panelWidth - 1, panelY + panelHeight - 1, borderColor);
			drawContext.drawVerticalLine(panelX, panelY, panelY + panelHeight - 1, borderColor);
			drawContext.drawVerticalLine(panelX + panelWidth - 1, panelY, panelY + panelHeight - 1, borderColor);

			// Draw text lines
			for (int i = 0; i < lines.length; i++) {
				String label = lines[i][0];
				String value = lines[i][1];
				int valueColorHex = Integer.decode(lines[i][2]);
				int labelX = panelX + padding;
				int valueX = labelX + client.textRenderer.getWidth(label);
				int lineY = panelY + padding + i * lineHeight;
				drawContext.drawTextWithShadow(client.textRenderer, label, labelX, lineY, labelColor);
				drawContext.drawTextWithShadow(client.textRenderer, value, valueX, lineY, valueColorHex);
			}
		});

		// Server-side TNT respawn
		ServerTickEvents.END_WORLD_TICK.register(TntRespawnManager::tick);

		// Items
		ModItems.register();
		ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.REDSTONE)
				.register(content -> content.add(ModItems.REMOTE_TRIGGER));

		ClientTriggerPacket.register();
		ServerTaskScheduler.register();
		ShotManager.register();
	}

	private static String[] @NotNull [] getStrings() {
		String stateOn  = Text.translatable("tnttracker.state.on").getString();
		String stateOff = Text.translatable("tnttracker.state.off").getString();
		String dots     = Text.translatable("tnttracker.state.dots").getString();
		String linesStr = Text.translatable("tnttracker.state.lines").getString();

		// Für DrawOrder die Namen ebenfalls übersetzt ausgeben
		String orderName = switch (ModConfig.drawOrder) {
			case XYZ -> "XYZ";
			case XZY -> "XZY";
			case YXZ -> "YXZ";
			case YZX -> "YZX";
			case ZXY -> "ZXY";
			case ZYX -> "ZYX";
		};
		String rec = ModConfig.drawOrder == ModConfig.DrawOrder.YZX ? " (rec)" : "";

        return new String[][]{
                { Text.translatable("tnttracker.hud.label.tracking").getString(),
                        ModConfig.showTntPositions ? stateOn : stateOff,
                        ModConfig.showTntPositions ? "#55FF55" : "#FF5555" },
                { Text.translatable("tnttracker.hud.label.mode").getString(),
                        ModConfig.renderTrailAsDots ? dots : linesStr,
                        ModConfig.renderTrailAsDots ? "#FFFF55" : "#55FFFF" },
                { Text.translatable("tnttracker.hud.label.safe").getString(),
                        ModConfig.safeMode ? stateOn : stateOff,
                        ModConfig.safeMode ? "#55FF55" : "#FF5555" },
                { Text.translatable("tnttracker.hud.label.respawn").getString(),
                        ModConfig.respawnTnt ? stateOn : stateOff,
                        ModConfig.respawnTnt ? "#55FF55" : "#FF5555" },
                { Text.translatable("tnttracker.hud.label.group").getString(),
                        String.format("%.2f", ModConfig.groupingDistance),
                        "#FFFFFF" },
                { Text.translatable("tnttracker.hud.label.order").getString(),
                        orderName + rec,
                        ModConfig.drawOrder == ModConfig.DrawOrder.YZX ? "#55FF55" : "#AAAAAA" }
        };
	}

	public static void exportTrailsToFile() {
		try {
			Path file = FabricLoader.getInstance().getGameDir().resolve("tnt_trails.csv");
			BufferedWriter writer = Files.newBufferedWriter(file,
					StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
			writer.write("Trail,Step,Tick,X,Y,Z,Speed");
			writer.newLine();

			for (int trailIdx = 0; trailIdx < TntTracker.savedTrails.size(); trailIdx++) {
				List<TimedPosition> trail = TntTracker.savedTrails.get(trailIdx);
				for (int i = 0; i < trail.size(); i++) {
					TimedPosition tp = trail.get(i);
					Vec3d pos = tp.pos();
					long tick = tp.tick();
					double speed = 0;
					if (i > 0) {
						Vec3d prev = trail.get(i - 1).pos();
						speed = pos.distanceTo(prev) * 20.0;
					}
					writer.write(String.format(Locale.US, "%d,%d,%d,%.6f,%.6f,%.6f,%.3f",
							trailIdx + 1, i + 1, tick, pos.x, pos.y, pos.z, speed));
					writer.newLine();
				}
			}
			writer.close();
		} catch (Exception e) {
			LoggerFactory.getLogger("tnttracker").error("Failed to export trails", e);
		}
	}

	private static Map<Integer, Long> computeSalveStartTicks() {
		if (TntTracker.savedTrails.isEmpty()) return Map.of();

		record TrailInfo(int index, long startTick) {}
		List<TrailInfo> infos = new ArrayList<>();
		for (int i = 0; i < TntTracker.savedTrails.size(); i++) {
			List<TntTrackerMod.TimedPosition> trail = TntTracker.savedTrails.get(i);
			if (trail.isEmpty()) continue;
			infos.add(new TrailInfo(i, trail.get(0).tick()));
		}
		infos.sort(Comparator.comparingLong(TrailInfo::startTick));

		Map<Integer, Long> result = new HashMap<>();
		long groupRefTick = -1;   // Marker: noch keine Gruppe

		for (TrailInfo info : infos) {
			long startTick = info.startTick();
			if (groupRefTick == -1 || (startTick - groupRefTick) > ModConfig.salveGroupingTicks) {
				// Neue Gruppe beginnen
				groupRefTick = startTick;
			}
			result.put(info.index(), groupRefTick);
		}
		return result;
	}
}