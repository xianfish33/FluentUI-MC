package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public class SelectBox extends TooltippingElement<SelectBox> {
    // GAP 是选中块相对外框的内缩量：边框与填充之间留出未选中时的背景色。
    private static final int SIZE = 9, GAP = 2;

    public record SelectBoxColors(int border, int bg, int fill,
                                   int hoverBorder, int hoverBg, int hoverFill) {
        public static SelectBoxColors blue() {
            return new SelectBoxColors(0xFF444444, 0xFF2A2A2A, 0xFF3B82F6, 0xFF777777, 0xFF333333, 0xFF3B82F6);
        }
    }

    protected SelectBoxColors colors;
    protected boolean selected;
    protected Runnable onClick;
    protected final Animator hoverAnim = new Animator();
    protected final Animator pressAnim = new Animator();
    private boolean pending;

    public SelectBox(SelectBoxColors c) { super(SIZE, SIZE); this.colors = c; hoverAnim.set(0); }
    public SelectBox() { this(SelectBoxColors.blue()); }

    public SelectBox onClick(Runnable r) { onClick = r; return this; }
    public boolean isSelected() { return selected; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        float ht = hoverAnim.get();

        int borderCol = Colors.lerp(colors.border, colors.hoverBorder, ht);
        int bgCol = Colors.lerp(colors.bg, colors.hoverBg, ht);

        e.fill(ax, ay, ax + width, ay + height, borderCol);
        e.fill(ax + 1, ay + 1, ax + width - 1, ay + height - 1, bgCol);

        if (selected) {
            // 选中块按 GAP 内缩：边框与填充之间露出未选中时的背景色，不拿背景当填充物铺满。
            e.fill(ax + GAP, ay + GAP, ax + width - GAP, ay + height - GAP, colors.fill);
        }
        int pa = (int) (pressAnim.get() * 0x50);
        if (pa > 0) e.fill(ax + 1, ay + 1, ax + width - 1, ay + height - 1, (pa << 24) | 0xFFFFFF);
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0) return false;
        if (InteractionManager.hovering(this, e.x(), e.y())) {
            pending = true;
            pressAnim.animate(0, 1, 60, Easing.EASE_IN_CUBIC);
            return true;
        }
        return false;
    }

    @Override public boolean pressReleased(MouseReleasedEvent e, boolean hit) {
        boolean wp = pending; pending = false; pressAnim.animate(1, 0, 100, Easing.EASE_OUT_CUBIC);
        if (wp && hit) {
            selected = !selected;
            if (onClick != null) onClick.run();
            return true;
        }
        return wp;
    }

    // 悬停动画由进入／离开事件驱动，命中查询不带副作用。
    // 点按门控走交互管理器悬停仲裁，与分发、渲染读到同一结果。
    @Override public void mouseEntered(HoverEvent e) {
        super.mouseEntered(e);
        hoverAnim.animate(hoverAnim.get(), 1, 100, Easing.EASE_OUT_CUBIC);
    }
    @Override public void mouseExited(HoverEvent e) {
        super.mouseExited(e);
        hoverAnim.animate(hoverAnim.get(), 0, 100, Easing.EASE_OUT_CUBIC);
    }
}
