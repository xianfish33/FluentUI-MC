package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;

import java.util.function.Consumer;

public class Slider extends TooltippingElement<Slider> {
    private static final int TRACK_H = 3, THUMB_W = 6, THUMB_H = 12, THUMB_S = 8;

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
    protected final Animator thumbAnim = new Animator();

    public Slider(int width, SliderColors c) {
        super(width, THUMB_H);
        this.colors = c;
        thumbAnim.set(value);
    }

    public Slider(int width) { this(width, SliderColors.blue()); }

    @Override public int pressFlags() {
        return CAPTURE_DRAG | CAPTURE_RELEASE | BLOCK_HOVER | BLOCK_RIGHT;
    }

    public Slider value(float v) { value = clamp(v); thumbAnim.set(value); return this; }
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

        e.fill(ax, trackY, ax + width, trackY + TRACK_H, colors.track);

        float displayVal = nonlinear ? (float) Math.sqrt(value) : value;
        int fillW = (int) (displayVal * width);
        if (fillW > 0) e.fill(ax, trackY, ax + fillW, trackY + TRACK_H, colors.trackFill);

        float animVal = thumbAnim.get();
        float displayAnim = nonlinear ? (float) Math.sqrt(animVal) : animVal;
        int thumbX = ax + (int) (displayAnim * (width - THUMB_W));
        int thumbCol = hovered || dragging ? colors.thumbHover : colors.thumb;
        // 滑块是竖向居中的小方块，不是撑满全高的竖条；外面再描一圈边框。
        int side = Math.max(4, Math.min(THUMB_S, height - 4));
        int ty = ay + (height - side) / 2;
        e.fill(thumbX, ty, thumbX + side, ty + side, thumbCol);
        e.outline(thumbX, ty, side, side, colors.thumbBorder);
    }

    /**
     * 滑块位置跟数值对齐。
     *
     * <p>刻度型（{@code steps > 0}）的数值是按档跳的，直接赋值会让滑块瞬移一整格，
     * 所以走一段过渡；连续型要死死贴住光标，保持直接赋值。</p>
     */
    private void syncThumb() {
        if (steps > 0) thumbAnim.to(value, 120, Easing.EASE_OUT_CUBIC);
        else thumbAnim.set(value);
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0) return false;
        if (InteractionManager.hovering(this, e.x(), e.y())) {
            dragging = true;
            value = toNorm((float) e.x());
            syncThumb();
            if (onChanged != null) onChanged.accept(value);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(MouseDraggedEvent e) {
        if (!dragging || e.button() != 0) return false;
        value = toNorm((float) e.x());
        syncThumb();
        if (onChanged != null) onChanged.accept(value);
        return true;
    }

    @Override public boolean mouseReleased(MouseReleasedEvent e) {
        if (!dragging || e.button() != 0) return false;
        dragging = false;
        return true;
    }

    @Override public boolean mouseScrolled(MouseScrolledEvent e) {
        if (!visible || !InteractionManager.hovering(this, e.x(), e.y())) return false;
        float step = steps > 0 ? 1f / steps : 0.02f;
        value = clamp(value - (float) e.vertical() * step);
        syncThumb();
        if (onChanged != null) onChanged.accept(value);
        return true;
    }
}
