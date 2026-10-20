package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

import java.util.function.Consumer;

public class Slider extends TooltippingElement<Slider> {
    private static final int TRACK_H = 3, THUMB_W = 6, THUMB_H = 12, THUMB_S = 8;
    /** 白色填充相对拖块边长的缩放：常态铺满，按下拖动时缩到一半。 */
    private static final float FILL_REST = 1f, FILL_PRESSED = 0.5f;
    /** 每帧朝目标合拢的固定比例：显示数值以它逼近。 */
    private static final float APPROACH = 0.4f;

    public record SliderColors(int track, int trackFill, int thumb, int thumbHover, int thumbBorder) {
        public static SliderColors blue() {
            return new SliderColors(0x30FFFFFF, 0xFF3B82F6, 0xFFFFFFFF, 0xFF93C5FD, 0xFF555555);
        }
    }

    protected SliderColors colors;
    protected float value = 0.5f;
    protected int steps = 0;
    protected boolean dragging, hovered, nonlinear;
    protected Consumer<Float> onChanged;
    /** 滑块的显示值：每帧朝 {@code value} 逼近，逐帧读的只有它。 */
    private float animVal;
    /** 拖块内部白色填充的缩放：按非线性缓动在铺满与一半之间过渡。 */
    private final Animator fillAnim = new Animator();

    public Slider(int width, SliderColors c) {
        super(width, THUMB_H);
        this.colors = c;
        animVal = value;
        fillAnim.set(FILL_REST);
    }

    public Slider(int width) { this(width, SliderColors.blue()); }

    @Override public int pressFlags() {
        return CAPTURE_DRAG | CAPTURE_RELEASE | BLOCK_HOVER | BLOCK_RIGHT;
    }

    public Slider value(float v) { value = clamp(v); animVal = value; return this; }
    public float value() { return value; }

    public Slider steps(int n) { steps = n; return this; }
    public int steps() { return steps; }

    public Slider nonlinear(boolean v) { nonlinear = v; return this; }

    public Slider onChanged(Consumer<Float> c) { onChanged = c; return this; }

    private float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    private float toNorm(float screenX) {
        int ax = getAbsoluteX();
        float raw = clamp((screenX - ax - THUMB_W / 2f) / (width - THUMB_W));
        if (nonlinear) raw = raw * raw;
        if (steps > 0) raw = Math.round(raw * steps) / (float) steps;
        return raw;
    }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return; layout();
        hovered = InteractionManager.hovering(this, mx, my);
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        int trackY = ay + (height - TRACK_H) / 2;

        // 每帧把剩余差距按固定比例合拢：目标连续变时收敛路径唯一确定，没有会话可重启。
        animVal += (value - animVal) * APPROACH;
        if (Math.abs(value - animVal) < 0.001f) animVal = value;

        e.fill(ax, trackY, ax + width, trackY + TRACK_H, colors.track);

        float displayAnim = nonlinear ? (float) Math.sqrt(animVal) : animVal;
        // 底条的填充宽度与滑块位置读同一条逼近值，追上目标时两者一起落位。
        int fillW = (int) (displayAnim * width);
        if (fillW > 0) e.fill(ax, trackY, ax + fillW, trackY + TRACK_H, colors.trackFill);
        int thumbX = ax + (int) (displayAnim * (width - THUMB_W));
        int thumbCol = hovered || dragging ? colors.thumbHover : colors.thumb;

        // 拖块整体维持静态边长；内部白色填充在按下拖动时缩到一半，松开收回，缩放走非线性缓动。
        int side = Math.max(4, Math.min(THUMB_S, height - 4));
        int fill = Math.round(side * fillAnim.get());
        int ty = ay + (height - side) / 2;
        int fx = thumbX + (side - fill) / 2, fy = ty + (side - fill) / 2;
        // 拖块底铺边框色作框体，白色填充缩在框内；填充缩到一半时框体随之露出。
        e.fill(thumbX, ty, thumbX + side, ty + side, colors.thumbBorder);
        e.fill(fx, fy, fx + fill, fy + fill, thumbCol);
        e.outline(thumbX, ty, side, side, colors.thumbBorder);
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0) return false;
        if (InteractionManager.hovering(this, e.x(), e.y())) {
            dragging = true;
            fillAnim.to(FILL_PRESSED, 120, Easing.EASE_OUT_CUBIC);
            value = toNorm((float) e.x());
            if (onChanged != null) onChanged.accept(value);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseDraggedEvent e) {
        if (!dragging || e.button() != 0) return false;
        value = toNorm((float) e.x());
        if (onChanged != null) onChanged.accept(value);
        return true;
    }

    @Override public boolean mouseReleased(MouseReleasedEvent e) {
        if (!dragging || e.button() != 0) return false;
        dragging = false;
        fillAnim.to(FILL_REST, 120, Easing.EASE_OUT_CUBIC);
        return true;
    }

    @Override public boolean mouseScrolled(MouseScrolledEvent e) {
        if (!visible || !InteractionManager.hovering(this, e.x(), e.y())) return false;
        float step = steps > 0 ? 1f / steps : 0.02f;
        value = clamp(value - (float) e.vertical() * step);
        if (onChanged != null) onChanged.accept(value);
        return true;
    }
}
