package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * 右键上下文菜单：推入浮层层列表的顶层浮层。
 *
 * <p>它本身是 {@link Element}（元素），不继承任何浮层专属类型——是不是浮层只取决于在不在
 * 浮层层列表里。它平时不参与布局树的命中分发；{@code show()} 先经 {@code removeFromParent()}
 * 脱离布局父容器，再经 {@link xianfish.fluentui.client.ui.overlay.OverlayManager#push} 推入
 * 浮层层列表完成根级挂载。
 * 命中它就由它消费——浮层分发自最上层往下问，第一个返回 {@code true} 的层截断，底下元素树收不到；
 * 外部输入经层记录的 {@code dismissOnOutsideClick} 路由到
 * {@code onOutsideInteraction()} 外部交互钩子自动收起，与其它浮层为多浮层共存（无竞争）关系。
 * 关闭统一走 {@link #close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名）。</p>
 */
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
     * 在指定位置显示菜单：先脱离布局父容器，再以幕布浮层做根级推入。
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
        // removeFromParent() 脱离布局父容器：仅以浮层身份参与浮层层列表分发，
        // 否则布局父容器仍会按布局顺序绘制，导致被后推入的父对话框等浮层盖住。
        removeFromParent();
        // 根级推入浮层层列表，此后只作为浮层参与分发。
        if (InteractionManager.active != null) InteractionManager.active.overlays().push(this, true);
    }

    /**
     * {@code close()} 关闭：复位动画与悬停索引，并经 {@code overlays().remove} 卸载摘除浮层层记录。
     */
    public void close() {
        visible = false;
        open = false;
        if (InteractionManager.active != null) InteractionManager.active.releasePressOwnerIfOwnerIs(this);
        revealAnim.set(0);
        scrollAnim.set(0);
        hoveredIdx = -1;
        if (InteractionManager.active != null) InteractionManager.active.overlays().remove(this);
    }

    /** {@code close()} 关闭；{@code hide} 为兼容别名，优先使用 {@link #close()}。 */
    public void hide() { close(); }

    @Override public void onOutsideInteraction() { close(); }

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

        // 悬停索引不在渲染期写：hoveredIdx 由 refreshHover（输入路径）独占写入，
        // 这里保留 owned/hov 仅作本帧高亮填充的绘制判据（与输入路径同一条算式）。
        boolean owned = InteractionManager.hovering(this, mx, my);
        for (int i = 0; i < items.size(); i++) {
            int iy = ay + 1 + i * ITEM_H - (int) sc, ib = iy + ITEM_H;
            if (ib < ay || iy > ay + height) continue;
            MenuItem item = items.get(i);
            boolean hov = owned && mx >= ax && mx <= ax + width && my >= iy && my <= ib;
            if (item.bgColor != 0) e.fill(ax + 1, iy, ax + width - 1, ib, item.bgColor);
            if (hov && item.enabled) { e.fill(ax + 1, iy, ax + width - 1, ib, colors.hoverBg); }
            int tc = item.enabled ? colors.text : colors.disabledText;
            e.text(font, item.text, ax + PAD_X, iy + (ITEM_H - font.lineHeight) / 2, tc);
        }
        e.disableScissor();
    }

    /**
     * 悬停索引刷新：{@link #hoveredIdx} 的唯一写入点，由带坐标的输入方法用事件坐标当场算。
     *
     * <p>算法与原 {@code extractRenderState} 里被移走的那段同源：先复位 -1，归属不成立即返回；
     * 归属成立后逐行取 {@code iy = ay + 1 + i * ITEM_H - (int) scrollAnim.get()}，视口外跳过，
     * 行矩形命中且 {@code item.enabled} 才落索引（沿用原“后行覆盖前行”的边界语义，不加 break）。
     * 行高／滚动偏移／视口裁剪全部读本组件现有字段，不引入新几何常量。</p>
     */
    private void refreshHover(double mx, double my) {
        hoveredIdx = -1;
        if (!open || !visible) return;
        // 事件坐标是 double、渲染入参是 int：入口处收窄一次，后续算式与渲染路径逐字相同。
        int cx = (int) mx, cy = (int) my;
        if (!InteractionManager.hovering(this, cx, cy)) return;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        float sc = scrollAnim.get();
        for (int i = 0; i < items.size(); i++) {
            int iy = ay + 1 + i * ITEM_H - (int) sc, ib = iy + ITEM_H;
            if (ib < ay || iy > ay + height) continue;
            MenuItem item = items.get(i);
            boolean hov = cx >= ax && cx <= ax + width && cy >= iy && cy <= ib;
            if (hov && item.enabled) hoveredIdx = i;
        }
    }

    @Override public void mouseEntered(HoverEvent e) { super.mouseEntered(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseMoved(HoverEvent e) { super.mouseMoved(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseExited(HoverEvent e) { super.mouseExited(e); refreshHover(e.x(), e.y()); }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!open || !visible) return false;
        // 命中本节点：按事件坐标当场算本帧该高亮/该点中哪一项，再读 hoveredIdx（不依赖上一帧渲染）。
        refreshHover(e.x(), e.y());
        if (e.button() == 0 && hoveredIdx >= 0) {
            MenuItem item = items.get(hoveredIdx);
            if (item.enabled && item.action != null) item.action.run();
            if (item.closeOnClick) hide();
            return true;
        }
        return true;
    }

    @Override public boolean mouseScrolled(MouseScrolledEvent e) {
        if (!open || !visible || !shapeHit(e.x(), e.y())) return false;
        float totalH = items.size() * ITEM_H + 2, ms = Math.max(0, totalH - height);
        float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) e.vertical() * 20));
        scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
        return true;
    }

    @Override public boolean shapeHit(double mx, double my) {
        if (!open || revealAnim.get() < 0.01f) return false;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + (int)(height * revealAnim.get());
    }
}
