package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.TooltippingElement;

public class SelectBox extends TooltippingElement<SelectBox> {
    private static final int SIZE = 9, GAP = 1;

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
            e.fill(ax + GAP, ay + GAP, ax + width - GAP, ay + height - GAP, colors.fill);
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) return false;
        if (isHovered(mx, my)) {
            selected = !selected;
            if (onClick != null) onClick.run();
            return true;
        }
        return false;
    }

    // 悬停动画由进入／离开事件驱动，不再在命中查询里带副作用。
    // 查询保持为纯几何判定（基类实现），命中、分发、渲染读到同一结果。
    @Override public void mouseEntered(double mx, double my) {
        hoverAnim.animate(hoverAnim.get(), 1, 100, Easing.EASE_OUT_CUBIC);
    }
    @Override public void mouseExited() {
        hoverAnim.animate(hoverAnim.get(), 0, 100, Easing.EASE_OUT_CUBIC);
    }
}
