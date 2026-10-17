package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.util.TextDrawUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * 右下角消息浮层：推入浮层层列表的穿透式通知浮层。
 *
 * <p>它本身是 {@link Element}（元素），不继承任何浮层专属类型，也从不挂入布局树，
 * 无需 {@code removeFromParent()} 脱离布局父容器；经 {@code overlays().push} 根级推入完成挂载。
 * 它的穿透性来自自己的事件方法返回 {@code false}——分发预检只看可见且命中，两种情况都先问它，
 * 它不消费就放行给更低层与根元素树，底下控件照常交互。
 * 不靠 hover 丢失收起，靠自动消失定时器与外部关闭收起：
 * 外部输入经层记录的 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子；
 * 与其它浮层为多浮层共存（无竞争）关系。关闭统一走 {@link #close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名）。</p>
 */
public class MessageFlyout extends Element<MessageFlyout> {
    private static final int PAD = 8, SPACING = 4, BORDER_W = 2, MARGIN = 12;
    /** 左侧色条与左边、上边、下边各留的间距——色条是浮层内的装饰，不贴浮层边缘。 */
    private static final int BORDER_INSET = 1;
    /** 关闭按钮的字形：贴在右上角，放大到两倍，内缩见 {@link #closeInset()}。 */
    private static final String CLOSE_GLYPH = "\u00D7";
    /** 关闭按钮的缩放倍数：{@code text(...)} 没有缩放参数，靠渲染矩阵放大。 */
    private static final float CLOSE_SCALE = 2.0f;
    private static final int CLOSE_SIZE = 10, MAX_WIDTH = 260;

    private int borderColor = 0xFF3B82F6;
    private int bgColor = 0xE6222222;
    private int textColor = 0xFFFFFFFF;
    private int closeColor = 0xAAFFFFFF;
    private Component message = Component.empty();
    private final List<Element<?>> actions = new ArrayList<>();
    private boolean open;
    private boolean closeHovered;
    private final Animator slideAnim = new Animator();
    private final Animator closeAnim = new Animator();
    private Runnable onDismiss;
    private long autoDismissMs = 5000;
    private long showTime;
    private float slideTarget;

    public MessageFlyout() {
        super(0, 0, MAX_WIDTH, 0);
        slideAnim.set(0); closeAnim.set(0); visible = false;
    }

    public MessageFlyout type(int color) { borderColor = color; return this; }
    public int type() { return borderColor; }

    public MessageFlyout message(String text) { message = text == null ? Component.empty() : Component.literal(text); return this; }
    public MessageFlyout message(Component c) { message = c == null ? Component.empty() : c; return this; }
    public Component message() { return message; }

    public MessageFlyout bgColor(int c) { bgColor = c; return this; }
    public MessageFlyout textColor(int c) { textColor = c; return this; }

    public MessageFlyout autoDismiss(long ms) { autoDismissMs = ms; return this; }
    public MessageFlyout onDismiss(Runnable r) { onDismiss = r; return this; }

    @SuppressWarnings("unchecked")
    public MessageFlyout addAction(Element<?> el) {
        actions.add(el);
        el.setParent(this);
        el.font(font);
        markDirty();
        return this;
    }

    public void show() { show(Component.empty()); }

    public void show(String text) {
        if (text != null && !text.isEmpty()) message = Component.literal(text);
        openFlyout();
    }

    public void show(Component text) {
        if (text != null && !text.getString().isEmpty()) message = text;
        openFlyout();
    }

    /**
     * 打开浮层：计算尺寸后经 {@code overlays().push} 根级推入完成挂载。
     *
     * <p>它的鼠标事件方法一律返回 {@code false}，所以浮层分发不会在这里截断，
     * 下层浮层与布局树照常收到事件；外部输入仍经
     * {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子自动收起。</p>
     */
    private void openFlyout() {
        if (font == null) font = net.minecraft.client.Minecraft.getInstance().font;
        for (Element<?> a : actions) a.font(font);
        computeSize();
        open = true;
        visible = true;
        closeHovered = false;
        slideTarget = 1;
        slideAnim.animate(slideAnim.get(), 1, 200, Easing.EASE_OUT_CUBIC);
        showTime = System.currentTimeMillis();
        if (InteractionManager.active != null) InteractionManager.active.overlays().push(this, true);
    }

    /**
     * {@code close()} 关闭：复位滑入动画，触发 {@code onDismiss}，并经 {@code overlays().remove} 卸载摘除浮层层记录。
     */
    public void close() {
        visible = false;
        open = false;
        if (InteractionManager.active != null) InteractionManager.active.releasePressOwnerIfOwnerIs(this);
        slideAnim.set(0);
        closeHovered = false;
        if (onDismiss != null) onDismiss.run();
        if (InteractionManager.active != null) InteractionManager.active.overlays().remove(this);
    }

    /** {@code close()} 关闭；{@code hide} 为兼容别名，优先使用 {@link #close()}。 */
    public void hide() { close(); }

    private void computeSize() {
        if (font == null) return;
        int msgW = Math.min(font.width(message) + 2, MAX_WIDTH - PAD * 2 - BORDER_W - CLOSE_SIZE - 4);
        int actW = 0;
        for (Element<?> a : actions) actW = Math.max(actW, a.getWidth());
        width = Math.max(msgW, actW) + PAD * 2 + CLOSE_SIZE + 4;
        width = Math.min(width, MAX_WIDTH);

        int msgH = font.wordWrapHeight(message, width - PAD * 2 - BORDER_W - CLOSE_SIZE - 4);
        int actH = 0;
        for (Element<?> a : actions) actH += a.getHeight() + SPACING;
        if (!actions.isEmpty()) actH -= SPACING;

        height = PAD + msgH + (actions.isEmpty() ? 0 : SPACING + actH) + PAD;

        x = MARGIN;
        y = MARGIN;
    }

    @Override protected void measure() {}

    @Override public void tick() {
        super.tick();
        if (open && autoDismissMs > 0 && System.currentTimeMillis() - showTime > autoDismissMs) {
            hide();
        }
    }

    @Override public void layout() {
        super.layout();
        for (Element<?> a : actions) a.layout();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!open || !visible || font == null) return;
        layout();
        int sw = getScreenWidth(), sh = getScreenHeight();
        int bx = sw - width - MARGIN, by = sh - (int)((height + MARGIN) * slideAnim.get());
        x = bx; y = by;

        e.nextStratum();
        float slide = slideAnim.get();
        if (slide < 0.01f) return;

        String[] lines = wordWrap(message, width - PAD * 2 - BORDER_W - CLOSE_SIZE - 4);
        int msgH = lines.length * font.lineHeight;
        e.enableScissor(bx, by, bx + width, by + height);
        e.fill(bx, by, bx + width, by + height, bgColor);
        e.fill(bx + BORDER_INSET, by + BORDER_INSET, bx + BORDER_INSET + BORDER_W, by + height - BORDER_INSET, borderColor);

        int tx = bx + BORDER_INSET + BORDER_W + PAD, ty = by + PAD;
        for (String line : lines) {
            e.text(font, line, tx, ty, textColor);
            ty += font.lineHeight;
        }

        if (!actions.isEmpty()) ty += SPACING;
        for (Element<?> a : actions) {
            a.topLeft(tx, ty);
            a.extractRenderState(e, mx, my, d);
            ty += a.getHeight() + SPACING;
        }

        int cx = closeGlyphX(), cy = closeGlyphY();
        // 关钮悬停不在渲染期写：closeHovered 及其触发的 closeAnim 过渡由 refreshHover
        // （输入路径）独占驱动，这里只按 closeAnim 当前值取透明度画 ×。
        int ca = 0x80 + (int)(closeAnim.get() * 0x7F);
        TextDrawUtil.draw(e, font, CLOSE_GLYPH, cx, cy, (ca << 24) | 0xFFFFFF, CLOSE_SCALE);

        e.disableScissor();
    }

    /**
     * 关钮的内缩：距上边与距右边都取这一截。
     *
     * <p>取法是「关钮在单行浮层里居中」需要的那一截——单行时字形比一行正文高，
     * 上下只能各让出 {@code (单行高度 - 字形高) / 2}：再大就贴边，再小就压到正文上。
     * 多行时沿用同一截，于是消息在单行与多行之间换行时关钮不跳位。</p>
     */
    private int closeInset() {
        if (font == null) return PAD;
        int singleLineH = PAD * 2 + font.lineHeight;
        return Math.max(0, (singleLineH - TextDrawUtil.scaledHeight(font, CLOSE_SCALE)) / 2);
    }

    /**
     * 关闭按钮字形的左上角：距右边与距上边都是 {@link #closeInset()}，放大到 {@link #CLOSE_SCALE} 倍。
     *
     * <p>绘制、{@link #refreshHover} 与 {@link #mousePressed} 三处共用这一对取值，
     * 保证命中框与画出来的字形对齐。</p>
     */
    private int closeGlyphX() { return getAbsoluteX() + width - closeInset() - TextDrawUtil.scaledWidth(font, CLOSE_GLYPH, CLOSE_SCALE); }
    private int closeGlyphY() { return getAbsoluteY() + closeInset(); }

    /**
     * 关钮悬停刷新：{@link #closeHovered} 的唯一写入点（{@code openFlyout}/{@code close} 的
     * 复位除外），由带坐标的输入方法用事件坐标当场算，并在布尔翻转时触发 {@link #closeAnim}
     * 过渡——判定与过渡触发的语义都与原 {@code extractRenderState} 里被移走的那两行相同。
     *
     * <p>关钮矩形与 {@link #mousePressed} 共用 {@link #closeGlyphX()} / {@link #closeGlyphY()}，
     * 命中框取缩放后的宽高，事件坐标在入口收窄为 int 后再比，与渲染入参精度一致。</p>
     */
    private void refreshHover(double mx, double my) {
        if (!open || !visible) return;
        int cx = closeGlyphX(), cy = closeGlyphY();
        int gw = TextDrawUtil.scaledWidth(font, CLOSE_GLYPH, CLOSE_SCALE), gh = TextDrawUtil.scaledHeight(font, CLOSE_SCALE);
        int px = (int) mx, py = (int) my;
        boolean ch = px >= cx && px <= cx + gw && py >= cy && py <= cy + gh;
        if (ch != closeHovered) {
            closeHovered = ch;
            closeAnim.animate(closeAnim.get(), ch ? 1 : 0, 80, Easing.EASE_OUT_CUBIC);
        }
    }

    @Override public void mouseEntered(HoverEvent e) { super.mouseEntered(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseMoved(HoverEvent e) { super.mouseMoved(e); refreshHover(e.x(), e.y()); }
    @Override public void mouseExited(HoverEvent e) { super.mouseExited(e); refreshHover(e.x(), e.y()); }

    /**
     * 点击处理：命中关闭钮或动作子元素时消费事件；其余一律返回 {@code false} 不吞事件，
     * 以便外部点击经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子，
     * 由浮层层列表的外部关闭清扫统一收起（点击穿透语义）。
     */
    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!open || !visible || e.button() != 0) return false;
        // 落点当场刷新关钮悬停：即使这颗事件没有配对的 mouseMoved，状态也是这一帧的判定。
        refreshHover(e.x(), e.y());
        int cx = closeGlyphX(), cy = closeGlyphY();
        int gw = TextDrawUtil.scaledWidth(font, CLOSE_GLYPH, CLOSE_SCALE), gh = TextDrawUtil.scaledHeight(font, CLOSE_SCALE);
        if (e.x() >= cx && e.x() <= cx + gw
            && e.y() >= cy && e.y() <= cy + gh) { hide(); return true; }
        for (Element<?> a : children()) {
            if (Element.isHit(a, e.x(), e.y()) && a.mousePressed(e)) return true;
        }
        return false;
    }

    /** {@code close()} 关闭；{@code dismiss} 为兼容别名，优先使用 {@link #close()}。 */
    public void dismiss() { close(); }

    @Override public void onOutsideInteraction() { close(); }

    @Override public List<Element<?>> children() { return actions; }

    @Override public boolean shapeHit(double mx, double my) {
        if (!open) return false;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + height;
    }

    private String[] wordWrap(Component text, int maxWidth) {
        if (font == null || text == null || text.getString().isEmpty()) return new String[]{""};
        if (maxWidth < 40) maxWidth = 200;
        var seqs = font.split(text, maxWidth);
        List<String> lines = new ArrayList<>();
        for (var seq : seqs) {
            StringBuilder sb = new StringBuilder();
            seq.accept((i, style, cp) -> { sb.appendCodePoint(cp); return true; });
            lines.add(sb.toString());
        }
        return lines.isEmpty() ? new String[]{""} : lines.toArray(new String[0]);
    }

    private static int getScreenWidth() {
        return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    private static int getScreenHeight() {
        return net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }
}
