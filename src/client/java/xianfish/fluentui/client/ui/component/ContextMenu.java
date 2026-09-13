package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.OverlayElement;
import java.util.ArrayList;
import java.util.List;

/**
 * 右键上下文菜单：挂载于浮层树 OverlayTree 顶层的模态浮层。
 *
 * <p>继承浮层基类 OverlayElement，平时不参与布局树的命中分发；
 * {@code show()} 先经 {@code removeFromParent()} 脱离布局父容器，再经
 * {@code pushToActiveManager} 自挂载完成根级推入，直接进入浮层树 OverlayTree。
 * 以 {@code blocksMouse=true} 推入，形成 blocksMouse 整区不透明/幕布（相对点击穿透），
 * 悬停其上即吸收鼠标事件；外部输入经 {@code dismissOnOutsideClick} 路由到
 * {@code onOutsideInteraction()} 外部交互钩子自动收起，与其它浮层为多浮层共存（无竞争）关系。
 * 关闭统一走 {@code close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名）。</p>
 */
public class ContextMenu extends OverlayElement<ContextMenu> {
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

    /**
     * 在指定位置显示菜单：先脱离旧布局父容器，再以幕布浮层做根级推入。
     *
     * @param x 目标横坐标
     * @param y 目标纵坐标
     */
    public void show(int x, int y) {
        setPos(x, y);
        computeSize();
        open = true;
        visible = true;
        scrollAnim.set(0);
        revealAnim.animate(0, 1, 150, Easing.EASE_OUT_QUINT);
        // removeFromParent() 脱离布局父容器：仅以浮层身份参与浮层树 OverlayTree 分发，
        // 否则旧父容器仍会按原 z 序绘制，导致被后推入的父对话框等浮层盖住。
        removeFromParent();
        // pushToActiveManager 自挂载：根级推入浮层树 OverlayTree；
        // blocksMouse=true 取整区不透明/幕布语义（相对点击穿透），悬停即吸收事件。
        pushToActiveManager(/*blocksMouse=*/true);
    }

    /**
     * {@code close()} 关闭：复位动画与悬停索引，并经 {@code removeFromActiveManager} 卸载摘除浮层树 OverlayTree 节点。
     */
    @Override public void close() {
        super.close();
        revealAnim.set(0);
        scrollAnim.set(0);
        hoveredIdx = -1;
        removeFromActiveManager();
    }

    /** {@code close()} 关闭；{@code hide} 为兼容别名，优先使用 {@link #close()}。 */
    public void hide() { close(); }

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
        // 落在菜单之外：视为外部交互，直接经 hide()（即 close() 关闭）收起；
        // 其它未命中本节点的外部输入由 dismissOnOutsideClick 路由到 onOutsideInteraction() 外部交互钩子统一收起。
        if (!shapeHit(mx, my)) { hide(); return btn != 1; }
        if (btn == 0 && hoveredIdx >= 0) {
            MenuItem item = items.get(hoveredIdx);
            if (item.enabled && item.action != null) item.action.run();
            if (item.closeOnClick) hide();
            return true;
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!open || !visible || !shapeHit(mx, my)) return false;
        float totalH = items.size() * ITEM_H + 2, ms = Math.max(0, totalH - height);
        float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) va * 20));
        scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
        return true;
    }

    @Override public boolean shapeHit(double mx, double my) {
        if (!open || revealAnim.get() < 0.01f) return false;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + (int)(height * revealAnim.get());
    }
}
