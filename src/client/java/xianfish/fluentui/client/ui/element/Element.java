package xianfish.fluentui.client.ui.element;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import xianfish.fluentui.client.ui.element.layout.OverflowMode;

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

    /** 多点定位的出入参类型：只读点。锚定定位用（见 {@code tl／tr／bl／br／center}）。 */
    public record UIPoint(int x, int y) {
        public static final UIPoint ZERO = new UIPoint(0, 0);
    }

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

    /**
     * 把本元素从布局父容器的子列表中摘除（脱离布局父容器 {@code removeFromParent()}，若有）。
     * 单独的 {@link #setParent} 只改回指；当元素要迁移到浮层树、不再由原布局宿主绘制时，用本方法让旧宿主彻底放手。
     */
    @SuppressWarnings("unchecked")
    public T removeFromParent() {
        if (parent instanceof LayoutElement<?> layoutElement) {
            layoutElement.children().remove(this);
        }
        parent = null;
        return (T) this;
    }

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
            if (p instanceof LayoutElement<?> l) ax -= (int) l.scrollX();
        }
        return ax;
    }

    public int getAbsoluteY() {
        int ay = y;
        for (Element<?> p = parent; p != null; p = p.getParent()) {
            ay += p.getY();
            if (p instanceof LayoutElement<?> l) ay -= (int) l.scrollY();
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

    /**
     * 当明确用户交互（点击 / 松开 / 拖拽 / 滚轮）落在本元素命中区域<em>之外</em>，且本元素位于浮层树中时，由 {@code InteractionManager} 调用
     * （外部交互钩子 {@code onOutsideInteraction()}，明确输入专属）。默认空实现。
     *
     * <p>覆写以实现“点击外部关闭”，无需每个组件重复推导同一逻辑。
     * 悬停（被动）不触发。
     */
    public void onOutsideInteraction() {}

    /**
     * H0 纯几何矩形命中：仅自身几何矩形，不看裁剪、不看遮挡、不看浮层树，不可覆写。
     * 想"只问矩形"一律调它；分发门控走 L1 {@link #isHit}（含裁剪）及以上。
     */
    public static boolean rectHit(Element<?> el, double mx, double my) {
        int ax = el.getAbsoluteX(), ay = el.getAbsoluteY();
        return mx >= ax && mx <= ax + el.width && my >= ay && my <= ay + el.height;
    }

    /**
     * L0 裸矩形命中：默认即 H0 {@link #rectHit}，渲染期宽松反馈（如悬停描边）用它。
     *
     * <p><b>不得</b>用于失焦判定、开关切换、分发门控——那些走 L1 {@link #isHit}（含裁剪）及以上。
     */
    public boolean isHovered(double mx, double my) {
        return rectHit(this, mx, my);
    }

    /**
     * H1 有效形状命中：唯一可覆写点。默认即 H0 {@link #rectHit}；
     * 覆写只准谈"形状"（开关门限、动画中间态几何），不准谈裁剪、不准谈树。
     *
     * <p>L1 {@link #isHit} 经由本方法取值——覆写者自动参与分发门控、外部关闭、
     * 阻断判定的统一谓词，无需在各分发点重复自查。
     */
    public boolean shapeHit(double mx, double my) {
        return rectHit(this, mx, my);
    }

    /**
     * L1 裁剪命中：L0 矩形 ∩ 祖先裁剪链。分发门控、外部关闭、阻断判定的统一谓词。
     * 跨树的全 UI 命中见 {@code InteractionManager#topmostAnywhere}（L3）。
     */
    public static boolean isHit(Element<?> el, double mx, double my) {
        return el.shapeHit(mx, my) && el.isMouseWithinClipBounds(mx, my);
    }

    public boolean isMouseWithinClipBounds(double mx, double my) {
        for (Element<?> p = parent; p != null; p = p.getParent()) {
            if (p instanceof LayoutElement<?> l) {
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
