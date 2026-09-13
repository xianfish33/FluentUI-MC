package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.OverlayElement;
import java.util.ArrayList;
import java.util.List;

/**
 * 右下角消息浮层：挂载于浮层树 OverlayTree 的穿透式通知浮层。
 *
 * <p>继承浮层基类 OverlayElement，从不挂入布局树，无需 {@code removeFromParent()} 脱离布局父容器；
 * 经 {@code pushToActiveManager} 自挂载完成根级推入。以 {@code blocksMouse=false} 推入，
 * 取 blocksMouse 整区不透明/幕布 vs 点击穿透中的点击穿透一侧，所有鼠标事件均返回 {@code false}，
 * 底下控件照常交互。不靠 hover 丢失收起，靠自动消失定时器与外部关闭收起：
 * 外部输入经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子；
 * 与其它浮层为多浮层共存（无竞争）关系。关闭统一走 {@code close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名）。</p>
 */
public class MessageFlyout extends OverlayElement<MessageFlyout> {
    private static final int PAD = 8, SPACING = 4, BORDER_W = 3, MARGIN = 12;
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
     * 打开浮层：计算尺寸后经 {@code pushToActiveManager} 自挂载完成根级推入。
     *
     * <p>以 {@code blocksMouse=false} 推入，取点击穿透语义（相对整区不透明/幕布），
     * 浮层树 OverlayTree 分发时不阻断下层浮层与布局树，外部输入仍经
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
        pushToActiveManager(/*blocksMouse=*/false);
    }

    /**
     * {@code close()} 关闭：复位滑入动画，触发 {@code onDismiss}，并经 {@code removeFromActiveManager} 卸载摘除浮层树 OverlayTree 节点。
     */
    @Override public void close() {
        super.close();
        slideAnim.set(0);
        closeHovered = false;
        if (onDismiss != null) onDismiss.run();
        removeFromActiveManager();
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
        for (Element<?> a : actions) a.tick();
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
        e.fill(bx, by, bx + BORDER_W, by + height, borderColor);

        int tx = bx + PAD + BORDER_W, ty = by + PAD;
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

        int cx = bx + width - PAD - CLOSE_SIZE, cy = by + PAD;
        boolean ch = mx >= cx && mx <= cx + CLOSE_SIZE && my >= cy && my <= cy + CLOSE_SIZE;
        if (ch != closeHovered) { closeHovered = ch; closeAnim.animate(closeAnim.get(), ch ? 1 : 0, 80, Easing.EASE_OUT_CUBIC); }
        int ca = 0x80 + (int)(closeAnim.get() * 0x7F);
        e.text(font, "\u2715", cx, cy, (ca << 24) | 0xFFFFFF);

        e.disableScissor();
    }

    /**
     * 点击处理：命中关闭钮或动作子元素时消费事件；其余一律返回 {@code false} 不吞事件，
     * 以便外部点击经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子，
     * 由浮层树 OverlayTree 的外部关闭清扫统一收起（点击穿透语义）。
     */
    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!open || !visible || btn != 0) return false;
        int cx = getAbsoluteX() + width - PAD - CLOSE_SIZE;
        int cy = getAbsoluteY() + PAD;
        if (mx >= cx && mx <= cx + CLOSE_SIZE && my >= cy && my <= cy + CLOSE_SIZE) { hide(); return true; }
        for (Element<?> a : actions) {
            if (a.mouseClicked(mx, my, btn)) return true;
        }
        return false;
    }

    /** {@code close()} 关闭；{@code dismiss} 为兼容别名，优先使用 {@link #close()}。 */
    public void dismiss() { close(); }

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
