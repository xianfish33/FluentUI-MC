package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Colors;
import xianfish.fluentui.client.ui.element.Element;

public class Button extends Element<Button> {
    public record ButtonColors(int bg, int border, int text,
                                int hoverBg, int hoverBorder,
                                int pressBg, int pressBorder, int disabledText) {
        public static ButtonColors blue() {
            return new ButtonColors(BLACK_30, BLUE, WHITE, BLACK_40, BLUE_LIGHT, WHITE_20, BLUE_SOFT, WHITE_50);
        }
        private static final int BLUE = 0xFF3B82F6, BLUE_LIGHT = 0xFF60A5FA, BLUE_SOFT = 0xFF93C5FD;
        private static final int WHITE = 0xFFFFFFFF, WHITE_20 = 0x33FFFFFF, WHITE_50 = 0x80FFFFFF;
        private static final int BLACK_30 = 0x4D000000, BLACK_40 = 0x66000000;
    }

    protected ButtonColors colors;
    protected Component text;
    protected boolean hovered, pressed, enabled = true;
    protected Runnable action;
    protected final Animator borderAnim = new Animator();
    protected final Animator bgAnim = new Animator();
    private float borderAlpha, bgBrightness;

    public Button(Component text, int w, int h, ButtonColors c) { super(w, h); this.text = text; this.colors = c; borderAnim.set(0); bgAnim.set(0); }
    public Button(String text, int w, int h, ButtonColors c) { this(Component.literal(text), w, h, c); }
    public Button(Component text, int w, int h) { this(text, w, h, ButtonColors.blue()); }
    public Button(String text, int w, int h) { this(Component.literal(text), w, h, ButtonColors.blue()); }

    public Button action(Runnable r) { action = r; return this; }
    public Component text() { return text; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return;
        layout();
        borderAlpha = borderAnim.get();
        bgBrightness = bgAnim.get();

        boolean h = isHovered(mx, my);
        if (h != hovered) {
            hovered = h;
            if (hovered) { borderAlpha = 1; borderAnim.set(1); }
            else borderAnim.animate(1, 0, 120, Easing.EASE_OUT_CUBIC);
        }

        int cBg = colors.bg, cBorder = colors.border, cText = colors.text;
        if (!enabled) { cText = colors.disabledText; }
        else if (pressed) { cBg = Colors.lerp(colors.bg, colors.pressBg, bgBrightness); cBorder = Colors.lerp(colors.border, colors.pressBorder, bgBrightness); }
        else if (hovered) { cBg = Colors.lerp(colors.bg, colors.hoverBg, borderAlpha); cBorder = Colors.lerp(colors.border, colors.hoverBorder, borderAlpha); }

        int ax = getAbsoluteX(), ay = getAbsoluteY();
        e.fill(ax, ay, ax + width, ay + height, cBg);
        if (borderAlpha > 0.01f) e.outline(ax, ay, width, height, Colors.lerp(0x00000000, cBorder, borderAlpha));

        int tw = font.width(text), tx = ax + (width - tw) / 2, ty = ay + (height - font.lineHeight) / 2;
        e.text(font, text, tx, ty, cText);    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!enabled || !visible || btn != 0) return false;
        if (isHovered(mx, my)) { pressed = true; bgAnim.animate(0, 1, 60, Easing.EASE_IN_CUBIC); return true; }
        return false;
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (!enabled || btn != 0) return false;
        boolean wp = pressed; pressed = false; bgAnim.animate(1, 0, 100, Easing.EASE_OUT_CUBIC);
        if (wp && isHovered(mx, my) && action != null) action.run();
        return wp;
    }
    public void setEnabled(boolean v) { enabled = v; if (!v) { hovered = false; pressed = false; } }
}
