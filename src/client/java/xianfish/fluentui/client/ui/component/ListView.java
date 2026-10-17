package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ListView<T> extends TooltippingElement<ListView<T>> {
    protected static final int PAD_X = 8, ACCENT_W = 3;
    protected int itemHeight = 22;

    public record ListViewColors(int bg, int border, int itemText, int hoverBg, int selBg, int accent, int scrollbar) {
        public static ListViewColors blue() {
            return new ListViewColors(0xFF222222, 0xFF555555, 0xFFFFFFFF,
                0x403B82F6, 0x403B82F6, 0xFF3B82F6, 0x80FFFFFF);
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
        // 悬停状态不在渲染期写：hoveredIdx / sbHovered 由 refreshHover（输入路径）独占写入。
    }

    /**
     * 悬停状态刷新：{@link #hoveredIdx} 与 {@link #sbHovered} 的唯一写入点，由带坐标的
     * 输入方法（enter／move／exit／press／scroll／drag）用事件坐标当场调用，渲染路径只读。
     *
     * <p>算法与原 {@code extractRenderState} 末尾逐字同源：先复位为 -1，再问
     * {@link InteractionManager#hovering} 拿归属，归属成立且落在可视内容区内时按
     * {@code (my - ay + scrollAnim.get()) / itemHeight} 就地求行号；滚动条悬停仍由
     * {@link #hitScrollbar} 判。行高／滚动偏移／视口裁剪全部读本组件现有字段。</p>
     */
    private void refreshHover(double mx, double my) {
        if (!visible) return;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        int ch = contentHeight();
        // 事件坐标是 double、渲染入参是 int：入口处收窄一次，后续算式与渲染路径逐字相同。
        int cx = (int) mx, cy = (int) my;
        hoveredIdx = -1;
        boolean owned = InteractionManager.hovering(this, cx, cy);
        if (owned && cx >= ax && cx <= ax + width && cy >= ay && cy <= ay + ch) {
            int idx = (int) ((cy - ay + scrollAnim.get()) / itemHeight);
            if (idx >= 0 && idx < data.size()) hoveredIdx = idx;
        }
        sbHovered = owned && hitScrollbar(cx, cy);
    }

    @Override public void mouseEntered(HoverEvent e) { super.mouseEntered(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseMoved(HoverEvent e) { super.mouseMoved(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseExited(HoverEvent e) { super.mouseExited(e); refreshHover(e.x(), e.y()); }

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

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0 || !InteractionManager.hovering(this, e.x(), e.y())) return false;
        // press 前按事件坐标当场重算：不依赖上一帧渲染留下的悬停值。
        refreshHover(e.x(), e.y());
        if (hitScrollbar(e.x(), e.y())) {
            sbDragging = true; sbDragStartMy = e.y(); sbDragStartScroll = scrollAnim.get(); return true;
        }
        if (clickRow(e.x(), e.y(), e.button())) return true;
        if (hoveredIdx >= 0) { selIdx = hoveredIdx; if (onSelect != null) onSelect.accept(selIdx); }
        return true;
    }

    protected boolean clickRow(double mx, double my, int btn) {
        return false;
    }

    @Override public boolean mouseDragged(MouseDraggedEvent e) {
        if (!visible || e.button() != 0) return false;
        if (sbDragging) {
            float totalH = data.size() * itemHeight, maxS = Math.max(0, totalH - contentHeight());
            if (maxS <= 0) return true;
            float barH = Math.max(20, contentHeight() * contentHeight() / totalH);
            float ratio = maxS / (contentHeight() - barH);
            float target = Math.max(0, Math.min(maxS, sbDragStartScroll + (float)(e.y() - sbDragStartMy) * ratio));
            scrollOff = target; scrollAnim.set(target);
            refreshHover(e.x(), e.y());   // 滚动偏移变了，行映射跟着重算
            return true;
        }
        float maxS = Math.max(0, data.size() * itemHeight - contentHeight());
        scrollOff = Math.max(0, Math.min(maxS, scrollOff - (float) e.dy()));
        scrollAnim.set(scrollOff);
        refreshHover(e.x(), e.y());
        return true;
    }

    @Override public boolean mouseReleased(MouseReleasedEvent e) {
        if (sbDragging) { sbDragging = false; return true; }
        return false;
    }

    @Override public boolean mouseScrolled(MouseScrolledEvent e) {
        if (!visible || !InteractionManager.hovering(this, e.x(), e.y())) return false;
        refreshHover(e.x(), e.y());
        float maxS = Math.max(0, data.size() * itemHeight - contentHeight());
        float target = Math.max(0, Math.min(maxS, scrollAnim.get() - (float) e.vertical() * 30));
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
