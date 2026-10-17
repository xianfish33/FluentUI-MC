package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public class Toggle extends TooltippingElement<Toggle> {
    private static final int W = 30, H = 14, SL = 10, SO = 2;

    public record ToggleColors(int trackOn, int trackOff, int slider, int sliderShadow) {
        public static ToggleColors blue() {
            // 关态用共用底色，不在半透明黑上叠半透明黑。
            return new ToggleColors(0xFF3B82F6, 0xFF222222, 0xFFFFFFFF, 0x40000000);
        }
    }

    protected ToggleColors colors;
    protected boolean on;
    protected boolean enabled = true;
    protected Runnable onChange;
    protected final Animator sliderAnim = new Animator();
    protected final Animator trackAnim = new Animator();

    public Toggle(int x, int y, ToggleColors c) { super(x, y, W, H); this.colors = c; sliderAnim.set(SO); trackAnim.set(0); }
    public Toggle(int x, int y) { this(x, y, ToggleColors.blue()); }

    public Toggle on(boolean v) { on = v; sliderAnim.set(v ? sE() : SO); trackAnim.set(v ? 1 : 0); return this; }
    public boolean isOn() { return on; }
    public Toggle onChange(Runnable r) { onChange = r; return this; }
    private int sE() { return W - SL - SO; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        float tT = trackAnim.get();
        e.fill(ax, ay, ax + width, ay + height, Colors.lerp(colors.trackOff, colors.trackOn, tT));
        int sx = ax + (int) sliderAnim.get(), sy = ay + SO;
        e.fill(sx, sy, sx + SL, sy + SL, colors.slider);
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!enabled || !visible || e.button() != 0) return false;
        if (InteractionManager.hovering(this, e.x(), e.y())) {
            on = !on;
            sliderAnim.animate(sliderAnim.get(), on ? sE() : SO, 150, Easing.EASE_OUT_CUBIC);
            trackAnim.animate(trackAnim.get(), on ? 1 : 0, 150, Easing.EASE_OUT_CUBIC);
            if (onChange != null) onChange.run();
            return true;
        }
        return false;
    }
}
