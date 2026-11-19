package dev.ftadvisor;

import net.minecraft.client.gui.DrawContext;

public final class Gfx {
    /** Скруглённый прямоугольник из горизонтальных полос. */
    public static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < h; i++) {
            int inset = 0;
            int d = -1;
            if (i < r) d = r - i;
            else if (i >= h - r) d = i - (h - r) + 1;
            if (d > 0) {
                double dy = d - 0.5;
                inset = r - (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
            }
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
    }

    public static int lerp(int a, int b, float t) {
        int ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
