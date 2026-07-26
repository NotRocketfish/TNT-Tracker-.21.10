package com.example.tnttracker.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class FlatButtonWidget extends ButtonWidget {
    private final int bgColor, hoverColor, textColor, borderColor;

    public FlatButtonWidget(int x, int y, int width, int height, Text message,
                            PressAction onPress, int bgColor, int hoverColor, int textColor, int borderColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.bgColor = bgColor;
        this.hoverColor = hoverColor;
        this.textColor = textColor;
        this.borderColor = borderColor;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int color = isHovered() ? hoverColor : bgColor;
        context.fill(getX(), getY(), getX() + width, getY() + height, color);
        context.drawBorder(getX(), getY(), width, height, borderColor);
        int textWidth = client.textRenderer.getWidth(getMessage());
        context.drawTextWithShadow(client.textRenderer, getMessage(),
                getX() + width / 2 - textWidth / 2,
                getY() + (height - 8) / 2,
                textColor);
    }
}