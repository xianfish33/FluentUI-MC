package xianfish.fluentui.client.ui.element;

public final class Colors {
    private Colors() {}

    public static final int WHITE      = 0xFFFFFFFF;
    public static final int WHITE_50   = 0x80FFFFFF;
    public static final int WHITE_20   = 0x33FFFFFF;
    public static final int BLACK_50   = 0x80000000;
    public static final int BLACK_75   = 0xC0000000;
    public static final int BLUE       = 0xFF3B82F6;
    public static final int BLUE_50    = 0x803B82F6;
    public static final int BLUE_DARK  = 0xFF1D4ED8;
    public static final int GRAY_30    = 0x4D808080;
    public static final int GRAY_50    = 0x80808080;

    public static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >> 24) & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >> 24) & 0xFF;
        return ((Math.round(aa + (ba - aa) * t) & 0xFF) << 24)
             | ((Math.round(ar + (br - ar) * t) & 0xFF) << 16)
             | ((Math.round(ag + (bg - ag) * t) & 0xFF) << 8)
             | (Math.round(ab + (bb - ab) * t) & 0xFF);
    }

    /** 向黑色加深（保留透明度不变），t 越大越深 */
    public static int darken(int color, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return lerp(color, 0xFF000000, t);
    }
}
