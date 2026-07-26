package com.example.tnttracker.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class FlatSliderWidget extends SliderWidget {
    private final int bgColor, fillColor, textColor, borderColor;

    public FlatSliderWidget(int x, int y, int width, int height, Text message, double value,
                            int bgColor, int fillColor, int textColor, int borderColor) {
        super(x, y, width, height, message, value);
        this.bgColor = bgColor;
        this.fillColor = fillColor;
        this.textColor = textColor;
        this.borderColor = borderColor;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();

        // 1. Background – full widget area
        context.fill(getX(), getY(), getX() + width, getY() + height, bgColor);

        // 2. Filled portion – same height as the widget
        int fillWidth = (int) (this.value * width);
        if (fillWidth > 0) {
            context.fill(getX(), getY(), getX() + fillWidth, getY() + height, fillColor);
        }

        // 3. Border – around the full area
        context.drawBorder(getX(), getY(), width, height, borderColor);

        // 4. Text – centred on top of the fill
        int textWidth = client.textRenderer.getWidth(getMessage());
        context.drawTextWithShadow(client.textRenderer, getMessage(),
                getX() + width / 2 - textWidth / 2,
                getY() + (height - 8) / 2,
                textColor);
    }

    @Override
    protected void updateMessage() { }

    @Override
    protected void applyValue() { }
}