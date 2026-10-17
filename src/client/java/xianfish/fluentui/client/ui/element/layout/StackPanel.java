package xianfish.fluentui.client.ui.element.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.LayoutElement;
import xianfish.fluentui.client.ui.element.Margin;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;

public class StackPanel extends LayoutElement<StackPanel> {
    private Orientation orientation;
    private StackDirection direction;
    private boolean autoWidth, autoHeight, centered;
    private int spacing;
    private int bgColor = 0x80000000;
    private boolean scrollbar = false;
    private int scrollbarColor = 0x40FFFFFF;
    // 滚动条本体是一个真正的元素对象，挂在 children 末位即本容器最高层。
    // 它是容器的外壳，不是被滚的内容：内容尺寸与排布都跳过它，绘制由容器关掉裁剪后自己叫。
    private final ScrollBar scrollBar = new ScrollBar(this);
    static final int SB_W = 4;

    public StackPanel(int x, int y, int w, int h, Orientation o) {
        super(x, y, w, h);
        this.orientation = o;
        this.direction = o == Orientation.HORIZONTAL ? StackDirection.LEFT_TO_RIGHT : StackDirection.TOP_TO_BOTTOM;
        children.add(scrollBar);
        scrollBar.setParent(this);
    }

    public StackPanel(Orientation o) { this(0, 0, 0, 0, o); }

    public StackPanel add(Element<?> child) {
        children.add(child);
        child.setParent(this);
        markDirty();
        return this;
    }

    public StackPanel orientation(Orientation v) { orientation = v; markDirty(); return this; }
    public StackPanel direction(StackDirection v) { direction = v; markDirty(); return this; }
    public StackPanel autoWidth(boolean v) { autoWidth = v; markDirty(); return this; }
    public StackPanel autoHeight(boolean v) { autoHeight = v; markDirty(); return this; }
    public StackPanel spacing(int v) { spacing = v; markDirty(); return this; }
    public StackPanel centered(boolean v) { centered = v; markDirty(); return this; }
    public StackPanel bgColor(int v) { bgColor = v; return this; }
    public StackPanel scrollbar(boolean v) { scrollbar = v; return this; }
    public StackPanel scrollbar(boolean v, int color) { scrollbar = v; scrollbarColor = color; return this; }
    @Override public StackPanel visible(boolean v) { super.visible(v); return this; }
    @Override public StackPanel size(int w, int h) { super.size(w, h); return this; }
    @Override public StackPanel width(int w) { super.width(w); return this; }
    @Override public StackPanel height(int h) { super.height(h); return this; }
    @Override public StackPanel margin(Margin m) { super.margin(m); return this; }
    @Override public StackPanel topLeft(int x, int y) { super.topLeft(x, y); return this; }

    @Override protected void measure() {
        if (orientation == Orientation.HORIZONTAL) mH(); else mV();
    }
    private void mH() {
        int tw = 0, mh = 0; boolean f = true;
        for (Element<?> c : children) {
            // 容器外壳的装饰不进内容尺寸：它是视口的一部分，不是被滚的内容。
            if (!c.isVisible() || c == scrollBar) continue; c.layout();
            if (!f) tw += spacing;
            tw += c.getMargin().left() + c.getWidth() + c.getMargin().right();
            mh = Math.max(mh, c.getMargin().top() + c.getHeight() + c.getMargin().bottom());
            f = false;
        }
        if (autoWidth) width = tw;
        else if (width <= 0) width = tw;
        if (autoHeight) height = mh;
        else if (height <= 0) height = mh;
    }
    private void mV() {
        int mw = 0, th = 0; boolean f = true;
        for (Element<?> c : children) {
            // 同上：装饰不进内容尺寸。
            if (!c.isVisible() || c == scrollBar) continue; c.layout();
            if (!f) th += spacing;
            mw = Math.max(mw, c.getMargin().left() + c.getWidth() + c.getMargin().right());
            th += c.getMargin().top() + c.getHeight() + c.getMargin().bottom();
            f = false;
        }
        if (autoWidth) width = mw;
        else if (width <= 0) width = mw;
        if (autoHeight) height = th;
        else if (height <= 0) height = th;
    }

