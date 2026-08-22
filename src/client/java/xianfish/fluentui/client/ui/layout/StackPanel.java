package xianfish.fluentui.client.ui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.Layout;
import xianfish.fluentui.client.ui.element.Margin;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public class StackPanel extends Layout<StackPanel> {
    private Orientation orientation;
    private StackDirection direction;
    private boolean autoWidth, autoHeight, centered;
    private int spacing;
    private int bgColor = 0x80000000;
    private boolean scrollbar = false;
    private int scrollbarColor = 0x40FFFFFF;
    private static final int SB_W = 4;
    private boolean sbDragging;
    private double sbDragStartMouse;
    private float sbDragStartScroll;
    private boolean sbHovered;

    public StackPanel(int x, int y, int w, int h, Orientation o) {
        super(x, y, w, h);
        this.orientation = o;
        this.direction = o == Orientation.HORIZONTAL ? StackDirection.LEFT_TO_RIGHT : StackDirection.TOP_TO_BOTTOM;
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
            if (!c.isVisible()) continue; c.layout();
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
            if (!c.isVisible()) continue; c.layout();
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
            if (!c.isVisible()) continue;
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
            if (!c.isVisible()) continue;
            Margin m = c.getMargin();
            int cx = centered ? (width - c.getWidth()) / 2 : m.left();
            if (fwd) { cursor += m.top(); c.setPos(cx, (int) cursor); cursor += c.getHeight() + m.bottom() + spacing; }
            else { cursor -= m.bottom() + c.getHeight(); c.setPos(cx, (int) cursor); cursor -= m.top() + spacing; }
        }
    }
    public float cw() { float t = 0; boolean f = true; for (Element<?> c : children) { if (!c.isVisible()) continue; c.layout(); if (!f) t += spacing; t += c.getMargin().left() + c.getWidth() + c.getMargin().right(); f = false; } return t; }
    public float ch() { float t = 0; boolean f = true; for (Element<?> c : children) { if (!c.isVisible()) continue; c.layout(); if (!f) t += spacing; t += c.getMargin().top() + c.getHeight() + c.getMargin().bottom(); f = false; } return t; }

    @Override public void layout() {
        if (dirty) { measure(); arrange(); clearDirty(); }
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
        if (scrollbar && overflowMode == OverflowMode.SCROLL) {
            sbHovered = hitScrollbar(mx, my);
            renderScrollbar(ex, ax, ay);
        }
    }

    private void renderScrollbar(GuiGraphicsExtractor ex, int ax, int ay) {
        if (orientation == Orientation.VERTICAL) {
            float ch = ch(), maxS = Math.max(0, ch - height);
            if (maxS <= 0) return;
            float bh = Math.max(16, height * height / ch);
            float by = ay + (scrollY / maxS) * (height - bh);
            int color = sbHovered || sbDragging ? brighten(scrollbarColor) : scrollbarColor;
            ex.fill(ax + width - SB_W, (int) by, ax + width, (int) (by + bh), color);
        } else {
            float cw = cw(), maxS = Math.max(0, cw - width);
            if (maxS <= 0) return;
            float bw = Math.max(16, width * width / cw);
            float bx = ax + (scrollX / maxS) * (width - bw);
            int color = sbHovered || sbDragging ? brighten(scrollbarColor) : scrollbarColor;
            ex.fill((int) bx, ay + height - SB_W, (int) (bx + bw), ay + height, color);
        }
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!visible) return false;
        if (super.mouseScrolled(mx, my, ha, va)) return true;
        if (overflowMode == OverflowMode.SCROLL && InteractionManager.isHit(this, mx, my)) {
            float max = orientation == Orientation.VERTICAL ? Math.max(0, ch() - height) : Math.max(0, cw() - width);
            float v = (float) (orientation == Orientation.VERTICAL ? va : va);
            if (orientation == Orientation.VERTICAL) { targetScrollY = Math.max(0, Math.min(max, targetScrollY - v * 20)); }
            else { targetScrollX = Math.max(0, Math.min(max, targetScrollX - v * 20)); }
            return true;
        }
        return false;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        if (btn == 0 && scrollbar && overflowMode == OverflowMode.SCROLL && hitScrollbar(mx, my)) {
            sbDragging = true;
            sbDragStartMouse = orientation == Orientation.VERTICAL ? my : mx;
            sbDragStartScroll = orientation == Orientation.VERTICAL ? targetScrollY : targetScrollX;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (sbDragging) {
            double delta = (orientation == Orientation.VERTICAL ? my : mx) - sbDragStartMouse;
            float max = orientation == Orientation.VERTICAL
                ? Math.max(0, ch() - height) : Math.max(0, cw() - width);
            float content = orientation == Orientation.VERTICAL ? ch() : cw();
            float barSize = orientation == Orientation.VERTICAL ? height : width;
            float barH = Math.max(16, barSize * barSize / content);
            float ratio = max / (barSize - barH);
            float target = Math.max(0, Math.min(max, sbDragStartScroll + (float) delta * ratio));
            if (orientation == Orientation.VERTICAL) targetScrollY = target;
            else targetScrollX = target;
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (sbDragging) { sbDragging = false; return true; }
        return super.mouseReleased(mx, my, btn);
    }

    private boolean hitScrollbar(double mx, double my) {
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        sbHovered = false;
        if (orientation == Orientation.VERTICAL) {
            if (mx < ax + width - SB_W || mx > ax + width) return false;
            float ch = ch(), maxS = Math.max(0, ch - height);
            if (maxS <= 0) return false;
            float barH = Math.max(16, height * height / ch);
            float barY = ay + (scrollY / maxS) * (height - barH);
            sbHovered = my >= barY && my <= barY + barH;
            return sbHovered;
        } else {
            if (my < ay + height - SB_W || my > ay + height) return false;
            float cw = cw(), maxS = Math.max(0, cw - width);
            if (maxS <= 0) return false;
            float barW = Math.max(16, width * width / cw);
            float barX = ax + (scrollX / maxS) * (width - barW);
            sbHovered = mx >= barX && mx <= barX + barW;
            return sbHovered;
        }
    }

    private static int brighten(int color) {
        int a = Math.min(255, (int)(((color >> 24) & 0xFF) * 1.6f));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
