package xianfish.fluentui.client.ui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.Layout;

public class Canvas extends Layout<Canvas> {

    public Canvas(int x, int y, int w, int h) { super(x, y, w, h); }
    public Canvas(int w, int h) { super(w, h); }

    public Canvas add(Element<?> child) { return add(child, 0, 0); }

    public Canvas add(Element<?> child, int x, int y) {
        child.setPos(x, y);
        children.add(child);
        child.setParent(this);
        markDirty();
        return this;
    }

    public Canvas add(Element<?> child, Anchor anchor) { return add(child, anchor, 0, 0); }

    public Canvas add(Element<?> child, Anchor anchor, int ox, int oy) {
        child.layout();
        int ax = switch (anchor) {
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> 0;
            case TOP_CENTER, CENTER, BOTTOM_CENTER -> (width - child.getWidth()) / 2;
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> width - child.getWidth();
        };
        int ay = switch (anchor) {
            case TOP_LEFT, TOP_CENTER, TOP_RIGHT -> 0;
            case CENTER_LEFT, CENTER, CENTER_RIGHT -> (height - child.getHeight()) / 2;
            case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> height - child.getHeight();
        };
        child.setPos(ax + ox, ay + oy);
        children.add(child);
        child.setParent(this);
        markDirty();
        return this;
    }

    @Override public Canvas visible(boolean v) {
        super.visible(v);
        for (Element<?> child : children) child.visible(v);
        return this;
    }

    public Canvas show() { return visible(true); }
    public Canvas hide() { return visible(false); }

    @Override public Canvas size(int w, int h) { super.size(w, h); return this; }
    @Override public Canvas width(int w) { super.width(w); return this; }
    @Override public Canvas height(int h) { super.height(h); return this; }
    @Override public Canvas margin(xianfish.fluentui.client.ui.element.Margin m) { super.margin(m); return this; }
    @Override public Canvas topLeft(int x, int y) { super.topLeft(x, y); return this; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return;
        layout();
        for (Element<?> c : children) {
            if (!c.isVisible()) continue;
            c.extractRenderState(e, mx, my, d);
        }
    }
}
