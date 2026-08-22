package xianfish.fluentui.client.ui.element;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import xianfish.fluentui.client.ui.layout.OverflowMode;

public abstract class Element<T extends Element<T>> {
    protected int x;
    protected int y;
    protected int width;
    protected int height;
    protected Margin margin = Margin.NONE;
    protected boolean visible = true;
    protected Element<?> parent;
    protected boolean dirty = true;
    protected int zIndex = 0;
    protected Font font;
    protected boolean paused;
    protected Element<?> pressOwner;

    public Element(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    public Element(int width, int height) {
        this(0, 0, width, height);
    }

    public Font font() { return font; }
    @SuppressWarnings("unchecked") public T font(Font f) { font = f; return (T) this; }

    public int getZIndex() { return zIndex; }
    @SuppressWarnings("unchecked") public T zIndex(int z) { zIndex = z; return (T) this; }

    public int getX() { return x; }
    public int getY() { return y; }

    public UIPoint tl() { return new UIPoint(x, y); }
    public UIPoint tr() { return new UIPoint(x + width, y); }
    public UIPoint bl() { return new UIPoint(x, y + height); }
    public UIPoint br() { return new UIPoint(x + width, y + height); }
    public UIPoint center() { return new UIPoint(x + width / 2, y + height / 2); }

    @SuppressWarnings("unchecked") public T topLeft(int x, int y) { this.x = x; this.y = y; return (T) this; }
    public void setPos(int x, int y) { this.x = x; this.y = y; }
    public void setPos(UIPoint pt) { setPos(pt.x(), pt.y()); }

    public final int getWidth() { return width; }
    public final int getHeight() { return height; }

    @SuppressWarnings("unchecked") public T width(int w) { width = w; markDirty(); return (T) this; }
    @SuppressWarnings("unchecked") public T height(int h) { height = h; markDirty(); return (T) this; }
    @SuppressWarnings("unchecked") public T size(int w, int h) { width = w; height = h; markDirty(); return (T) this; }

    public ScreenRectangle getRect() { return new ScreenRectangle(x, y, width, height); }

    public Margin getMargin() { return margin; }
    @SuppressWarnings("unchecked") public T margin(Margin m) { margin = m; markDirty(); return (T) this; }

    public boolean isVisible() { return visible; }
    @SuppressWarnings("unchecked") public T visible(boolean v) { visible = v; markDirty(); return (T) this; }

    public Element<?> getParent() { return parent; }
    public void setParent(Element<?> parent) { this.parent = parent; }

    public Element<?> pressOwner() { return pressOwner; }

    public static final int CAPTURE_DRAG = 1 << 0;
    public static final int CAPTURE_RELEASE = 1 << 1;
    public static final int CAPTURE_SCROLL = 1 << 2;
    public static final int BLOCK_HOVER = 1 << 3;
    public static final int BLOCK_RIGHT = 1 << 4;

    public int pressFlags() { return CAPTURE_DRAG | CAPTURE_RELEASE; }

    public int getAbsoluteX() {
        int ax = x;
        for (Element<?> p = parent; p != null; p = p.getParent()) {
            ax += p.getX();
            if (p instanceof Layout<?> l) ax -= (int) l.scrollX();
        }
        return ax;
    }

    public int getAbsoluteY() {
        int ay = y;
        for (Element<?> p = parent; p != null; p = p.getParent()) {
            ay += p.getY();
            if (p instanceof Layout<?> l) ay -= (int) l.scrollY();
        }
        return ay;
    }

    public boolean isDirty() { return dirty; }
    public void markDirty() { dirty = true; if (parent != null) parent.markDirty(); }
    public void clearDirty() { dirty = false; }
    public void tick() {}
    protected abstract void measure();
    protected void arrange() {}
    public void layout() { if (dirty) { measure(); arrange(); clearDirty(); } }

    public abstract void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d);

    public boolean mouseClicked(double mx, double my, int btn) { return false; }
    public boolean mouseReleased(double mx, double my, int btn) { return false; }
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { return false; }
    public boolean mouseScrolled(double mx, double my, double ha, double va) { return false; }
    public void mouseEntered(double mx, double my) {}
    public void mouseMoved(double mx, double my) {}
    public void mouseExited() {}

    public boolean isPaused() { return paused; }
    @SuppressWarnings("unchecked")
    public T setPaused(boolean v) { paused = v; return (T) this; }
    public void refreshBounds() { layout(); }
    public void suspendInteractions() {
        setPaused(true);
        if (this instanceof Layout<?> layout) {
            for (Element<?> c : layout.children()) c.suspendInteractions();
        }
    }
    public void resumeInteractions() {
        setPaused(false);
        if (this instanceof Layout<?> layout) {
            for (Element<?> c : layout.children()) c.resumeInteractions();
        }
        refreshBounds();
    }

    public boolean isHovered(double mx, double my) {
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + height;
    }

    public boolean isMouseWithinClipBounds(double mx, double my) {
        for (Element<?> p = parent; p != null; p = p.getParent()) {
            if (p instanceof Layout<?> l) {
                OverflowMode o = l.getOverflowMode();
                if (o == OverflowMode.HIDDEN || o == OverflowMode.SCROLL) {
                    int px = l.getAbsoluteX(), py = l.getAbsoluteY();
                    if (mx < px || mx > px + l.getWidth() || my < py || my > py + l.getHeight()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
