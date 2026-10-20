package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public class Button extends TooltippingElement<Button> {
    public record ButtonColors(int bg, int border, int text,
                                int hoverBg, int hoverBorder,
                                int pressBg, int pressBorder, int disabledText) {
        public static ButtonColors blue() {
            return new ButtonColors(BG, REST_BORDER, WHITE, BG_HOVER, HOVER_BORDER, ACCENT, ACCENT, WHITE_50);
        }
        // 与输入框、下拉、菜单、列表共用一套：底色与边框分层，激活态才上强调色。
        private static final int BG = 0xFF222222, BG_HOVER = 0xFF2E2E2E;
        private static final int REST_BORDER = 0xFF555555, HOVER_BORDER = 0xFF777777;
        private static final int ACCENT = 0xFF3B82F6;
        private static final int WHITE = 0xFFFFFFFF, WHITE_50 = 0x80FFFFFF;
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

        boolean h = InteractionManager.hovering(this, mx, my);
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
        // 边框常态可见：rest 用共用边框色，悬停动画把边框推亮。
        e.outline(ax, ay, width, height, cBorder);

        int tw = font.width(text), tx = ax + (width - tw) / 2, ty = ay + (height - font.lineHeight) / 2;
        e.text(font, text, tx, ty, cText);    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!enabled || !visible || e.button() != 0) return false;
        if (InteractionManager.hovering(this, e.x(), e.y())) { pressed = true; bgAnim.animate(0, 1, 60, Easing.EASE_IN_CUBIC); return true; }
        return false;
    }
    @Override public boolean mouseReleased(MouseReleasedEvent e) { return pressReleased(e, false); }
    @Override public boolean pressReleased(MouseReleasedEvent e, boolean hit) {
        if (!enabled || e.button() != 0) return false;
        boolean wp = pressed; pressed = false; bgAnim.animate(1, 0, 100, Easing.EASE_OUT_CUBIC);
        if (wp && hit && action != null) action.run();
        return wp;
    }
    public void setEnabled(boolean v) { enabled = v; if (!v) { hovered = false; pressed = false; } }
}
