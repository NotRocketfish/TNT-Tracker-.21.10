package com.example.tnttracker.gui;

import com.example.tnttracker.TntTrackerMod;
import com.example.tnttracker.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class SettingsOverlay extends Screen {
    private static final int WINDOW_WIDTH = 180;
    private static final int WINDOW_HEIGHT = 260;
    private static int lastX = -1, lastY = -1;
    private int x, y;
    private boolean dragging = false;
    private int dragOffsetX, dragOffsetY;

    // Colours
    private static final int BTN_BG = 0xFF333333;
    private static final int BTN_HOVER = 0xFF555555;
    private static final int BTN_TEXT = 0xFFFFFFFF;
    private static final int BTN_BORDER = 0xFF888888;
    private static final int SLIDER_BG = 0xFF333333;
    private static final int SLIDER_FILL = 0xFF00AAFF;
    private static final int SLIDER_TEXT = 0xFFFFFFFF;
    private static final int SLIDER_BORDER = 0xFF888888;

    public SettingsOverlay() {
        super(Text.translatable("category.tnttracker"));  // oder eigener Key, z.B. "tnttracker.gui.settings_title"
    }

    private String getTooltipForPosition(int mouseX, int mouseY) {
        int btnWidth = 150;
        int btnX = x + (WINDOW_WIDTH - btnWidth) / 2;
        int startY = y + 20;
        int btnHeight = 16;

        // Tracking
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY && mouseY <= startY + btnHeight)
            return Text.translatable("tnttracker.gui.tracking").getString();
        // Trails
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 18 && mouseY <= startY + 18 + btnHeight)
            return Text.translatable("tnttracker.gui.trails").getString();
        // Mode
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 36 && mouseY <= startY + 36 + btnHeight)
            return Text.translatable("tnttracker.gui.mode").getString();
        // Safe
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 54 && mouseY <= startY + 54 + btnHeight)
            return Text.translatable("tnttracker.gui.safe").getString();
        // Group slider
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 72 && mouseY <= startY + 72 + btnHeight)
            return Text.translatable("tnttracker.gui.group").getString();
        // Order
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 90 && mouseY <= startY + 90 + btnHeight)
            return Text.translatable("tnttracker.gui.order").getString();
        // X Size
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 108 && mouseY <= startY + 108 + btnHeight)
            return Text.translatable("tnttracker.gui.xsize").getString();
        // Respawn TNT
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 126 && mouseY <= startY + 126 + btnHeight)
            return Text.translatable("tnttracker.gui.respawn").getString();
        // Cooldown
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 144 && mouseY <= startY + 144 + btnHeight)
            return Text.translatable("tnttracker.gui.respawn_cooldown").getString();
        // Shot Summary
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 162 && mouseY <= startY + 162 + btnHeight)
            return Text.translatable("tnttracker.gui.shot_summary").getString();
        // Salve Group slider
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 180 && mouseY <= startY + 180 + btnHeight)
            return Text.translatable("tnttracker.gui.salve_group").getString();
        // Replay Panel
        if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= startY + 198 && mouseY <= startY + 198 + btnHeight)
            return Text.translatable("tnttracker.gui.replay_panel").getString();

        return null;
    }

    @Override
    protected void init() {
        if (lastX >= 0 && lastY >= 0) {
            x = lastX; y = lastY;
        } else {
            x = (width - WINDOW_WIDTH) / 2;
            y = (height - WINDOW_HEIGHT) / 2;
        }

        // Vorbereitete farbige Zustände
        Text stateOn  = Text.translatable("tnttracker.state.on").formatted(Formatting.GREEN);
        Text stateOff = Text.translatable("tnttracker.state.off").formatted(Formatting.RED);
        Text modeDots  = Text.translatable("tnttracker.state.dots").formatted(Formatting.YELLOW);
        Text modeLines = Text.translatable("tnttracker.state.lines").formatted(Formatting.AQUA);

        int btnWidth = 150;
        int btnX = x + (WINDOW_WIDTH - btnWidth) / 2;
        int startY = y + 20;
        int btnHeight = 16;

        // --- Tracking ---
        addDrawableChild(new FlatButtonWidget(btnX, startY, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.tracking", ModConfig.showTntPositions ? stateOn : stateOff),
                btn -> {
                    ModConfig.showTntPositions = !ModConfig.showTntPositions;
                    btn.setMessage(Text.translatable("tnttracker.setting.tracking",
                            ModConfig.showTntPositions ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // --- Trails ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 18, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.trails", ModConfig.showTrails ? stateOn : stateOff),
                btn -> {
                    ModConfig.showTrails = !ModConfig.showTrails;
                    btn.setMessage(Text.translatable("tnttracker.setting.trails",
                            ModConfig.showTrails ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // --- Mode (Dots/Lines) ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 36, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.mode", ModConfig.renderTrailAsDots ? modeDots : modeLines),
                btn -> {
                    ModConfig.renderTrailAsDots = !ModConfig.renderTrailAsDots;
                    btn.setMessage(Text.translatable("tnttracker.setting.mode",
                            ModConfig.renderTrailAsDots ? modeDots : modeLines));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // --- Safe ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 54, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.safe", ModConfig.safeMode ? stateOn : stateOff),
                btn -> {
                    ModConfig.safeMode = !ModConfig.safeMode;
                    btn.setMessage(Text.translatable("tnttracker.setting.safe",
                            ModConfig.safeMode ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Grouping slider
        final String groupLabel = Text.translatable("tnttracker.label.group").getString();
        addDrawableChild(new FlatSliderWidget(btnX, startY + 72, btnWidth, btnHeight,
                Text.literal(groupLabel + " " + String.format("%.2f", ModConfig.groupingDistance)),
                (ModConfig.groupingDistance - 0.01) / (2.0 - 0.01),
                SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(groupLabel + " " + String.format("%.2f", ModConfig.groupingDistance)));
            }
            @Override protected void applyValue() {
                ModConfig.groupingDistance = 0.01 + this.value * (2.0 - 0.01);
            }
        });

        // --- Draw order button ---
        Text recText = ModConfig.drawOrder == ModConfig.DrawOrder.YZX
                ? Text.literal(" (rec)") : Text.empty();
        addDrawableChild(new FlatButtonWidget(btnX, startY + 90, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.order",
                        Text.literal(ModConfig.drawOrder.name()), recText),
                btn -> {
                    ModConfig.DrawOrder[] orders = ModConfig.DrawOrder.values();
                    int next = (ModConfig.drawOrder.ordinal() + 1) % orders.length;
                    ModConfig.drawOrder = orders[next];
                    Text newRec = ModConfig.drawOrder == ModConfig.DrawOrder.YZX
                            ? Text.literal(" (rec)") : Text.empty();
                    btn.setMessage(Text.translatable("tnttracker.setting.order",
                            Text.literal(ModConfig.drawOrder.name()), newRec));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // X size slider
        final String xSizeLabel = Text.translatable("tnttracker.label.xsize").getString();
        addDrawableChild(new FlatSliderWidget(btnX, startY + 108, btnWidth, btnHeight,
                Text.literal(xSizeLabel + " " + String.format("%.1f", ModConfig.explosionXSize)),
                (ModConfig.explosionXSize - 0.1) / (1.0 - 0.1),
                SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(xSizeLabel + " " + String.format("%.1f", ModConfig.explosionXSize)));
            }
            @Override protected void applyValue() {
                ModConfig.explosionXSize = 0.1 + this.value * (1.0 - 0.1);
            }
        });

        // --- TNT Respawn ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 126, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.respawn", ModConfig.respawnTnt ? stateOn : stateOff),
                btn -> {
                    ModConfig.respawnTnt = !ModConfig.respawnTnt;
                    btn.setMessage(Text.translatable("tnttracker.setting.respawn",
                            ModConfig.respawnTnt ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Respawn TNT cooldown slider
        final String cooldownLabel = Text.translatable("tnttracker.label.respawn_cooldown").getString();
        addDrawableChild(new FlatSliderWidget(btnX, startY + 144, btnWidth, btnHeight,
                Text.literal(cooldownLabel + " " + ModConfig.respawnTntCooldownTicks + " ticks"),
                (ModConfig.respawnTntCooldownTicks) / (300.0 - 0),
                SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(cooldownLabel + " " + ModConfig.respawnTntCooldownTicks + " ticks"));
            }
            @Override protected void applyValue() {
                ModConfig.respawnTntCooldownTicks = (int)(0 + this.value * (300.0 - 0));
            }
        });

        // --- Shot Summary Toggle ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 162, btnWidth, btnHeight,
                Text.translatable("tnttracker.setting.shot_summary", ModConfig.enableShotSummary ? stateOn : stateOff),
                btn -> {
                    ModConfig.enableShotSummary = !ModConfig.enableShotSummary;
                    btn.setMessage(Text.translatable("tnttracker.setting.shot_summary",
                            ModConfig.enableShotSummary ? stateOn : stateOff));
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));

        // Salve Grouping slider
        final String salveLabel = Text.translatable("tnttracker.label.salve_group").getString();
        addDrawableChild(new FlatSliderWidget(btnX, startY + 180, btnWidth, btnHeight,
                Text.literal(salveLabel + " " + ModConfig.salveGroupingTicks + " ticks"),
                (ModConfig.salveGroupingTicks) / (100.0 - 0),
                SLIDER_BG, SLIDER_FILL, SLIDER_TEXT, SLIDER_BORDER) {
            @Override protected void updateMessage() {
                setMessage(Text.literal(salveLabel + " " + ModConfig.salveGroupingTicks + " ticks"));
            }
            @Override protected void applyValue() {
                ModConfig.salveGroupingTicks = (int)(0 + this.value * (100.0 - 0));
            }
        });

        // --- Replay Panel button ---
        addDrawableChild(new FlatButtonWidget(btnX, startY + 198, btnWidth, btnHeight,
                Text.translatable("tnttracker.settings.replay_panel"),
                btn -> {
                    if (client != null) {
                        client.setScreen(new ReplayPanel());
                    }
                }, BTN_BG, BTN_HOVER, BTN_TEXT, BTN_BORDER));
    }

    @Override public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {}
    @Override public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drop shadow
        context.fill(x + 2, y + 2, x + WINDOW_WIDTH + 2, y + WINDOW_HEIGHT + 2, 0x88000000);
        // Background
        context.fill(x, y, x + WINDOW_WIDTH, y + WINDOW_HEIGHT, 0xCC222222);
        // Border
        int white = 0xFFFFFFFF;
        context.drawHorizontalLine(x, x + WINDOW_WIDTH - 1, y, white);
        context.drawHorizontalLine(x, x + WINDOW_WIDTH - 1, y + WINDOW_HEIGHT - 1, white);
        context.drawVerticalLine(x, y, y + WINDOW_HEIGHT - 1, white);
        context.drawVerticalLine(x + WINDOW_WIDTH - 1, y, y + WINDOW_HEIGHT - 1, white);
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, x + WINDOW_WIDTH / 2, y + 6, 0xFFFFFF);
        // Autor-Credit unten mittig
        String credit = "TNT Tracker v1.0 by Pepino16";  // ← ersetze DeinName mit deinem Namen
        int creditColor = 0x33AAAAAA; // dezentes Grau
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.literal(credit),
                x + WINDOW_WIDTH / 2,
                y + WINDOW_HEIGHT - 10,    // 10 Pixel über dem unteren Rand
                creditColor
        );
        // Draw buttons FIRST
        super.render(context, mouseX, mouseY, delta);

        // --- Tooltip drawn LAST so it appears on top of everything ---
        String tooltip = getTooltipForPosition(mouseX, mouseY);
        if (tooltip != null) {
            MatrixStack matrices = context.getMatrices();
            matrices.push();
            matrices.translate(0, 0, 400.0f);   // Draw on top of everything

            int bgColor = 0xEE222222;
            int borderColor = 0xFFFFFFFF;
            int textColor = 0xFFFFFF;
            int padding = 4;

            int textWidth = textRenderer.getWidth(tooltip);
            int textHeight = textRenderer.fontHeight;
            int boxWidth = textWidth + padding * 2;
            int boxHeight = textHeight + padding * 2;

            // Position: right of cursor, slightly above
            int drawX = mouseX + 12;
            int drawY = mouseY - boxHeight - 4;

            // Keep tooltip on screen
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

    @Override
    public void close() {
        TntTrackerMod.onPanelClosed();
        super.close();
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
            x = (int) (mouseX - dragOffsetX);
            y = (int) (mouseY - dragOffsetY);

            // Clamp to keep the entire window on screen
            x = Math.max(0, Math.min(x, width - WINDOW_WIDTH));
            y = Math.max(0, Math.min(y, height - WINDOW_HEIGHT));

            lastX = x; lastY = y;
            clearChildren(); init();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) { dragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean shouldPause() { return false; }
}