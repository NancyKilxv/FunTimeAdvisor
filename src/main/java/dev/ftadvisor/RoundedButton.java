package dev.ftadvisor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Скруглённая кнопка с плавной подсветкой при наведении. */
public class RoundedButton extends ButtonWidget {
    private float hover = 0f;
    public boolean selected = false;
    private final int radius;

    public RoundedButton(int x, int y, int w, int h, Text msg, PressAction action) {
        this(x, y, w, h, 8, msg, action);
    }

    public RoundedButton(int x, int y, int w, int h, int radius, Text msg, PressAction action) {
        super(x, y, w, h, msg, action, DEFAULT_NARRATION_SUPPLIER);
        this.radius = radius;
    }

    @Override
    public void renderWidget(DrawContext c, int mouseX, int mouseY, float delta) {
        boolean hov = this.active && this.isHovered();
        hover += ((hov ? 1f : 0f) - hover) * 0.25f;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (this.active && !selected) Gfx.rrect(c, x, y + 2, w, h, radius, 0x66000000);
        int base = selected ? 0xFF4752C4 : 0xFF3A3F5C;
        int col = this.active ? Gfx.lerp(base, 0xFF6B77FF, hover) : 0xFF262833;
        Gfx.rrect(c, x, y, w, h, radius, col);
        int tc = this.active ? 0xFFFFFFFF : 0xFF6E7080;
        var tr = MinecraftClient.getInstance().textRenderer;
        c.drawText(tr, getMessage(), x + (w - tr.getWidth(getMessage())) / 2, y + (h - 8) / 2, tc, this.active);
    }
}
