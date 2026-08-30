package com.example.tnttracker;

import com.example.tnttracker.config.ModConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.TntEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class TntTrackerMod implements ClientModInitializer {
    public record TimedPosition(Vec3d pos, long tick) {
    }

    private record RenderedTrail(
            List<TimedPosition> positions,
            List<Integer> dotIndices,
            List<Integer> labelIndices,
            long salveStartTick,
            boolean stationary
    ) {
    }

    private record HudLine(String label, String value, int valueColor) {
    }

    private static boolean scrollCallbackRegistered = false;
    private static int cachedTrailVersion = -1;
    private static double cachedGroupingDistance = Double.NaN;
    private static int cachedSalveGroupingTicks = -1;
    private static List<RenderedTrail> cachedRenderedTrails = List.of();

    public static boolean SODIUM_DETECTED = false;

    private static int hudX = 5;
    private static int hudY = 5;
    private static boolean draggingHud = false;
    private static int dragStartMouseX;
    private static int dragStartMouseY;
    private static int dragStartPanelX;
    private static int dragStartPanelY;

    public static void onPanelClosed() {
    }

    @Override
    public void onInitializeClient() {
        SODIUM_DETECTED = FabricLoader.getInstance().isModLoaded("sodium");
        KeybindManager.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            registerScrollCallback(client);
            KeybindManager.handlePresses(client);

            if (ModConfig.showTntPositions && client.world != null) {
                TntTracker.tick(client.world.getEntities(), client.world.getTime());
            }
        });

        WorldRenderEvents.LAST.register(TntTrackerMod::renderWorld);
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
    }

    private static void registerScrollCallback(MinecraftClient client) {
        if (scrollCallbackRegistered || client.getWindow() == null) {
            return;
        }

        scrollCallbackRegistered = true;
        long handle = client.getWindow().getHandle();
        AtomicReference<GLFWScrollCallbackI> oldCallbackRef = new AtomicReference<>();
        oldCallbackRef.set(GLFW.glfwSetScrollCallback(handle, (window, xoffset, yoffset) -> {
            boolean handled = false;
            if (ModConfig.replayActive && ReplayEngine.maxReplayTicks > 0) {
                boolean shiftDown = InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
                if (shiftDown) {
                    int step = (int)Math.signum(yoffset) * 2;
                    setReplayTick(ReplayEngine.replayTick + step);
                    handled = true;
                }
            }

            GLFWScrollCallbackI old = oldCallbackRef.get();
            if (!handled && old != null) {
                old.invoke(window, xoffset, yoffset);
            }
        }));
    }

    private static void setReplayTick(int tick) {
        int maxTick = ReplayEngine.maxReplayTicks - 1;
        ReplayEngine.replayTick = Math.max(0, Math.min(tick, maxTick));
        if (!ModConfig.replayPaused) {
            ReplayEngine.replayStartTime = System.currentTimeMillis()
                    - (long)(ReplayEngine.replayTick * 50.0 / ModConfig.replaySpeed);
        }
    }

    private static void renderWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || (!ModConfig.showTrails && !ModConfig.showTntPositions)) {
            return;
        }

        ReplayEngine.advanceTick();

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
        RenderSystem.depthMask(false);

        try {
            VertexFormat.DrawMode drawMode = SODIUM_DETECTED
                    ? VertexFormat.DrawMode.LINES
                    : VertexFormat.DrawMode.DEBUG_LINES;
            buffer.begin(drawMode, VertexFormats.POSITION_COLOR);
            renderTrailGeometry(matrices, buffer);
            renderActiveTntBoxes(matrices, buffer);
            drawLineBuffer(tessellator, buffer);
            renderTrailLabels(matrices, client);
        } finally {
            matrices.pop();
            RenderSystem.disablePolygonOffset();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableDepthTest();
        }
    }

    private static void drawLineBuffer(Tessellator tessellator, BufferBuilder buffer) {
        if (SODIUM_DETECTED) {
            RenderSystem.polygonOffset(-1.0f, -1.0f);
            RenderSystem.enablePolygonOffset();
            RenderLayer.getLines().draw(buffer, VertexSorter.BY_DISTANCE);
            RenderSystem.disablePolygonOffset();
        } else {
            tessellator.draw();
        }
    }

    private static void renderTrailGeometry(MatrixStack matrices, BufferBuilder buffer) {
        if (!ModConfig.showTrails) {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        List<RenderedTrail> renderedTrails = getRenderedTrails();
        for (int trailIdx = 0; trailIdx < renderedTrails.size(); trailIdx++) {
            RenderedTrail renderedTrail = renderedTrails.get(trailIdx);
            if (renderedTrail.positions().isEmpty()) {
                continue;
            }

            boolean isReplay = isReplayTrail(trailIdx);
            int drawCount = getDrawCount(renderedTrail.positions(), isReplay);
            if (drawCount <= 0) {
                continue;
            }

            float alpha = getTrailAlpha(isReplay);
            if (ModConfig.renderTrailAsDots) {
                if (renderedTrail.stationary() && !isReplay) {
                    continue;
                }
                renderDotBoxes(matrix, buffer, renderedTrail, drawCount, alpha);
                renderAxisSteps(matrix, buffer, renderedTrail.positions(), drawCount, alpha);
            } else {
                renderLineTrail(matrix, buffer, renderedTrail.positions(), drawCount, alpha);
            }

            renderExplosionMarker(matrix, buffer, renderedTrail.positions().get(drawCount - 1).pos(), alpha);
            renderReplayBox(matrix, buffer, renderedTrail.positions(), isReplay);
        }
    }

    private static void renderDotBoxes(Matrix4f matrix, BufferBuilder buffer, RenderedTrail renderedTrail,
                                       int drawCount, float alpha) {
        for (int pointIndex : renderedTrail.dotIndices()) {
            if (pointIndex >= drawCount) {
                break;
            }

            Vec3d point = renderedTrail.positions().get(pointIndex).pos();
            float t = (float)pointIndex / Math.max(1, drawCount - 1);
            double size = 0.49;
            drawBox(
                    matrix,
                    buffer,
                    point.x - size,
                    point.y,
                    point.z - size,
                    point.x + size,
                    point.y + size * 2.0,
                    point.z + size,
                    t,
                    1.0f - t,
                    0.0f,
                    alpha
            );
        }
    }

    private static void renderAxisSteps(Matrix4f matrix, BufferBuilder buffer, List<TimedPosition> trail,
                                        int drawCount, float alpha) {
        for (int i = 0; i < drawCount - 1; i++) {
            Vec3d from = trail.get(i).pos();
            Vec3d to = trail.get(i + 1).pos();
            if (from.squaredDistanceTo(to) < 0.0001) {
                continue;
            }

            double currentX = from.x;
            double currentY = from.y;
            double currentZ = from.z;
            for (ModConfig.Axis axis : ModConfig.drawOrder.axes()) {
                double nextX = currentX;
                double nextY = currentY;
                double nextZ = currentZ;
                double move = 0.0;
                float red = 0.0f;
                float green = 0.0f;
                float blue = 0.0f;

                switch (axis) {
                    case X -> {
                        nextX = to.x;
                        move = nextX - currentX;
                        red = 1.0f;
                    }
                    case Y -> {
                        nextY = to.y;
                        move = nextY - currentY;
                        green = 1.0f;
                    }
                    case Z -> {
                        nextZ = to.z;
                        move = nextZ - currentZ;
                        blue = 1.0f;
                    }
                }

                if (Math.abs(move) > 0.001) {
                    addLine(matrix, buffer, currentX, currentY, currentZ, nextX, nextY, nextZ,
                            red, green, blue, 0.8f * alpha);
                }

                currentX = nextX;
                currentY = nextY;
                currentZ = nextZ;
            }
        }
    }

    private static void renderLineTrail(Matrix4f matrix, BufferBuilder buffer, List<TimedPosition> trail,
                                        int drawCount, float alpha) {
        for (int i = 0; i < drawCount - 1; i++) {
            Vec3d p1 = trail.get(i).pos();
            Vec3d p2 = trail.get(i + 1).pos();
            float t = (float)i / Math.max(1, drawCount - 1);
            addLine(matrix, buffer, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, t, 1.0f - t, 0.0f, alpha);
        }
    }

    private static void renderExplosionMarker(Matrix4f matrix, BufferBuilder buffer, Vec3d pos, float alpha) {
        float size = (float)ModConfig.explosionXSize;
        addLine(matrix, buffer, pos.x - size, pos.y - size, pos.z, pos.x + size, pos.y + size, pos.z,
                1.0f, 0.0f, 0.0f, alpha);
        addLine(matrix, buffer, pos.x - size, pos.y + size, pos.z, pos.x + size, pos.y - size, pos.z,
                1.0f, 0.0f, 0.0f, alpha);
        addLine(matrix, buffer, pos.x - size, pos.y, pos.z - size, pos.x + size, pos.y, pos.z + size,
                1.0f, 0.3f, 0.0f, alpha * 0.8f);
        addLine(matrix, buffer, pos.x - size, pos.y, pos.z + size, pos.x + size, pos.y, pos.z - size,
                1.0f, 0.3f, 0.0f, alpha * 0.8f);
    }

    private static void renderReplayBox(Matrix4f matrix, BufferBuilder buffer, List<TimedPosition> trail,
                                        boolean isReplay) {
        if (!isReplay || ReplayEngine.replayTick >= trail.size()) {
            return;
        }

        Vec3d currentPos = trail.get(ReplayEngine.replayTick).pos();
        double boxSize = 0.6;
        drawBox(
                matrix,
                buffer,
                currentPos.x - boxSize / 2.0,
                currentPos.y,
                currentPos.z - boxSize / 2.0,
                currentPos.x + boxSize / 2.0,
                currentPos.y + boxSize,
                currentPos.z + boxSize / 2.0,
                1.0f,
                1.0f,
                1.0f,
                1.0f
        );
    }

    private static void renderActiveTntBoxes(MatrixStack matrices, BufferBuilder buffer) {
        if (!ModConfig.showTntPositions) {
            return;
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (TntEntity tnt : TntTracker.getActiveTntEntities()) {
            if (tnt.isRemoved()) {
                continue;
            }

            double size = 0.98;
            drawBox(
                    matrix,
                    buffer,
                    tnt.getX() - size / 2.0,
                    tnt.getY(),
                    tnt.getZ() - size / 2.0,
                    tnt.getX() + size / 2.0,
                    tnt.getY() + size,
                    tnt.getZ() + size / 2.0,
                    1.0f,
                    0.0f,
                    0.0f,
                    1.0f
            );
        }
    }

    private static void renderTrailLabels(MatrixStack matrices, MinecraftClient client) {
        if (!ModConfig.showTrails) {
            return;
        }

        VertexConsumerProvider.Immediate vertexConsumers = client.getBufferBuilders().getEntityVertexConsumers();
        List<RenderedTrail> renderedTrails = getRenderedTrails();
        for (int trailIdx = 0; trailIdx < renderedTrails.size(); trailIdx++) {
            RenderedTrail renderedTrail = renderedTrails.get(trailIdx);
            if (renderedTrail.positions().isEmpty()) {
                continue;
            }

            boolean isReplay = isReplayTrail(trailIdx);
            int drawCount = getDrawCount(renderedTrail.positions(), isReplay);
            if (drawCount <= 0) {
                continue;
            }

            float alpha = getTrailAlpha(isReplay);
            if (ModConfig.renderTrailAsDots && (!renderedTrail.stationary() || isReplay)) {
                renderTickLabels(matrices, client, vertexConsumers, renderedTrail, drawCount, alpha);
            }
            renderReplayLabel(matrices, client, vertexConsumers, renderedTrail.positions(), trailIdx, isReplay);
        }
        vertexConsumers.draw();
    }

    private static void renderTickLabels(MatrixStack matrices, MinecraftClient client,
                                         VertexConsumerProvider.Immediate vertexConsumers,
                                         RenderedTrail renderedTrail, int drawCount, float alpha) {
        for (int pointIndex : renderedTrail.labelIndices()) {
            if (pointIndex >= drawCount) {
                break;
            }

            TimedPosition timedPosition = renderedTrail.positions().get(pointIndex);
            float color = 1.0f - ((float)pointIndex / Math.max(1, drawCount - 1));
            int red = (int)(255 * (1.0f - color));
            int green = (int)(255 * color);
            int argb = ((int)(255 * alpha) << 24) | (red << 16) | (green << 8);
            drawWorldLabel(
                    matrices,
                    client,
                    vertexConsumers,
                    timedPosition.pos(),
                    1.5,
                    "T+" + (timedPosition.tick() - renderedTrail.salveStartTick()),
                    argb
            );
        }
    }

    private static void renderReplayLabel(MatrixStack matrices, MinecraftClient client,
                                          VertexConsumerProvider.Immediate vertexConsumers,
                                          List<TimedPosition> trail, int trailIdx, boolean isReplay) {
        if (!isReplay || ReplayEngine.replayTick >= trail.size()) {
            return;
        }

        drawWorldLabel(
                matrices,
                client,
                vertexConsumers,
                trail.get(ReplayEngine.replayTick).pos(),
                0.9,
                "#" + (trailIdx + 1),
                0xFFFFFFFF
        );
    }

    private static void drawWorldLabel(MatrixStack matrices, MinecraftClient client,
                                       VertexConsumerProvider.Immediate vertexConsumers,
                                       Vec3d pos, double yOffset, String label, int argb) {
        matrices.push();
        matrices.translate(pos.x, pos.y + yOffset, pos.z);
        matrices.multiply(client.gameRenderer.getCamera().getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        client.textRenderer.draw(
                label,
                -client.textRenderer.getWidth(label) / 2f,
                0,
                argb,
                false,
                matrix,
                vertexConsumers,
                TextRenderer.TextLayerType.NORMAL,
                0,
                15728880
        );
        matrices.pop();
    }

    private static void drawBox(Matrix4f matrix, BufferBuilder buffer,
                                double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ,
                                float red, float green, float blue, float alpha) {
        addLine(matrix, buffer, minX, minY, minZ, maxX, minY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, alpha);
        addLine(matrix, buffer, minX, minY, maxZ, minX, minY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrix, buffer, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, minX, minY, minZ, minX, maxY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, alpha);
        addLine(matrix, buffer, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, alpha);
        addLine(matrix, buffer, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, alpha);
    }

    private static void addLine(Matrix4f matrix, BufferBuilder buffer,
                                double x1, double y1, double z1,
                                double x2, double y2, double z2,
                                float red, float green, float blue, float alpha) {
        buffer.vertex(matrix, (float)x1, (float)y1, (float)z1).color(red, green, blue, alpha).next();
        buffer.vertex(matrix, (float)x2, (float)y2, (float)z2).color(red, green, blue, alpha).next();
    }

    private static List<RenderedTrail> getRenderedTrails() {
        int trailVersion = TntTracker.getTrailsVersion();
        if (cachedTrailVersion == trailVersion
                && Double.compare(cachedGroupingDistance, ModConfig.groupingDistance) == 0
                && cachedSalveGroupingTicks == ModConfig.salveGroupingTicks
                && cachedRenderedTrails.size() == TntTracker.savedTrails.size()) {
            return cachedRenderedTrails;
        }

        cachedTrailVersion = trailVersion;
        cachedGroupingDistance = ModConfig.groupingDistance;
        cachedSalveGroupingTicks = ModConfig.salveGroupingTicks;
        cachedRenderedTrails = buildRenderedTrails();
        return cachedRenderedTrails;
    }

    private static List<RenderedTrail> buildRenderedTrails() {
        double groupingDistanceSquared = ModConfig.groupingDistance * ModConfig.groupingDistance;
        Map<Integer, Long> salveStarts = computeSalveStartTicks();
        List<RenderedTrail> renderedTrails = new ArrayList<>(TntTracker.savedTrails.size());

        for (int trailIdx = 0; trailIdx < TntTracker.savedTrails.size(); trailIdx++) {
            List<TimedPosition> trail = TntTracker.savedTrails.get(trailIdx);
            List<Integer> dotIndices = new ArrayList<>();
            List<Integer> labelIndices = new ArrayList<>();
            Set<Long> labeledTicks = new HashSet<>();

            for (int i = 0; i < trail.size(); i++) {
                Vec3d point = trail.get(i).pos();
                boolean duplicate = false;
                for (int keptIndex : dotIndices) {
                    if (point.squaredDistanceTo(trail.get(keptIndex).pos()) < groupingDistanceSquared) {
                        duplicate = true;
                        break;
                    }
                }
                if (duplicate) {
                    continue;
                }

                dotIndices.add(i);
                if (labeledTicks.add(trail.get(i).tick())) {
                    labelIndices.add(i);
                }
            }

            boolean stationary = false;
            if (trail.size() > 1) {
                Vec3d first = trail.get(0).pos();
                Vec3d last = trail.get(trail.size() - 1).pos();
                stationary = first.squaredDistanceTo(last) < groupingDistanceSquared;
            }

            long salveStartTick = trail.isEmpty()
                    ? 0L
                    : salveStarts.getOrDefault(trailIdx, trail.get(0).tick());
            renderedTrails.add(new RenderedTrail(trail, dotIndices, labelIndices, salveStartTick, stationary));
        }

        return renderedTrails;
    }

    private static boolean isReplayTrail(int trailIdx) {
        return ModConfig.replayActive
                && trailIdx >= Math.max(0, ModConfig.replayTrailStart)
                && trailIdx <= ModConfig.replayTrailEnd;
    }

    private static int getDrawCount(List<TimedPosition> trail, boolean isReplay) {
        return isReplay ? Math.min(ReplayEngine.replayTick + 1, trail.size()) : trail.size();
    }

    private static float getTrailAlpha(boolean isReplay) {
        if (isReplay) {
            return 1.0f;
        }
        return ModConfig.replayActive ? 0.2f : 1.0f;
    }

    private static void renderHud(DrawContext drawContext) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ModConfig.showTrails || TntTracker.savedTrails.isEmpty()) {
            return;
        }

        renderReplayHud(drawContext, client);
        renderShotHud(drawContext, client);
        renderStatusPanel(drawContext, client);
    }

    private static void renderReplayHud(DrawContext drawContext, MinecraftClient client) {
        if (!ModConfig.replayActive) {
            return;
        }

        String progress = Text.translatable(
                "tnttracker.replay.scrub",
                ReplayEngine.replayTick,
                ReplayEngine.maxReplayTicks - 1
        ).getString();
        int x = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(progress) - 10;
        int y = client.getWindow().getScaledHeight() - 30;
        drawContext.drawTextWithShadow(client.textRenderer, progress, x, y, 0xFFFF55);

        String speed = Text.translatable("tnttracker.replay.speed", ModConfig.replaySpeed).getString();
        drawContext.drawTextWithShadow(client.textRenderer, speed, x, y + 12, 0xAAAAAA);
    }

    private static void renderShotHud(DrawContext drawContext, MinecraftClient client) {
        if (!ShotManager.isShotActive()) {
            return;
        }

        String shotInfo = Text.translatable(
                "tnttracker.message.shot_hud",
                ShotManager.getCurrentTotal()
        ).getString();
        int x = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(shotInfo) - 10;
        int y = client.getWindow().getScaledHeight() - 44;
        drawContext.drawTextWithShadow(client.textRenderer, shotInfo, x, y, 0x55FFFF);
    }

    private static void renderStatusPanel(DrawContext drawContext, MinecraftClient client) {
        int windowWidth = client.getWindow().getWidth();
        int windowHeight = client.getWindow().getHeight();
        int scaledWidth = client.getWindow().getScaledWidth();
        int scaledHeight = client.getWindow().getScaledHeight();

        int guiMouseX = (int)(client.mouse.getX() * scaledWidth / (double)windowWidth);
        int guiMouseY = (int)(client.mouse.getY() * scaledHeight / (double)windowHeight);
        boolean leftDown = GLFW.glfwGetMouseButton(
                client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_1
        ) == GLFW.GLFW_PRESS;

        int padding = 4;
        int lineHeight = 12;
        int titleHeight = 12;
        HudLine[] lines = getHudLines();

        int maxLineWidth = 0;
        for (HudLine line : lines) {
            int width = client.textRenderer.getWidth(line.label() + line.value());
            if (width > maxLineWidth) {
                maxLineWidth = width;
            }
        }

        int panelWidth = maxLineWidth + padding * 2;
        int panelHeight = lines.length * lineHeight + padding * 2;
        updateHudDrag(guiMouseX, guiMouseY, leftDown, panelWidth, panelHeight, scaledWidth, scaledHeight, titleHeight);

        drawContext.fill(hudX + 2, hudY + 2, hudX + panelWidth + 2, hudY + panelHeight + 2, 0x88000000);
        drawContext.fill(hudX, hudY, hudX + panelWidth, hudY + panelHeight, 0xCC222222);
        drawContext.drawHorizontalLine(hudX, hudX + panelWidth - 1, hudY, 0xFFFFFFFF);
        drawContext.drawHorizontalLine(hudX, hudX + panelWidth - 1, hudY + panelHeight - 1, 0xFFFFFFFF);
        drawContext.drawVerticalLine(hudX, hudY, hudY + panelHeight - 1, 0xFFFFFFFF);
        drawContext.drawVerticalLine(hudX + panelWidth - 1, hudY, hudY + panelHeight - 1, 0xFFFFFFFF);

        for (int i = 0; i < lines.length; i++) {
            HudLine line = lines[i];
            int labelX = hudX + padding;
            int lineY = hudY + padding + i * lineHeight;
            drawContext.drawTextWithShadow(client.textRenderer, line.label(), labelX, lineY, 0xFFAAAAAA);
            drawContext.drawTextWithShadow(
                    client.textRenderer,
                    line.value(),
                    labelX + client.textRenderer.getWidth(line.label()),
                    lineY,
                    line.valueColor()
            );
        }
    }

    private static void updateHudDrag(int mouseX, int mouseY, boolean leftDown, int panelWidth, int panelHeight,
                                      int scaledWidth, int scaledHeight, int titleHeight) {
        if (!draggingHud && leftDown
                && mouseX >= hudX && mouseX <= hudX + panelWidth
                && mouseY >= hudY && mouseY <= hudY + titleHeight) {
            draggingHud = true;
            dragStartMouseX = mouseX;
            dragStartMouseY = mouseY;
            dragStartPanelX = hudX;
            dragStartPanelY = hudY;
        }

        if (draggingHud) {
            if (leftDown) {
                hudX = dragStartPanelX + (mouseX - dragStartMouseX);
                hudY = dragStartPanelY + (mouseY - dragStartMouseY);
            } else {
                draggingHud = false;
                clampHud(panelWidth, panelHeight, scaledWidth, scaledHeight);
            }
        }

        if (hudX + panelWidth < 20 || hudX > scaledWidth - 20
                || hudY + titleHeight < 5 || hudY > scaledHeight - 5) {
            clampHud(panelWidth, panelHeight, scaledWidth, scaledHeight);
        }
    }

    private static void clampHud(int panelWidth, int panelHeight, int scaledWidth, int scaledHeight) {
        hudX = Math.max(0, Math.min(hudX, scaledWidth - panelWidth));
        hudY = Math.max(0, Math.min(hudY, scaledHeight - panelHeight));
    }

    private static HudLine[] getHudLines() {
        String stateOn = Text.translatable("tnttracker.state.on").getString();
        String stateOff = Text.translatable("tnttracker.state.off").getString();
        String dots = Text.translatable("tnttracker.state.dots").getString();
        String lines = Text.translatable("tnttracker.state.lines").getString();
        String rec = ModConfig.drawOrder == ModConfig.DrawOrder.YZX ? " (rec)" : "";

        return new HudLine[]{
                new HudLine(
                        Text.translatable("tnttracker.hud.label.tracking").getString(),
                        ModConfig.showTntPositions ? stateOn : stateOff,
                        ModConfig.showTntPositions ? 0xFF55FF55 : 0xFFFF5555
                ),
                new HudLine(
                        Text.translatable("tnttracker.hud.label.mode").getString(),
                        ModConfig.renderTrailAsDots ? dots : lines,
                        ModConfig.renderTrailAsDots ? 0xFFFFFF55 : 0xFF55FFFF
                ),
                new HudLine(
                        Text.translatable("tnttracker.hud.label.safe").getString(),
                        ModConfig.safeMode ? stateOn : stateOff,
                        ModConfig.safeMode ? 0xFF55FF55 : 0xFFFF5555
                ),
                new HudLine(
                        Text.translatable("tnttracker.hud.label.respawn").getString(),
                        ModConfig.respawnTnt ? stateOn : stateOff,
                        ModConfig.respawnTnt ? 0xFF55FF55 : 0xFFFF5555
                ),
                new HudLine(
                        Text.translatable("tnttracker.hud.label.group").getString(),
                        String.format(Locale.US, "%.2f", ModConfig.groupingDistance),
                        0xFFFFFFFF
                ),
                new HudLine(
                        Text.translatable("tnttracker.hud.label.order").getString(),
                        ModConfig.drawOrder.name() + rec,
                        ModConfig.drawOrder == ModConfig.DrawOrder.YZX ? 0xFF55FF55 : 0xFFAAAAAA
                )
        };
    }

    public static void exportTrailsToFile() {
        Path file = FabricLoader.getInstance().getGameDir().resolve("tnt_trails.csv");
        try (BufferedWriter writer = Files.newBufferedWriter(
                file,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writer.write("Trail,Step,Tick,X,Y,Z,Speed");
            writer.newLine();

            for (int trailIdx = 0; trailIdx < TntTracker.savedTrails.size(); trailIdx++) {
                List<TimedPosition> trail = TntTracker.savedTrails.get(trailIdx);
                for (int i = 0; i < trail.size(); i++) {
                    TimedPosition timedPosition = trail.get(i);
                    Vec3d pos = timedPosition.pos();
                    double speed = 0.0;
                    if (i > 0) {
                        speed = pos.distanceTo(trail.get(i - 1).pos()) * 20.0;
                    }

                    writer.write(String.format(
                            Locale.US,
                            "%d,%d,%d,%.6f,%.6f,%.6f,%.3f",
                            trailIdx + 1,
                            i + 1,
                            timedPosition.tick(),
                            pos.x,
                            pos.y,
                            pos.z,
                            speed
                    ));
                    writer.newLine();
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(TntTrackerCommon.MOD_ID).error("Failed to export trails", e);
        }
    }

    private static Map<Integer, Long> computeSalveStartTicks() {
        record TrailInfo(int index, long startTick) {
        }

        List<TrailInfo> infos = new ArrayList<>();
        for (int i = 0; i < TntTracker.savedTrails.size(); i++) {
            List<TimedPosition> trail = TntTracker.savedTrails.get(i);
            if (!trail.isEmpty()) {
                infos.add(new TrailInfo(i, trail.get(0).tick()));
            }
        }
        infos.sort(Comparator.comparingLong(TrailInfo::startTick));

        Map<Integer, Long> result = new HashMap<>();
        long groupRefTick = -1L;
        for (TrailInfo info : infos) {
            long startTick = info.startTick();
            if (groupRefTick == -1L || startTick - groupRefTick > ModConfig.salveGroupingTicks) {
                groupRefTick = startTick;
            }
            result.put(info.index(), groupRefTick);
        }
        return result;
    }
}
