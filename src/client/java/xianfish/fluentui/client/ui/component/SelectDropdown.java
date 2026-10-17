package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.util.TextDrawUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 下拉选择框：按钮本体留在布局树，下拉面板以内嵌浮层形式推入浮层层列表。
 *
  * <p>本体不是可聚焦元素：开关状态由 {@code open} 内部维护，不借用聚焦语义。
  * 下拉面板 {@code DropPanel} 本身是 {@link Element}（元素），不继承任何浮层专属类型；
  * 面板从不挂入布局树，无需 {@code removeFromParent()} 脱离布局父容器，
  * 打开时直接经 {@code overlays().push} 完成根级推入。
  * 面板的鼠标事件方法只对命中自己的部分返回 {@code true}，其余放行给底下元素树。
  * 外部输入经层记录的 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子收起；
  * 不靠 hover 丢失收起，靠显式关闭与外部关闭收起，与其它浮层为多浮层共存（无竞争）关系。
  * 关闭统一走 {@code close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名），
  * 面板 {@code close()} 委托给属主 {@code closePanel()}，集中复位 {@code open=false} 并经
  * {@code overlays().remove} 卸载摘除浮层层记录。</p>
 */
public class SelectDropdown extends TooltippingElement<SelectDropdown> {
    private static final int DD_OFF = 2, OPT_H = 20, PAD_X = 8, BTN_H = 22;

    /** 左边框相对其余三边的缩放：0.5 表示半宽。 */
    private static final float LEFT_BORDER_SCALE = 0.5f;

    /**
     * 下拉配色：本体取 {@code btn*} 各项，面板取 {@code dd*} 与 {@code opt*} 各项。
     * 常底色与边框跟输入框逐值对齐（{@code 0xFF222222} / {@code 0xFF555555}，全不透明），
     * 不沿用旧值的半透明黑底与常态蓝边。
     * <p>{@code btnFocusBorder} 对应输入框的聚焦边框：本体不是可聚焦元素，聚焦态由
     * “面板展开”充当——展开即激活，与输入框拿到焦点同色。
     * {@code optHoverBg}/{@code optSelBg} 取输入框的选区底色（0x403B82F6）同一不透明度。</p>
     */
    public record DropdownColors(int btnBg, int btnBorder, int btnFocusBorder, int btnText,
                                  int ddBg, int ddBorder, int optText,
                                  int optHoverBg, int optSelBg, int optAccent) {
        public static DropdownColors blue() {
            return new DropdownColors(0xFF222222, 0xFF555555, 0xFF3B82F6, 0xFFFFFFFF,
                0xFF222222, 0xFF555555, 0xFFFFFFFF, 0x403B82F6, 0x403B82F6, 0xFF3B82F6);
        }
    }

    protected DropdownColors colors;
    protected List<Component> options = new ArrayList<>();
    protected int selIdx = -1, maxDdH = 160;
    protected boolean open;
    protected Consumer<Integer> onChanged;
    protected DropPanel dropPanel;

    public SelectDropdown(int w, DropdownColors c) { super(w, BTN_H); this.colors = c; }
    public SelectDropdown(int w) { this(w, DropdownColors.blue()); }

    public SelectDropdown options(List<String> v) { options.clear(); v.forEach(s -> options.add(Component.literal(s))); return this; }
    public SelectDropdown options(Component... v) { options.clear(); for (Component c : v) options.add(c); return this; }
    public SelectDropdown selected(int v) { selIdx = v; return this; }
    public SelectDropdown onChanged(Consumer<Integer> c) { onChanged = c; return this; }
    public SelectDropdown maxDropdownHeight(int v) { maxDdH = v; return this; }
    public SelectDropdown maxVisible(int count) { maxDdH = count * OPT_H; return this; }
    public int selectedIndex() { return selIdx; }
    public Component selectedText() { return selIdx >= 0 && selIdx < options.size() ? options.get(selIdx) : Component.empty(); }
    public boolean isOpen() { return open; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean hov = open || (mx >= ax && mx <= ax + width && my >= ay && my <= ay + BTN_H);
        // 背景恒定全不透明，与输入框一致：悬停只改边框一档，不做半透明提亮。
        e.fill(ax, ay, ax + width, ay + BTN_H, colors.btnBg);
        // 边框三档与输入框逐档对应：常态灰、悬停亮一档、展开走聚焦蓝。
        // 左边按半宽画、其余三边满宽：outline 只能整圈同宽，这圈边框得拆开自己拼。
        int borderColor = open ? colors.btnFocusBorder : hov ? 0xFF777777 : colors.btnBorder;
        e.fill(ax, ay, ax + width, ay + 1, borderColor);
        e.fill(ax, ay + BTN_H - 1, ax + width, ay + BTN_H, borderColor);
        e.fill(ax + width - 1, ay, ax + width, ay + BTN_H, borderColor);
        TextDrawUtil.drawScaledRect(e, ax, ay, 1, BTN_H, borderColor, LEFT_BORDER_SCALE);
        Component sel = selectedText();
        Component label = sel.getString().isEmpty() ? Component.literal("\u25BC")
                : sel.copy().append(" \u25BC");
        e.text(font, label, ax + PAD_X, ay + (BTN_H - font.lineHeight) / 2, colors.btnText);
    }

    /**
     * 开关下拉面板：打开时新建 {@code DropPanel} 并做根级推入，关闭时走 {@code closePanel()} 集中复位。
     *
     * <p>打开路径发生在 {@code mousePressed} 调用栈内，清扫跑在分发前，
     * 新面板在清扫时还不存在，自然免疫，无需豁免；下一次点击恢复正常关闭判定。</p>
     */
    public void toggle() {
        open = !open;
        if (open) {
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            dropPanel = new DropPanel(ax, ay + BTN_H + DD_OFF, width);
            if (InteractionManager.active != null) InteractionManager.active.overlays().push(dropPanel);
        } else {
            closePanel();
        }
    }

    /**
      * 关闭下拉面板的唯一收口：摘除浮层层记录并集中复位状态。
     *
     * <p>经 {@code overlays().remove} 卸载面板，
     * 置空 {@code dropPanel} 并复位 {@code open=false}，使选项选中、外部点击（经层记录的
     * {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子）、
     * 失焦与开关切换各条关闭路径收敛到同一形状，避免某条路径遗漏复位。</p>
     */
    private void closePanel() {
        if (dropPanel != null && InteractionManager.active != null) InteractionManager.active.overlays().remove(dropPanel);
        dropPanel = null;
        // 关闭时状态复位的唯一收口：各条关闭路径（选项选中、外部点击、失焦、开关切换）
        // 都回到这里，把 SelectDropdown 收敛到同一形状——`open=false` 且无面板。
        // 若无此收口，DropPanel.mousePressed 等每条路径都要自行记得置 `open=false`，新增路径极易遗漏。
        open = false;
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0) return false;
        int ay = getAbsoluteY();
        if (e.y() >= ay && e.y() <= ay + BTN_H && e.x() >= getAbsoluteX() && e.x() <= getAbsoluteX() + width) {
            toggle(); return true;
        }
        return false;
    }

    private class DropPanel extends Element<DropPanel> {

        final Animator revealAnim = new Animator();
        final Animator scrollAnim = new Animator();
        int hoveredIdx = -1;

        /**
         * {@code close()} 关闭：面板自身不直接摘除节点，而是委托给属主 {@code closePanel()}，
         * 由属主统一经 {@code overlays().remove} 卸载并复位 {@code open=false}。
         * 外部关闭经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子，
         * 最终同样回到本方法（{@code hide}/{@code dismiss} 为兼容别名，优先使用 {@code close()}）。
         */
        public void close() { visible = false; if (InteractionManager.active != null) InteractionManager.active.releasePressOwnerIfOwnerIs(this); closePanel(); }

        @Override public void onOutsideInteraction() { close(); }

        DropPanel(int px, int py, int pw) {
            super(px, py, pw, 10);
            revealAnim.set(0); scrollAnim.set(0);
            visible = true;
            font = SelectDropdown.this.font;
            float fullH = Math.min(options.size() * OPT_H, maxDdH);
            height = (int) fullH;
            float target = Math.max(0, Math.min(selIdx * OPT_H, options.size() * OPT_H - Math.min(options.size() * OPT_H, maxDdH)));
            scrollAnim.set(target);
            revealAnim.animate(0, 1, 200, Easing.EASE_OUT_QUINT);
        }

        @Override protected void measure() {}

        @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
            if (!visible || font == null) return;
            float revealT = revealAnim.get();
            if (revealT < 0.01f) return;

            int px = getAbsoluteX(), py = getAbsoluteY();
            x = SelectDropdown.this.getAbsoluteX();
            y = SelectDropdown.this.getAbsoluteY() + BTN_H + DD_OFF;

            float contentH = options.size() * OPT_H, fullH = Math.min(contentH, maxDdH);
            float curH = fullH * revealT;
            if (curH < 1) return;

            e.nextStratum();
            e.enableScissor(px, py, px + width, py + (int) curH);
            e.fill(px, py, px + width, py + (int) fullH, colors.ddBg);
            e.outline(px, py, width, (int) fullH, colors.ddBorder);

            for (int i = 0; i < options.size(); i++) {
                int oy = py + i * OPT_H - (int) scrollAnim.get(), ob = oy + OPT_H;
                if (ob < py || oy > py + (int) curH) continue;
                boolean sel = i == selIdx, h = mx >= px && mx <= px + width && my >= oy && my <= ob;
                int optBg = sel ? colors.optSelBg : h ? colors.optHoverBg : 0x00000000;
                if (optBg != 0) e.fill(px + 1, oy, px + width - 1, ob, optBg);
                if (sel) e.fill(px + 1, oy, px + 4, ob, colors.optAccent);
                e.text(font, options.get(i), px + PAD_X + (sel ? 4 : 0), oy + (OPT_H - font.lineHeight) / 2, colors.optText);            }

            // 悬停索引不在渲染期写：hoveredIdx 由 refreshHover（输入路径）独占写入。
            e.disableScissor();
        }

        /**
         * 悬停索引刷新：{@link #hoveredIdx} 的唯一写入点，由带坐标的输入方法用事件坐标当场算。
         *
         * <p>算法与原 {@code extractRenderState} 末尾被移走的两行同源：复位 -1 后，
         * 仅当 {@code my} 落在 {@code [py, py + curH]}（{@code curH = min(options.size() * OPT_H, maxDdH) * revealAnim.get()}）
         * 内时按 {@code (my - py + scrollAnim.get()) / OPT_H} 就地求行号。行高 OPT_H、滚动偏移、
         * 展开进度、视口高度全读本组件现有字段；与原实现一致，不设横向门限（面板命中分发已由
         * {@link #shapeHit} 把关）。{@code py} 取 {@link #getAbsoluteY()}，与渲染路径同一取值表达式。</p>
         */
        private void refreshHover(double mx, double my) {
            hoveredIdx = -1;
            if (!visible) return;
            // 事件坐标是 double、渲染入参是 int：入口处收窄一次，后续算式与渲染路径逐字相同。
            int cy = (int) my;
            int py = getAbsoluteY();
            float curH = Math.min(options.size() * OPT_H, maxDdH) * revealAnim.get();
            if (cy >= py && cy <= py + curH) {
                int ri = (int) ((cy - py + scrollAnim.get()) / OPT_H);
                if (ri >= 0 && ri < options.size()) hoveredIdx = ri;
            }
        }

        @Override public void mouseEntered(HoverEvent e) { super.mouseEntered(e); refreshHover(e.x(), e.y()); }
        @Override public void mouseMoved(HoverEvent e) { super.mouseMoved(e); refreshHover(e.x(), e.y()); }
        @Override public void mouseExited(HoverEvent e) { super.mouseExited(e); refreshHover(e.x(), e.y()); }

        @Override public boolean mousePressed(MousePressedEvent e) {
            if (e.button() != 0) return false;
            // 先按事件坐标当场算，再读 hoveredIdx：选中的是“这一刻光标压着的那一项”，
            // 而非上一帧渲染时留下的值。
            refreshHover(e.x(), e.y());
            if (hoveredIdx >= 0) {
                selIdx = hoveredIdx; if (onChanged != null) onChanged.accept(selIdx);
                closePanel(); return true;  // 关闭收口集中复位状态
            }
            if (shapeHit(e.x(), e.y())) { closePanel(); return true; }
            return false;
        }

        @Override public boolean mouseScrolled(MouseScrolledEvent e) {
            if (!visible) return false;
            float ch = options.size() * OPT_H, fh = Math.min(ch, maxDdH), ms = Math.max(0, ch - fh);
            float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) e.vertical() * 20));
            scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
            return true;
        }

        @Override public boolean shapeHit(double mx, double my) {
            if (revealAnim.get() < 0.01f) return false;
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            int curH = (int) (Math.min(options.size() * OPT_H, maxDdH) * revealAnim.get());
            return mx >= ax && mx <= ax + width && my >= ay && my <= ay + curH;
        }
    }
}
