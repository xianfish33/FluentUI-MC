package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.TooltippingElement;

import java.util.function.Consumer;

public class Slider extends TooltippingElement<Slider> {
    private static final int TRACK_H = 4, THUMB_W = 8, THUMB_H = 16;

    public record SliderColors(int track, int trackFill, int thumb, int thumbHover) {
        public static SliderColors blue() {
            return new SliderColors(0x30FFFFFF, 0xFF3B82F6, 0xFFFFFFFF, 0xFF93C5FD);
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
        hovered = isHovered(mx, my);
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
        e.fill(thumbX, ay, thumbX + THUMB_W, ay + height, thumbCol);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) return false;
        if (isHovered(mx, my)) {
            dragging = true;
            value = toNorm((float) mx);
            thumbAnim.set(value);
            if (onChanged != null) onChanged.accept(value);
            return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!dragging || btn != 0) return false;
        value = toNorm((float) mx);
        thumbAnim.set(value);
        if (onChanged != null) onChanged.accept(value);
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (!dragging || btn != 0) return false;
        dragging = false;
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!visible || !isHovered(mx, my)) return false;
        float step = steps > 0 ? 1f / steps : 0.02f;
        value = clamp(value - (float) va * step);
        thumbAnim.set(value);
        if (onChanged != null) onChanged.accept(value);
        return true;
    }
}
