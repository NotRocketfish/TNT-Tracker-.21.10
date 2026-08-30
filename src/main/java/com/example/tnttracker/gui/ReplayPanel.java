package com.example.tnttracker.gui;

import com.example.tnttracker.ReplayEngine;
import com.example.tnttracker.TntTrackerMod;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.client.gui.Element;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class ReplayPanel extends Screen {
    private static final int WINDOW_WIDTH = 180;
    private static final int WINDOW_HEIGHT = 182;
    private static int lastX = -1, lastY = -1;
    private int x, y;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    private static final int BTN_BG = 0xFF333333;
    private static final int BTN_HOVER = 0xFF555555;
    private static final int BTN_TEXT = 0xFFFFFFFF;
    private static final int BTN_BORDER = 0xFF888888;
    private static final int SLIDER_BG = 0xFF333333;
    private static final int SLIDER_FILL = 0xFF00AAFF;
    private static final int SLIDER_TEXT = 0xFFFFFFFF;
    private static final int SLIDER_BORDER = 0xFF888888;

    public ReplayPanel() {
        super(Text.translatable("tnttracker.replay.title"));
    }

    private String getTooltipForPosition(int mouseX, int mouseY) {
        int btnWidth = 160;
        int btnX = x + (WINDOW_WIDTH - btnWidth) / 2;
        int startY = y + 20;
        int btnHeight = 16;
        int fieldWidth = 45;
        int labelWidth = 40;
        int endLabelX = btnX + labelWidth - 5 + fieldWidth;

        // Start field area (label + input)
        if (mouseX >= btnX - 5 && mouseX <= btnX - 5 + labelWidth + fieldWidth &&
                mouseY >= startY && mouseY <= startY + btnHeight)
            return Text.translatable("tnttracker.replay.range_tooltip_start").getString();

        // End field area
        if (mouseX >= endLabelX && mouseX <= endLabelX + labelWidth + fieldWidth &&
                mouseY >= startY && mouseY <= startY + btnHeight)
            return Text.translatable("tnttracker.replay.range_tooltip_end").getString();

        // Play/Pause
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 22 && mouseY <= startY + 22 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_play").getString();

        // Stop
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 40 && mouseY <= startY + 40 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_stop").getString();

        // Speed slider
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 58 && mouseY <= startY + 58 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_speed").getString();

        // Scrub slider (only if active)
        if (ModConfig.replayActive && ReplayEngine.maxReplayTicks > 0 &&
                mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 76 && mouseY <= startY + 76 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_scrub").getString();

        // Loop toggle
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 94 && mouseY <= startY + 94 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_loop").getString();

        // Export CSV
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 112 && mouseY <= startY + 112 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_export").getString();

        // Settings
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 130 && mouseY <= startY + 130 + btnHeight)
            return Text.translatable("tnttracker.replay.tooltip_back").getString();

        return null;
    }

    @Override
    public void close() {
        TntTrackerMod.onPanelClosed();
        super.close();
    }

    @Override
    protected void init() {
        if (lastX >= 0 && lastY >= 0) {
            x = lastX; y = lastY;
        } else {
            x = (width - WINDOW_WIDTH) / 2;
            y = (height - WINDOW_HEIGHT) / 2;
        }

        int btnWidth = 160;
        int btnX = x + (WINDOW_WIDTH - btnWidth) / 2;
        int startY = y + 20;
        int btnHeight = 16;

        // Range labels and text fields
        int fieldWidth = 45;
        int labelWidth = 40;
        int spacing = 0;

        // Reusable colored states.
        Text stateOn  = Text.translatable("tnttracker.state.on").formatted(Formatting.GREEN);
        Text stateOff = Text.translatable("tnttracker.state.off").formatted(Formatting.RED);

        // --- Start label ---
        addDrawableChild(new FlatButtonWidget(btnX - 5, startY, labelWidth, btnHeight,
                Text.translatable("tnttracker.replay.start_label"), null, 0x00000000, 0x00000000, 0xFFAAAAAA, 0x00000000) {
            @Override public void onClick(double mx, double my) {}
        });

        // --- Start text field ---
        TextFieldWidget startField = new TextFieldWidget(textRenderer, btnX + labelWidth - 5, startY, fieldWidth, btnHeight, Text.empty());
        startField.setText(String.valueOf(ModConfig.replayTrailStart));
        startField.setChangedListener(s -> {
            try { ModConfig.replayTrailStart = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(startField);

        // --- End label ---
        int endLabelX = btnX + labelWidth - 5 + fieldWidth + spacing;
        addDrawableChild(new FlatButtonWidget(endLabelX, startY, labelWidth, btnHeight,
                Text.translatable("tnttracker.replay.end_label"), null, 0x00000000, 0x00000000, 0xFFAAAAAA, 0x00000000) {
            @Override public void onClick(double mx, double my) {}
        });

        // --- End text field ---
        TextFieldWidget endField = new TextFieldWidget(textRenderer, endLabelX + labelWidth - 5, startY, fieldWidth, btnHeight, Text.empty());
        endField.setText(String.valueOf(ModConfig.replayTrailEnd));
        endField.setChangedListener(s -> {
            try { ModConfig.replayTrailEnd = Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        });
        addDrawableChild(endField);

        // Play / Pause
        Text playPauseText;
        if (!ModConfig.replayActive) {
            playPauseText = Text.translatable("tnttracker.replay.start");
        } else if (ModConfig.replayPaused) {
            playPauseText = Text.translatable("tnttracker.replay.play");
        } else {
            playPauseText = Text.translatable("tnttracker.replay.pause");
        }
        addDrawableChild(new FlatButtonWidget(btnX, startY + 22, btnWidth, btnHeight,
                playPauseText,
                btn -> {
                    if (!ModConfig.replayActive) {
                        ModConfig.replayActive = true;
                        ModConfig.replayPaused = false;
                        ReplayEngine.startReplay();
                    } else {
                        ModConfig.replayPaused = !ModConfig.replayPaused;
                    }
                    // Update button text
                    Text newText;
                    if (!ModConfig.replayActive) {
                        newText = Text.translatable("tnttracker.replay.start");
                    } else if (ModConfig.replayPaused) {
                        newText = Text.translatable("tnttracker.replay.play");
                    } else {
                        newText = Text.translatable("tnttracker.replay.pause");
                    }
                    btn.setMessage(newText);
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Stop
        addDrawableChild(new FlatButtonWidget(btnX, startY + 40, btnWidth, btnHeight,
                Text.translatable("tnttracker.replay.stop"),
                btn -> {
                    ModConfig.replayActive = false;
                    ReplayEngine.stopReplay();
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Speed slider
        addDrawableChild(new FlatSliderWidget(btnX, startY + 58, btnWidth, btnHeight,
                Text.translatable("tnttracker.replay.speed", String.format("%.1f", ModConfig.replaySpeed)),
                (ModConfig.replaySpeed - 0.1) / (2.0 - 0.1),
                SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
            @Override protected void updateMessage() {
                setMessage(Text.translatable("tnttracker.replay.speed", String.format("%.1f", ModConfig.replaySpeed)));
            }
            @Override protected void applyValue() {
                ModConfig.replaySpeed = 0.1 + this.value * (2.0 - 0.1);
            }
        });

        // Scrub slider (only when replay is active)
        if (ModConfig.replayActive && ReplayEngine.maxReplayTicks > 0) {
            addDrawableChild(new FlatSliderWidget(btnX, startY + 76, btnWidth, btnHeight,
                    Text.translatable("tnttracker.replay.scrub", ReplayEngine.replayTick, ReplayEngine.maxReplayTicks - 1),
                    (double) ReplayEngine.replayTick / Math.max(1, ReplayEngine.maxReplayTicks - 1),
                    SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
                @Override protected void updateMessage() {
                    int cur = (int) (this.value * (ReplayEngine.maxReplayTicks - 1));
                    setMessage(Text.translatable("tnttracker.replay.scrub", cur, ReplayEngine.maxReplayTicks - 1));
                }
                @Override protected void applyValue() {
                    int target = (int) (this.value * (ReplayEngine.maxReplayTicks - 1));
                    ReplayEngine.replayTick = target;
                    if (!ModConfig.replayPaused) {
                        ReplayEngine.replayStartTime = System.currentTimeMillis() - (long)(target * 50.0 / ModConfig.replaySpeed);
                    }
                }
            });
        }

        // Loop toggle
        Text loopState = ModConfig.replayLoop ? stateOn : stateOff;
        addDrawableChild(new FlatButtonWidget(btnX, startY + 94, btnWidth, btnHeight,
                Text.translatable("tnttracker.replay.loop", loopState),
                btn -> {
                    ModConfig.replayLoop = !ModConfig.replayLoop;
                    btn.setMessage(Text.translatable("tnttracker.replay.loop",
                            ModConfig.replayLoop ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Export button
        addDrawableChild(new FlatButtonWidget(btnX, startY + 112, btnWidth, btnHeight,
                Text.translatable("tnttracker.replay.export"),
                btn -> {
                    TntTrackerMod.exportTrailsToFile();
                    if (MinecraftClient.getInstance().player != null)
                        MinecraftClient.getInstance().player.sendMessage(
                                Text.translatable("tnttracker.message.export_csv").formatted(Formatting.GREEN), true);
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Button to switch back to Settings
        addDrawableChild(new FlatButtonWidget(btnX, startY + 130, btnWidth, btnHeight,
                Text.translatable("tnttracker.replay.settings"),
                btn -> {
                    if (client != null) {
                        client.setScreen(new SettingsOverlay());
                    }
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background and border.
        context.fill(x + 2, y + 2, x + WINDOW_WIDTH + 2, y + WINDOW_HEIGHT + 2, 0x88000000);
        context.fill(x, y, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xCC222222);
        int white = 0xFFFFFFFF;
        context.drawHorizontalLine(x, x + WINDOW_WIDTH - 1, y, white);
        context.drawHorizontalLine(x, x + WINDOW_WIDTH - 1, y + WINDOW_HEIGHT - 1, white);
        context.drawVerticalLine(x, y, y + WINDOW_HEIGHT - 1, white);
        context.drawVerticalLine(x + WINDOW_WIDTH - 1, y, y + WINDOW_HEIGHT - 1, white);
        context.drawCenteredTextWithShadow(textRenderer, title, x + WINDOW_WIDTH / 2, y + 6, 0xFFFFFF);

        // Render child widgets before tooltip.
        super.render(context, mouseX, mouseY, delta);

        // Draw tooltip last so it appears above the overlay.
        String tooltip = getTooltipForPosition(mouseX, mouseY);
        if (tooltip != null) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(0, 0, 400.0f);

            int bgColor = 0xEE222222;
            int borderColor = 0xFFFFFFFF;
            int textColor = 0xFFFFFF;
            int padding = 4;

            int textWidth = textRenderer.getWidth(tooltip);
            int textHeight = textRenderer.fontHeight;
            int boxWidth = textWidth + padding * 2;
            int boxHeight = textHeight + padding * 2;

            int drawX = mouseX + 12;
            int drawY = mouseY - boxHeight - 4;

            // Keep on screen
            if (drawX + boxWidth > width) drawX = mouseX - boxWidth - 12;
            if (drawY < 0) drawY = mouseY + 12;

            // Shadow
            context.fill(drawX + 2, drawY + 2, drawX + boxWidth + 2, drawY + boxHeight + 2, 0x88000000);
            // Background
            context.fill(drawX, drawY, drawX + boxWidth, drawY + boxHeight, bgColor);
            // Border
            context.drawHorizontalLine(drawX, drawX + boxWidth - 1, drawY, borderColor);
            context.drawHorizontalLine(drawX, drawX + boxWidth - 1, drawY + boxHeight - 1, borderColor);
            context.drawVerticalLine(drawX, drawY, drawY + boxHeight - 1, borderColor);
            context.drawVerticalLine(drawX + boxWidth - 1, drawY, drawY + boxHeight - 1, borderColor);
            // Text
            context.drawTextWithShadow(textRenderer, tooltip, drawX + padding, drawY + padding, textColor);

            matrices.pop();
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_1 && mouseX >= x && mouseX <= x + WINDOW_WIDTH && mouseY >= y && mouseY <= y + 14) {
            dragging = true;
            dragOffsetX = (int) (mouseX - x);
            dragOffsetY = (int) (mouseY - y);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            moveWindowTo((int) (mouseX - dragOffsetX), (int) (mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (ModConfig.replayActive && ReplayEngine.maxReplayTicks > 0) {
            int newTick = ReplayEngine.replayTick + (int)(verticalAmount * 1);
            newTick = Math.max(0, Math.min(newTick, ReplayEngine.maxReplayTicks - 1));
            ReplayEngine.replayTick = newTick;
            if (!ModConfig.replayPaused) {
                ReplayEngine.replayStartTime = System.currentTimeMillis() - (long)(newTick * 50.0 / ModConfig.replaySpeed);
            }
            clearChildren();
            init();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean shouldPause() { return false; }

    private void moveWindowTo(int newX, int newY) {
        newX = Math.max(0, Math.min(newX, width - WINDOW_WIDTH));
        newY = Math.max(0, Math.min(newY, height - WINDOW_HEIGHT));

        int dx = newX - x;
        int dy = newY - y;
        if (dx == 0 && dy == 0) {
            return;
        }

        x = newX;
        y = newY;
        lastX = x;
        lastY = y;

        for (Element child : children()) {
            if (child instanceof ClickableWidget widget) {
                widget.setX(widget.getX() + dx);
                widget.setY(widget.getY() + dy);
            }
        }
    }
}