    @Override protected void arrange() {
        if (orientation == Orientation.HORIZONTAL) aH(); else aV();
    }
    private void aH() {
        boolean fwd = direction != StackDirection.RIGHT_TO_LEFT;
        float cursor = fwd ? 0 : cw();
        for (Element<?> c : children) {
            if (!c.isVisible() || c == scrollBar) continue;
            Margin m = c.getMargin();
            int cy = centered ? (height - c.getHeight()) / 2 : m.top();
            if (fwd) { cursor += m.left(); c.setPos((int) cursor, cy); cursor += c.getWidth() + m.right() + spacing; }
            else { cursor -= m.right() + c.getWidth(); c.setPos((int) cursor, cy); cursor -= m.left() + spacing; }
        }
    }
    private void aV() {
        boolean fwd = direction != StackDirection.BOTTOM_TO_TOP;
        float cursor = fwd ? 0 : ch();
        for (Element<?> c : children) {
            if (!c.isVisible() || c == scrollBar) continue;
            Margin m = c.getMargin();
            int cx = centered ? (width - c.getWidth()) / 2 : m.left();
            if (fwd) { cursor += m.top(); c.setPos(cx, (int) cursor); cursor += c.getHeight() + m.bottom() + spacing; }
            else { cursor -= m.bottom() + c.getHeight(); c.setPos(cx, (int) cursor); cursor -= m.top() + spacing; }
        }
    }
    public float cw() { float t = 0; boolean f = true; for (Element<?> c : children) { if (!c.isVisible() || c == scrollBar) continue; c.layout(); if (!f) t += spacing; t += c.getMargin().left() + c.getWidth() + c.getMargin().right(); f = false; } return t; }
    public float ch() { float t = 0; boolean f = true; for (Element<?> c : children) { if (!c.isVisible() || c == scrollBar) continue; c.layout(); if (!f) t += spacing; t += c.getMargin().top() + c.getHeight() + c.getMargin().bottom(); f = false; } return t; }

    @Override public void layout() {
        if (dirty) { measure(); arrange(); clearDirty(); }
        // 滚动条必须待在 children 末位：框架的口径是「列表位置即层级」，末位即本容器最高层，
        // 命中查找逆序扫子元素时才先扫到它，压在下面的内容元素抢不走落在条带上的按下。
        // 用户后续 add() 会把它挤到中间，这里每帧自愈回末位。
        if (!children.isEmpty() && children.get(children.size() - 1) != scrollBar) {
            children.remove(scrollBar);
            children.add(scrollBar);
        }
        boolean wantBar = scrollbar && overflowMode == OverflowMode.SCROLL;
        if (scrollBar.isVisible() != wantBar) scrollBar.visible(wantBar);
        for (Element<?> c : children) c.layout();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor ex, int mx, int my, float d) {
        if (!visible) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        if (bgColor != 0) ex.fill(ax, ay, ax + width, ay + height, bgColor);
        boolean clip = overflowMode == OverflowMode.HIDDEN || overflowMode == OverflowMode.SCROLL;
        if (clip) ex.enableScissor(ax, ay, ax + width, ay + height);
        super.extractRenderState(ex, mx, my, d);
        if (clip) ex.disableScissor();
    }

    @Override public boolean mouseScrolled(MouseScrolledEvent e) {
        if (!visible) return false;
        if (super.mouseScrolled(e)) return true;
        if (overflowMode == OverflowMode.SCROLL && Element.isHit(this, e.x(), e.y())) {
            float max = orientation == Orientation.VERTICAL ? Math.max(0, ch() - height) : Math.max(0, cw() - width);
            float v = (float) e.vertical();
            if (orientation == Orientation.VERTICAL) { targetScrollY = Math.max(0, Math.min(max, targetScrollY - v * 20)); }
            else { targetScrollX = Math.max(0, Math.min(max, targetScrollX - v * 20)); }
            return true;
        }
        return false;
    }

    // 滚动条的按下、拖动、松手全在 ScrollBar 自己身上：它是 children 末位的元素，
    // 命中查找逆序先扫到它，这一层就已经把按下吃掉了，容器不必再参与，也就不需要鼠标捕获。

    // ---- 供 ScrollBar 使用的容器侧读数 ----
    boolean vertical() { return orientation == Orientation.VERTICAL; }
    float contentExtent() { return vertical() ? ch() : cw(); }
    float viewportExtent() { return vertical() ? getHeight() : getWidth(); }
    float scroll() { return vertical() ? scrollY : scrollX; }
    int scrollbarColor() { return scrollbarColor; }

    /**
     * 写滚动位置：当前值与目标值一起写。
     *
     * <p>滑块的位置是拿当前滚动值算的，只改目标值的话画面要等缓动追上，拖起来会觉得「拽不住」。</p>
     */
    void applyScroll(float v) {
        float max = Math.max(0, contentExtent() - viewportExtent());
        float t = Math.max(0, Math.min(max, v));
        if (vertical()) { scrollY = t; targetScrollY = t; }
        else { scrollX = t; targetScrollX = t; }
    }
}
