package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.OverlayElement;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 下拉选择框：按钮本体留在布局树，下拉面板以内嵌浮层形式挂载于浮层树 OverlayTree。
 *
  * <p>本体不再是可聚焦元素：开关状态由 {@code open} 内部维护，不借用聚焦语义。
  * 下拉面板 {@code DropPanel} 继承浮层基类 OverlayElement；
 * 面板从不挂入布局树，无需 {@code removeFromParent()} 脱离布局父容器，
 * 打开时直接经 {@code pushOverlay} 完成根级推入（语义等价于 {@code pushToActiveManager} 自挂载，
 * 默认 {@code blocksMouse=false} 取 blocksMouse 整区不透明/幕布 vs 点击穿透中的点击穿透一侧）。
 * 外部输入经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子收起；
 * 不靠 hover 丢失收起，靠显式关闭与外部关闭收起，与其它浮层为多浮层共存（无竞争）关系。
 * 关闭统一走 {@code close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名），
 * 面板 {@code close()} 委托给属主 {@code closePanel()}，集中复位 {@code open=false} 并经
 * {@code removeOverlay} 卸载（语义对应 {@code removeFromActiveManager} 卸载）。</p>
 */
public class SelectDropdown extends TooltippingElement<SelectDropdown> {
    private static final int DD_OFF = 2, OPT_H = 20, PAD_X = 8, BTN_H = 22;

    public record DropdownColors(int btnBg, int btnBorder, int btnText,
                                  int ddBg, int ddBorder, int optText,
                                  int optHoverBg, int optSelBg, int optAccent) {
        public static DropdownColors blue() {
            return new DropdownColors(0x30000000, 0xFF3B82F6, 0xFFFFFFFF,
                0xFF1E1E1E, 0xFF333333, 0xFFFFFFFF, 0x303B82F6, 0x303B82F6, 0xFF3B82F6);
        }
    }

    protected DropdownColors colors;
    protected List<Component> options = new ArrayList<>();
    protected int selIdx = -1, maxDdH = 160;
    protected boolean open;
    protected Consumer<Integer> onChanged;
    protected DropPanel dropPanel;

    public SelectDropdown(int w, DropdownColors c) { super(w, BTN_H); this.colors = c; zIndex(1); }
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
        int bg = hov ? Colors.lerp(colors.btnBg, 0x50000000, 0.6f) : colors.btnBg;
        e.fill(ax, ay, ax + width, ay + BTN_H, bg);
        e.outline(ax, ay, width, BTN_H, colors.btnBorder);
        Component sel = selectedText();
        Component label = sel.getString().isEmpty() ? Component.literal("\u25BC")
                : sel.copy().append(" \u25BC");
        e.text(font, label, ax + PAD_X, ay + (BTN_H - font.lineHeight) / 2, colors.btnText);
    }

    /**
     * 开关下拉面板：打开时新建 {@code DropPanel} 并做根级推入，关闭时走 {@code closePanel()} 集中复位。
     *
     * <p>打开路径发生在 {@code mouseClicked} 调用栈内，清扫跑在分发前，
     * 新面板在清扫时还不存在，自然免疫，无需豁免；下一次点击恢复正常关闭判定。</p>
     */
    public void toggle() {
        open = !open;
        if (open) {
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            dropPanel = new DropPanel(ax, ay + BTN_H + DD_OFF, width);
            if (InteractionManager.active != null) InteractionManager.active.pushOverlay(dropPanel);
        } else {
            closePanel();
        }
    }

    /**
     * 关闭下拉面板的唯一收口：摘除浮层树 OverlayTree 节点并集中复位状态。
     *
     * <p>经 {@code removeOverlay} 卸载面板（语义对应 {@code removeFromActiveManager} 卸载），
     * 置空 {@code dropPanel} 并复位 {@code open=false}，使选项选中、外部点击（经
     * {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子）、
     * 失焦与开关切换各条关闭路径收敛到同一形状，避免某条路径遗漏复位。</p>
     */
    private void closePanel() {
        if (dropPanel != null && InteractionManager.active != null) InteractionManager.active.removeOverlay(dropPanel);
        dropPanel = null;
        // 关闭时状态复位的唯一收口：各条关闭路径（选项选中、外部点击、失焦、开关切换）
        // 都回到这里，把 SelectDropdown 收敛到同一形状——`open=false` 且无面板。
        // 若无此收口，DropPanel.mouseClicked 等每条路径都要自行记得置 `open=false`，新增路径极易遗漏。
        open = false;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) return false;
        int ay = getAbsoluteY();
        if (my >= ay && my <= ay + BTN_H && mx >= getAbsoluteX() && mx <= getAbsoluteX() + width) {
            toggle(); return true;
        }
        return false;
    }

    private class DropPanel extends OverlayElement<DropPanel> {

        final Animator revealAnim = new Animator();
        final Animator scrollAnim = new Animator();
        int hoveredIdx = -1;

        /**
         * {@code close()} 关闭：面板自身不直接摘除节点，而是委托给属主 {@code closePanel()}，
         * 由属主统一经 {@code removeOverlay} 卸载并复位 {@code open=false}。
         * 外部关闭经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子，
         * 最终同样回到本方法（{@code hide}/{@code dismiss} 为兼容别名，优先使用 {@code close()}）。
         */
        @Override public void close() { super.close(); closePanel(); }

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

            hoveredIdx = -1;
            if (my >= py && my <= py + curH) { int ri = (int) ((my - py + scrollAnim.get()) / OPT_H); if (ri >= 0 && ri < options.size()) hoveredIdx = ri; }
            e.disableScissor();
        }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0) return false;
            if (hoveredIdx >= 0) {
                selIdx = hoveredIdx; if (onChanged != null) onChanged.accept(selIdx);
                closePanel(); return true;  // 关闭收口集中复位状态
            }
            if (shapeHit(mx, my)) { closePanel(); return true; }
            return false;
        }

        @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
            if (!visible) return false;
            float ch = options.size() * OPT_H, fh = Math.min(ch, maxDdH), ms = Math.max(0, ch - fh);
            float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) va * 20));
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
