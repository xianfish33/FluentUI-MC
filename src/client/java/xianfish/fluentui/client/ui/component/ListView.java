package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListView<T> extends TooltippingElement<ListView<T>> {
    protected static final int PAD_X = 8, ACCENT_W = 3;
    protected int itemHeight = 22;

    public record ListViewColors(int bg, int border, int itemText, int hoverBg, int selBg, int accent, int scrollbar) {
        public static ListViewColors blue() {
            return new ListViewColors(0xFF1A1A1A, 0xFF333333, 0xFFFFFFFF,
                0x303B82F6, 0x303B82F6, 0xFF3B82F6, 0x80FFFFFF);
        }
    }

    protected ListViewColors colors;
    protected List<T> data = new ArrayList<>();
    protected int selIdx = -1, hoveredIdx = -1;
    protected Consumer<Integer> onSelect;
    protected final Animator scrollAnim = new Animator();
    protected float scrollOff;
    protected boolean sbDragging, sbHovered;
    protected double sbDragStartMy;
    protected float sbDragStartScroll;

    public ListView(int w, int h, ListViewColors c) { super(w, h); this.colors = c; scrollAnim.set(0); }
    public ListView(int w, int h) { this(w, h, ListViewColors.blue()); }

    public ListView<T> items(List<T> v) { data.clear(); data.addAll(v); return this; }
    public List<T> items() { return data; }
    public ListView<T> itemHeight(int v) { itemHeight = v; return this; }
    public int itemHeight() { return itemHeight; }
    public ListView<T> onSelected(Consumer<Integer> c) { onSelect = c; return this; }
    public int selectedIndex() { return selIdx; }

    protected int contentHeight() { return height; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        int ch = contentHeight();
        e.fill(ax, ay, ax + width, ay + height, colors.bg);
        e.enableScissor(ax, ay, ax + width, ay + ch);

        for (int i = 0; i < data.size(); i++) {
            int iy = ay + i * itemHeight - (int) scrollAnim.get(), ib = iy + itemHeight;
            if (ib < ay || iy > ay + ch) continue;
            boolean sel = i == selIdx, hov = i == hoveredIdx;

            if (sel) {
                e.fill(ax + 1, iy, ax + width - 1, ib, colors.selBg);
                e.fill(ax + 1, iy, ax + 1 + ACCENT_W, ib, colors.accent);
            } else if (hov) {
                e.fill(ax + 1, iy, ax + width - 1, ib, colors.hoverBg);
            }

            drawRow(data.get(i), i, sel, hov, e, ax, iy, ib);
        }

        drawScrollbar(e, ax, ay);

        e.disableScissor();
        e.outline(ax, ay, width, height, colors.border);

        hoveredIdx = -1;
        if (mx >= ax && mx <= ax + width && my >= ay && my <= ay + ch) {
            int idx = (int) ((my - ay + scrollAnim.get()) / itemHeight);
            if (idx >= 0 && idx < data.size()) hoveredIdx = idx;
        }
        sbHovered = hitScrollbar(mx, my);
    }

    protected void drawRow(T item, int idx, boolean sel, boolean hov,
                           GuiGraphicsExtractor e, int ax, int iy, int ib) {
        int tx = ax + PAD_X + (sel ? ACCENT_W : 0);
        int ty = iy + (itemHeight - font.lineHeight) / 2;
        Component txt = item instanceof Component c ? c
                : item == null ? Component.literal("") : Component.literal(item.toString());
        if (font.width(txt) > width - PAD_X * 2 - 8) {
            e.enableScissor(ax + PAD_X, iy, ax + width - PAD_X, ib);
            e.text(font, txt, tx, ty, colors.itemText);
            e.disableScissor();
        } else e.text(font, txt, tx, ty, colors.itemText);
    }

    protected void drawScrollbar(GuiGraphicsExtractor e, int ax, int ay) {
        float totalH = data.size() * itemHeight, maxS = Math.max(0, totalH - contentHeight());
        if (maxS <= 0) return;
        scrollOff = scrollAnim.get();
        float bh = Math.max(20, contentHeight() * contentHeight() / totalH);
        float by = ay + (scrollOff / maxS) * (contentHeight() - bh);
        int col = sbHovered || sbDragging ? brighten(colors.scrollbar) : colors.scrollbar;
        e.fill(ax + width - 4, (int) by, ax + width - 2, (int) (by + bh), col);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0 || !isHovered(mx, my)) return false;
        if (hitScrollbar(mx, my)) {
            sbDragging = true; sbDragStartMy = my; sbDragStartScroll = scrollAnim.get(); return true;
        }
        if (clickRow(mx, my, btn)) return true;
        if (hoveredIdx >= 0) { selIdx = hoveredIdx; if (onSelect != null) onSelect.accept(selIdx); }
        return true;
    }

    protected boolean clickRow(double mx, double my, int btn) {
        return false;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!visible || btn != 0) return false;
        if (sbDragging) {
            float totalH = data.size() * itemHeight, maxS = Math.max(0, totalH - contentHeight());
            if (maxS <= 0) return true;
            float barH = Math.max(20, contentHeight() * contentHeight() / totalH);
            float ratio = maxS / (contentHeight() - barH);
            float target = Math.max(0, Math.min(maxS, sbDragStartScroll + (float)(my - sbDragStartMy) * ratio));
            scrollOff = target; scrollAnim.set(target);
            return true;
        }
        float maxS = Math.max(0, data.size() * itemHeight - contentHeight());
        scrollOff = Math.max(0, Math.min(maxS, scrollOff - (float) dy));
        scrollAnim.set(scrollOff);
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (sbDragging) { sbDragging = false; return true; }
        return false;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!visible || !isHovered(mx, my)) return false;
        float maxS = Math.max(0, data.size() * itemHeight - contentHeight());
        float target = Math.max(0, Math.min(maxS, scrollAnim.get() - (float) va * 30));
        scrollAnim.animate(scrollAnim.get(), target, 120, Easing.EASE_OUT_CUBIC);
        return true;
    }

    protected boolean hitScrollbar(double mx, double my) {
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        if (mx < ax + width - 4 || mx > ax + width) return false;
        float totalH = data.size() * itemHeight, maxS = Math.max(0, totalH - contentHeight());
        if (maxS <= 0) return false;
        float barH = Math.max(20, contentHeight() * contentHeight() / totalH);
        float barY = ay + (scrollAnim.get() / maxS) * (contentHeight() - barH);
        return my >= barY && my <= barY + barH;
    }

    protected static int brighten(int color) {
        int a = Math.min(255, (int)(((color >> 24) & 0xFF) * 1.6f));
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
