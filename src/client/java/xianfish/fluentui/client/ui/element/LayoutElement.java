package xianfish.fluentui.client.ui.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.layout.OverflowMode;
import java.util.ArrayList;
import java.util.List;

public abstract class LayoutElement<T extends LayoutElement<T>> extends Element<T> {
    protected final List<Element<?>> children = new ArrayList<>();
    protected float scrollX;
    protected float scrollY;
    protected float targetScrollX;
    protected float targetScrollY;
    protected OverflowMode overflowMode = OverflowMode.VISIBLE;

    public LayoutElement(int x, int y, int w, int h) { super(x, y, w, h); }
    public LayoutElement(int w, int h) { super(w, h); }

    @Override
    public List<Element<?>> children() { return children; }

    @SuppressWarnings("unchecked")
    public T addChild(Element<?> child) {
        children.add(child);
        child.setParent(this);
        markDirty();
        return (T) this;
    }
    public float scrollX() { return scrollX; }
    public float scrollY() { return scrollY; }
    public void scrollTo(float x, float y) { targetScrollX = x; targetScrollY = y; }
    public void scrollBy(float dx, float dy) { targetScrollX += dx; targetScrollY += dy; }
    public OverflowMode getOverflowMode() { return overflowMode; }
    @SuppressWarnings("unchecked")
    public T overflow(OverflowMode v) { overflowMode = v; markDirty(); return (T) this; }

    @Override
    public void tick() {
        float f = 0.3f;
        scrollX += (targetScrollX - scrollX) * f;
        scrollY += (targetScrollY - scrollY) * f;
        if (Math.abs(targetScrollX - scrollX) < 0.01f) scrollX = targetScrollX;
        if (Math.abs(targetScrollY - scrollY) < 0.01f) scrollY = targetScrollY;
    }

    @Override
    public void layout() {
        super.layout();
        for (Element<?> c : children) c.layout();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return;
        layout();
        for (Element<?> c : children) {
            if (!c.isVisible()) continue;
            c.extractRenderState(e, mx, my, d);
        }
    }

}
