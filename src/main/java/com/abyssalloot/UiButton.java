package com.abyssalloot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * MOD の GUI 用ボタン (クライアント専用)。バニラの石ボタンの代わりに UiKit の配色で描く.
 * 左の色帯 (accent) で系統を示し、ホバーで枠が点灯・文字が金色になる。押し込み中は 1px 沈む。
 */
public class UiButton extends Button {
    private final int accent;

    protected UiButton(int x, int y, int w, int h, Component msg, OnPress onPress, int accent) {
        super(x, y, w, h, msg, onPress, DEFAULT_NARRATION);
        this.accent = accent;
    }

    public static UiButton of(String text, int x, int y, int w, int h, int accent, Runnable onClick) {
        return new UiButton(x, y, w, h, Component.literal(text), b -> onClick.run(), accent);
    }

    @Override
    protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hover = isHoveredOrFocused() && active;
        boolean pressed = hover && Minecraft.getInstance().mouseHandler.isLeftPressed();
        int top = !active ? 0xF0201E2C : hover ? 0xF03A3460 : 0xF02A2648;
        int bot = !active ? 0xF0181622 : hover ? 0xF0262142 : 0xF01A172F;
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, UiKit.EDGE_DARK);
        g.fillGradient(x, y, x + w, y + h, top, bot);
        g.renderOutline(x, y, w, h, hover ? accent : UiKit.EDGE);
        if (!pressed) g.hLine(x + 1, x + w - 2, y + 1, 0x28FFFFFF);
        g.hLine(x + 1, x + w - 2, y + h - 2, 0x40000000);
        g.fill(x + 1, y + 1, x + 3, y + h - 1, active ? accent : UiKit.alpha(accent, 0x50));
        int color = !active ? UiKit.MUTED : hover ? UiKit.TITLE : UiKit.TEXT;
        var font = Minecraft.getInstance().font;
        g.drawCenteredString(font, getMessage(), x + w / 2 + 1, y + (h - 8) / 2 + (pressed ? 1 : 0), color);
    }
}
