package xianfish.fluentui.client.ui.element.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;

/**
 * 容器的滚动条：挂在容器 {@code children} 末尾，因此按「列表位置即层级」天然是本容器最高层，
 * 命中查找逆序扫子元素时先扫到它——压在它下面的内容元素不会抢走落在条带上的按下。
 *
 * <p>两条与普通子元素不同的约定：</p>
 * <ul>
 *   <li>不参与内容尺寸、不被布局排布：容器在测量与排布时直接跳过它，位置由本元素自己算。
 *       绘制走普通子元素流程——挂在末位即画在最上，条带整体落在容器矩形内，裁不到它。</li>
 *   <li>覆写 {@link #getAbsoluteX()} / {@link #getAbsoluteY()} 直接算屏幕坐标：父链上
 *       「减去父级滚动量」那条路会把它跟内容一起带走，这里改成锚在容器视口的对应角上。</li>
 *   <li>不需要显式申请鼠标捕获：按下时它就是 {@code pressOwner}，默认的
 *       {@code CAPTURE_DRAG} 位会把拖动路由回它。</li>
 * </ul>
 */
final class ScrollBar extends Element<ScrollBar> {

    /** 滑块最短长度：内容比视口大不了多少时，至少给这么长才抓得住。 */
    private static final float MIN_THUMB = 16f;

    private final StackPanel owner;
    private boolean dragging;
    private double dragStartMouse;
    private float dragStartScroll;

    ScrollBar(StackPanel owner) {
        super(1, 1);
        this.owner = owner;
    }

    // --- 定位：锚在容器视口上，不受内容滚动影响 ---

    @Override
    public int getAbsoluteX() {
        int ax = owner.getAbsoluteX();
        return owner.vertical() ? ax + owner.getWidth() - StackPanel.SB_W : ax;
    }

    @Override
    public int getAbsoluteY() {
        int ay = owner.getAbsoluteY();
        return owner.vertical() ? ay : ay + owner.getHeight() - StackPanel.SB_W;
    }

    @Override
    public void layout() {
        super.layout();
        // 尺寸跟着容器走，只有真变了才写：width()/height() 会 markDirty 并向上传播，每帧调会拖垮布局。
        int w = owner.vertical() ? StackPanel.SB_W : owner.getWidth();
        int h = owner.vertical() ? owner.getHeight() : StackPanel.SB_W;
        if (width != w) width(w);
        if (height != h) height(h);
    }

    @Override
    protected void measure() { }

    // --- 绘制 ---

    @Override
    public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return;
        float content = owner.contentExtent(), viewport = owner.viewportExtent();
        float maxScroll = Math.max(0, content - viewport);
        if (maxScroll <= 0) return;

        float len = thumbLength(content, viewport);
        float pos = owner.scroll() / maxScroll * (viewport - len);
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean hovered = hitThumb(mx, my, pos, len);
        int color = hovered || dragging ? brighten(owner.scrollbarColor()) : owner.scrollbarColor();

        if (owner.vertical()) {
            e.fill(ax, (int) (ay + pos), ax + width, (int) (ay + pos + len), color);
        } else {
            e.fill((int) (ax + pos), ay, (int) (ax + pos + len), ay + height, color);
        }
    }

    // --- 输入：条带是本容器最高层，落上来的按下归它 ---

    @Override
    public boolean mousePressed(MousePressedEvent e) {
        if (!visible || e.button() != 0) return false;
        float content = owner.contentExtent(), viewport = owner.viewportExtent();
        float maxScroll = Math.max(0, content - viewport);
        // 没有可滚的内容时条带仍然吃掉这次按下：它是最上层，不能让底下的元素借这次点击生效。
        if (maxScroll <= 0) return true;

        float len = thumbLength(content, viewport);
        float pos = owner.scroll() / maxScroll * (viewport - len);
        double along = owner.vertical() ? e.y() : e.x();
        double base = owner.vertical() ? getAbsoluteY() : getAbsoluteX();
        if (along >= base + pos && along <= base + pos + len) {
            dragging = true;
            dragStartMouse = along;
            dragStartScroll = owner.scroll();
        }
        // 轨道区域也照收：层级上这里归滚动条，交给谁由滚动条自己决定。
        return true;
    }

    @Override
    public boolean mouseDragged(MouseDraggedEvent e) {
        if (!dragging) return false;
        float content = owner.contentExtent(), viewport = owner.viewportExtent();
        float maxScroll = Math.max(0, content - viewport);
        if (maxScroll <= 0) return true;
        float len = thumbLength(content, viewport);
        float ratio = content / viewport;
        double delta = (owner.vertical() ? e.y() : e.x()) - dragStartMouse;
        owner.applyScroll(dragStartScroll + (float) (delta * ratio));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseReleasedEvent e) {
        if (!dragging) return false;
        dragging = false;
        return true;
    }

    // --- 内部 ---

    private boolean hitThumb(double mx, double my, float pos, float len) {
        if (owner.vertical()) {
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            return mx >= ax && mx <= ax + width && my >= ay + pos && my <= ay + pos + len;
        }
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return my >= ay && my <= ay + height && mx >= ax + pos && mx <= ax + pos + len;
    }

    private static float thumbLength(float content, float viewport) {
        return Math.max(MIN_THUMB, viewport * viewport / content);
    }

    private static int brighten(int color) {
        // 半透明白色（0x40FFFFFF）的 RGB 已经是顶格，只抬 RGB 看不出变化，
        // 所以 alpha 也要一起抬：0x40 → 0x66，悬停时才看得出提亮。
        int a = Math.min(255, (int)(((color >>> 24) & 0xFF) * 1.6f));
        int r = Math.min(255, ((color >> 16) & 0xFF) + 40);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 40);
        int b = Math.min(255, (color & 0xFF) + 40);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}