package xianfish.fluentui.client.ui.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.layout.OverflowMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public abstract class Layout<T extends Layout<T>> extends Element<T> {
    protected final List<Element<?>> children = new ArrayList<>();
    protected float scrollX;
    protected float scrollY;
    protected float targetScrollX;
    protected float targetScrollY;
    protected OverflowMode overflowMode = OverflowMode.VISIBLE;

    public Layout(int x, int y, int w, int h) { super(x, y, w, h); }
    public Layout(int w, int h) { super(w, h); }

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
        for (Element<?> c : children) c.tick();
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
        List<Element<?>> sorted = sortedChildren();
        for (Element<?> c : sorted) {
            if (!c.isVisible()) continue;
            c.extractRenderState(e, mx, my, d);
        }
    }

    private List<Element<?>> sortedChildren() {
        List<Element<?>> s = new ArrayList<>(children);
        s.sort(Comparator.comparingInt(a -> ((Element<?>) a).getZIndex()));
        return s;
    }

    private boolean dispatchToTopmost(double mx, double my, java.util.function.BiFunction<Element<?>, Boolean, Boolean> fn, boolean release) {
        if (!visible) return false;
        for (Element<?> c : InteractionManager.hitOrder(this, mx, my)) {
            if (fn.apply(c, release)) return true;
        }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        pressOwner = null;
        return dispatchToTopmost(mx, my, (c, r) -> {
            if (c.mouseClicked(mx, my, btn)) {
                pressOwner = c instanceof Layout<?> l && l.pressOwner() != null ? l.pressOwner() : c;
                return true;
            }
            return false;
        }, false);
    }
    @Override public boolean mouseReleased(double mx, double my, int btn) {
        return dispatchToTopmost(mx, my, (c, r) -> c.mouseReleased(mx, my, btn), false);
    }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return dispatchToTopmost(mx, my, (c, r) -> c.mouseDragged(mx, my, btn, dx, dy), false);
    }
    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        return dispatchToTopmost(mx, my, (c, r) -> c.mouseScrolled(mx, my, ha, va), false);
    }
}
