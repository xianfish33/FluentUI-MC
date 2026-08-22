package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import java.util.ArrayList;
import java.util.List;

public class ContextMenu extends Element<ContextMenu> {
    private static final int ITEM_H = 18, PAD_X = 10;

    public record MenuItem(Component text, Runnable action, boolean enabled, boolean closeOnClick, int bgColor) {
        public MenuItem(String text, Runnable action, boolean enabled) { this(Component.literal(text), action, enabled, true, 0); }
        public MenuItem(Component text, Runnable action, boolean enabled) { this(text, action, enabled, true, 0); }
        public MenuItem(String text, Runnable action, boolean enabled, boolean closeOnClick) { this(Component.literal(text), action, enabled, closeOnClick, 0); }
        public MenuItem(Component text, Runnable action, boolean enabled, boolean closeOnClick) { this(text, action, enabled, closeOnClick, 0); }
        public MenuItem(String text, Runnable action, boolean enabled, boolean closeOnClick, int bgColor) { this(Component.literal(text), action, enabled, closeOnClick, bgColor); }
    }

    public record ContextMenuColors(int bg, int border, int text, int hoverBg, int disabledText) {
        public static ContextMenuColors blue() {
            return new ContextMenuColors(0xFF1E1E1E, 0xFF333333, 0xFFFFFFFF, 0x303B82F6, 0xFF666666);
        }
    }

    protected ContextMenuColors colors;
    protected List<MenuItem> items = new ArrayList<>();
    protected int hoveredIdx = -1, maxH = 260;
    protected boolean open;
    protected final Animator revealAnim = new Animator();
    protected final Animator scrollAnim = new Animator();

    public ContextMenu(ContextMenuColors c) {
        super(0, 0, 0, 0);
        this.colors = c;
        revealAnim.set(0); scrollAnim.set(0);
        zIndex(Integer.MAX_VALUE);
    }

    public ContextMenu() { this(ContextMenuColors.blue()); }

    public ContextMenu items(List<MenuItem> v) { items.clear(); items.addAll(v); return this; }
    public ContextMenu addItem(String text, Runnable action) { items.add(new MenuItem(text, action, true)); return this; }
    public ContextMenu addItem(Component text, Runnable action) { items.add(new MenuItem(text, action, true)); return this; }
    public ContextMenu addItem(String text, Runnable action, boolean enabled) { items.add(new MenuItem(text, action, enabled)); return this; }
    public ContextMenu addItem(Component text, Runnable action, boolean enabled) { items.add(new MenuItem(text, action, enabled)); return this; }
    public ContextMenu maxHeight(int v) { maxH = v; return this; }
    public ContextMenu font(net.minecraft.client.gui.Font f) { super.font(f); return this; }

    public static Builder builder(net.minecraft.client.gui.Font font) { return new Builder(font); }

    public static class Builder {
        private final net.minecraft.client.gui.Font font;
        private final List<MenuItem> items = new ArrayList<>();
        private int maxH = 260;
        private ContextMenuColors colors = ContextMenuColors.blue();

        Builder(net.minecraft.client.gui.Font font) { this.font = font; }

        public Builder colors(ContextMenuColors c) { colors = c; return this; }
        public Builder maxHeight(int v) { maxH = v; return this; }
        public Builder item(String text, Runnable action) { items.add(new MenuItem(text, action, true)); return this; }
        public Builder item(Component text, Runnable action) { items.add(new MenuItem(text, action, true)); return this; }
        public Builder item(String text, Runnable action, boolean enabled) { items.add(new MenuItem(text, action, enabled)); return this; }
        public Builder item(Component text, Runnable action, boolean enabled) { items.add(new MenuItem(text, action, enabled)); return this; }
        public Builder item(String text, Runnable action, boolean enabled, boolean closeOnClick) { items.add(new MenuItem(text, action, enabled, closeOnClick)); return this; }
        public Builder item(Component text, Runnable action, boolean enabled, boolean closeOnClick) { items.add(new MenuItem(text, action, enabled, closeOnClick)); return this; }
        public Builder item(String text, Runnable action, boolean enabled, boolean closeOnClick, int bgColor) { items.add(new MenuItem(text, action, enabled, closeOnClick, bgColor)); return this; }
        public Builder item(Component text, Runnable action, boolean enabled, boolean closeOnClick, int bgColor) { items.add(new MenuItem(text, action, enabled, closeOnClick, bgColor)); return this; }

        public ContextMenu build() {
            return new ContextMenu(colors).font(font).items(items).maxHeight(maxH);
        }
    }

    public void show(int x, int y) {
        setPos(x, y);
        computeSize();
        open = true;
        visible = true;
        scrollAnim.set(0);
        revealAnim.animate(0, 1, 150, Easing.EASE_OUT_QUINT);
        if (InteractionManager.active != null) InteractionManager.active.overlay(this);
    }

    public void hide() {
        open = false;
        visible = false;
        revealAnim.set(0);
        scrollAnim.set(0);
        hoveredIdx = -1;
        if (InteractionManager.active != null && InteractionManager.active.overlay() == this)
            InteractionManager.active.overlay(null);
    }

    public boolean isOpen() { return open; }

    private void computeSize() {
        int maxW = 40;
        for (MenuItem item : items) {
            if (font != null) maxW = Math.max(maxW, font.width(item.text) + PAD_X * 2);
        }
        width = maxW + 4;
        height = Math.min(items.size() * ITEM_H + 2, maxH);
    }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!open || !visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        float rt = revealAnim.get(), sc = scrollAnim.get();
        if (rt < 0.01f) return;

        e.nextStratum();
        e.enableScissor(ax, ay, ax + width, ay + (int)(height * rt));
        e.fill(ax, ay, ax + width, ay + (int)(Math.min(items.size() * ITEM_H + 2, maxH)), colors.bg);
        e.outline(ax, ay, width, (int)(Math.min(items.size() * ITEM_H + 2, maxH)), colors.border);

        hoveredIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            int iy = ay + 1 + i * ITEM_H - (int) sc, ib = iy + ITEM_H;
            if (ib < ay || iy > ay + height) continue;
            MenuItem item = items.get(i);
            boolean hov = mx >= ax && mx <= ax + width && my >= iy && my <= ib;
            if (item.bgColor != 0) e.fill(ax + 1, iy, ax + width - 1, ib, item.bgColor);
            if (hov && item.enabled) { hoveredIdx = i; e.fill(ax + 1, iy, ax + width - 1, ib, colors.hoverBg); }
            int tc = item.enabled ? colors.text : colors.disabledText;
            e.text(font, item.text, ax + PAD_X, iy + (ITEM_H - font.lineHeight) / 2, tc);
        }
        e.disableScissor();
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!open || !visible) return false;
        if (!isHovered(mx, my)) { hide(); return btn != 1; }
        if (btn == 0 && hoveredIdx >= 0) {
            MenuItem item = items.get(hoveredIdx);
            if (item.enabled && item.action != null) item.action.run();
            if (item.closeOnClick) hide();
            return true;
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!open || !visible || !isHovered(mx, my)) return false;
        float totalH = items.size() * ITEM_H + 2, ms = Math.max(0, totalH - height);
        float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) va * 20));
        scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
        return true;
    }

    @Override public boolean isHovered(double mx, double my) {
        if (!open || revealAnim.get() < 0.01f) return false;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + (int)(height * revealAnim.get());
    }
}
