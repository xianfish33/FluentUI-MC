package xianfish.fluentui.client.ui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.Layout;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

import java.util.ArrayList;
import java.util.List;

public class Layer extends Layout<Layer> {
    private boolean blocksMouse = true;
    private Element<?> backdrop;

    public Layer(int x, int y, int w, int h) { super(x, y, w, h); }
    public Layer(int w, int h) { super(w, h); }

    public Layer blocksMouse(boolean v) { blocksMouse = v; return this; }
    public boolean isBlockingMouse() { return blocksMouse; }
    public Layer backdrop(Element<?> v) { backdrop = v; return this; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        e.nextStratum();
        if (backdrop != null) {
            backdrop.topLeft(ax, ay).size(width, height);
            backdrop.extractRenderState(e, mx, my, d);
        }
        for (Element<?> c : children) {
            if (!c.isVisible()) continue;
            c.extractRenderState(e, mx, my, d);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        List<Element<?>> sorted = new ArrayList<>(children);
        sorted.sort((a, b) -> Integer.compare(b.getZIndex(), a.getZIndex()));
        for (Element<?> c : sorted) {
            if (!c.isVisible() || !InteractionManager.isHit(c, mx, my)) continue;
            if (c.mouseClicked(mx, my, btn)) return true;
        }
        return blocksMouse;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            Element<?> c = children.get(i);
            if (!c.isVisible() || !InteractionManager.isHit(c, mx, my)) continue;
            if (c.mouseReleased(mx, my, btn)) return true;
        }
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!visible) return false;
        for (Element<?> c : children) {
            if (!c.isVisible() || !InteractionManager.isClipHit(c, mx, my)) continue;
            if (c.mouseDragged(mx, my, btn, dx, dy)) return true;
        }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!visible) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            Element<?> c = children.get(i);
            if (!c.isVisible() || !InteractionManager.isHit(c, mx, my)) continue;
            if (c.mouseScrolled(mx, my, ha, va)) return true;
        }
        return blocksMouse;
    }
}
