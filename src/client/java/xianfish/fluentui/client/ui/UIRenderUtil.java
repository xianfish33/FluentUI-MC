package xianfish.fluentui.client.ui;

public final class UIRenderUtil {
    private UIRenderUtil() {}

    public static final int COLOR_BLACK_SEMI = 0x80000000;
    public static final int COLOR_BLACK_75 = 0xC0000000;
    public static final int COLOR_BLACK_50 = 0x80000000;
    public static final int COLOR_BLACK_25 = 0x40000000;
    public static final int COLOR_BLACK_12 = 0x1F000000;

    public static final int COLOR_BLUE_ACCENT = 0xFF3B82F6;
    public static final int COLOR_BLUE_ACCENT_SEMI = 0x803B82F6;
    public static final int COLOR_BLUE_DARK = 0xFF1D4ED8;
    public static final int COLOR_BLUE_LIGHT = 0xFF60A5FA;
    public static final int COLOR_BLUE_HOVER = 0xFF2563EB;

    public static final int COLOR_WHITE = 0xFFFFFFFF;
    public static final int COLOR_WHITE_SEMI = 0x80FFFFFF;

    public static int alpha(int color, float alpha) {
        int a = Math.round(alpha * ((color >> 24) & 0xFF));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int lerpColor(int from, int to, float t) {
        int a = lerpComponent((from >> 24) & 0xFF, (to >> 24) & 0xFF, t);
        int r = lerpComponent((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = lerpComponent((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = lerpComponent(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpComponent(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
