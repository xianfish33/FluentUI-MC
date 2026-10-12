package xianfish.fluentui.client.ui.interaction;

import xianfish.fluentui.client.ui.component.Tooltip;
import xianfish.fluentui.client.ui.element.*;
import xianfish.fluentui.client.ui.interaction.input.*;
import xianfish.fluentui.client.ui.overlay.OverlayManager;
import xianfish.fluentui.client.ui.overlay.OverlayTree;
import xianfish.fluentui.client.ui.tick.TickWalker;

import java.util.List;
import java.util.function.Predicate;

public class InteractionManager {


    private final LayoutElement<?> root;
    public LayoutElement<?> root() { return root; }
    /** 浮层管理器（Screen 持有，本类只借用引用做路由遍历，不拥有、不管理生命周期）。 */
    private final OverlayManager overlays;
    /**
     * 当前焦点目标（位置无关事件的分发目标），同一时刻至多一个。
     * 由点击链的焦点转移统一设置／清除，元素不得绕过
     * {@link #setFocusTarget}/{@link #clearFocusTarget} 自行其是。
     */
    private Focusable focusedTarget;

    private Element<?> pressOwner;
    private int pressButton;
    private int pressFlags;
    private Element<?> mouseCapture;
    private int captureButton;
    private double captureStartX, captureStartY;
    private double capturePrevX, capturePrevY;
    private double captureLastX, captureLastY;
    private boolean captureHovered;
    private Element<?> pendingCapture;
    private int suspendLevel;
    private Element<?> hoverOwner;
    private double lastHoverX, lastHoverY;

    public static InteractionManager active;

    @SuppressWarnings("unused")
    public Element<?> hoverOwner() { return hoverOwner; }

    public void updateHover(HoverEvent e) {
        double mx = e.x();
        double my = e.y();
        Element<?> next = null;
        if (suspendLevel <= 0) {
            next = topmostAnywhere(mx, my);
        }
        if (next != hoverOwner) {
            if (hoverOwner != null) hoverOwner.mouseExited(e);
            hoverOwner = next;
            if (hoverOwner != null) hoverOwner.mouseEntered(e);
        } else if (hoverOwner != null && (mx != lastHoverX || my != lastHoverY)) {
            hoverOwner.mouseMoved(e);
        }
        lastHoverX = mx;
        lastHoverY = my;
    }

    /**
     * 全 UI 命中测试 {@code topmostAnywhere}：先走浮层树（视觉上在上层的先命中），再走根元素树。悬停归属跟踪与 Tooltip 消除遍历都必须在全 UI 范围内找元素，而不能只在根树里找。
     */
    public Element<?> topmostAnywhere(double mx, double my) {
        Element<?> hit = overlays.tree().topmostInTree(mx, my);
        if (hit != null) return hit;
        return root != null ? Hit.topmost(root, mx, my) : null;
    }

    /**
     * 悬停仲裁 {@code isHover}：交互管理器主导的多树悬停唯一入口。
     * 粗筛（L0 裸矩形＋可见性）→ 全 UI 最上层命中（浮层树优先，见 {@link #topmostAnywhere}）
     * → 归属判定：命中是本元素自身或其后代（容器语义：悬停子元素时容器仍算悬停）。
     * 挂起期（{@code suspendLevel > 0}）一律返回 {@code false}，与 {@link #updateHover} 同语义。
     * 这里不缓存命中结果。
     */
    public boolean isHover(Element<?> el, double mx, double my) {
        if (el == null || !el.isVisible()) return false;
        if (!Element.rectHit(el, mx, my)) return false;
        if (suspendLevel > 0) return false;
        Element<?> top = topmostAnywhere(mx, my);
        if (top == null) return false;
        for (Element<?> p = top; p != null; p = p.getParent()) {
            if (p == el) return true;
        }
        return false;
    }

    /**
     * 静态门面 {@code hovering}：组件侧唯一的悬停查询（替代已删除的 {@code Element.isHovered}）。
     * 无活动管理器时回退 L0 裸矩形，保证无 IM 上下文（如单测）不崩。
     */
    public static boolean hovering(Element<?> el, double mx, double my) {
        InteractionManager im = active;
        if (im == null) return el != null && Element.rectHit(el, mx, my);
        return im.isHover(el, mx, my);
    }

    public InteractionManager(LayoutElement<?> root, OverlayManager overlays) {
        this.root = root;
        this.overlays = overlays;
        // active 不在构造时自装：由驱动方在 init 显式安装、在 removed 显式卸载。
    }

    /** 返回 Screen 持有的浮层管理器（借用引用）。浮层的推入／移除经此访问，本类不代持。 */
    public OverlayManager overlays() { return overlays; }

    /**
     * 通知浮层中设置了 {@code dismissOnOutsideClick} 且不在光标下的层发生了外部交互：
     * 每一层触发一次 {@link Element#onOutsideInteraction} 外部交互钩子。由四个鼠标分发器在
     * 明确的用户输入（点击／松开／拖拽／滚轮）分发<b>前</b>调用，悬停有意不走这里。
     *
     * <p>层间父子影响：子层操作，父层豁免关闭；父层关闭，所有子层连带(见 {@link OverlayTree#remove})。
     *
     * <p>清扫跑在分发前：分发中诞生的新浮层在清扫时还不存在，自然免疫，无需额外豁免机制（去留是 (输入, 分发前状态) 的函数）。
     */
    private void dismissOverlaysOutside(double mx, double my) {
        List<OverlayTree.Layer> layerList = overlays.tree().layers();
        for (OverlayTree.Layer layer : layerList) {
            Element<?> el = layer.root();
            if (!el.isVisible()) continue;
            if (overlays.tree().subtreeHit(el, mx, my)) continue;  // 光标落在本层内
            if (!layer.dismissOnOutsideClick()) continue;

            // 若点击落在其子孙浮层内，则该浮层不关闭（保留父关闭链）
            boolean descendantHit = false;
            for (OverlayTree.Layer other : layerList) {
                if (other == layer) continue;
                Element<?> cur = other.parent();
                while (cur != null) {
                    if (cur == el) {
                        if (other.root().isVisible() && overlays.tree().subtreeHit(other.root(), mx, my)) {
                            descendantHit = true;
                        }
                        break;
                    }
                    OverlayTree.Layer parentLayer = overlays.tree().findLayer(cur);
                    cur = parentLayer == null ? null : parentLayer.parent();
                }
                if (descendantHit) break;
            }
            if (descendantHit) continue;

            el.onOutsideInteraction();
        }
    }

    /** 当前焦点目标（可为 {@code null}）；供元素领焦／清焦与键盘分发使用。 */
    public Focusable focusedTarget() { return focusedTarget; }
    /**
     * 登记焦点目标：由 {@code FocusableElement} 领焦时调用。
     * 直接覆盖当前目标——调用方（点击链转移算法）负责先让当前目标失焦。
     */
    public void setFocusTarget(Focusable f) { focusedTarget = f; }
    /** 清除焦点目标：仅当当前目标同一性等于 {@code f} 时清零，防止误删新目标。 */
    public void clearFocusTarget(Focusable f) { if (focusedTarget == f) focusedTarget = null; }

    public boolean hasFocusedTextInput() {
        return focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver;
    }

    public boolean mousePressed(MousePressedEvent e) {
        double mx = e.x();
        double my = e.y();
        int btn = e.button();
        if (pressOwner != null && (pressFlags & Element.BLOCK_RIGHT) != 0 && btn == 1) return true;

        pendingCapture = null;
        pressOwner = null;

        if (mouseCapture != null) {
            if (mouseCapture.mousePressed(e)) return true;
        }

        if (suspendLevel > 0) return false;

        // 外部交互关闭前移：先按分发前状态定各浮层去留，再分发。
        // 分发中诞生的新浮层在清扫时还不存在，自然免疫——无需额外豁免机制。
        dismissOverlaysOutside(mx, my);

        // L3 全 UI 命中一次算出，向下复用：Tooltip 消除与焦点转移读同一份命中，
        // 各环节共用同一判定结果。
        Element<?> hit = topmostAnywhere(mx, my);
        Element<?> clicked = hit;
        while (clicked != null) {
            if (clicked instanceof TooltippingElement<?> t) { t.dismissTooltip(); break; }
            clicked = clicked.getParent();
        }
        // 全链统一收起 tooltip：被动节点不收 miss 调用，隐式隐藏在此处显式执行
        // （点击 tooltip 自身同样收起；无 tooltip 时空操作）。
        Tooltip.hide();

        // 焦点转移（分发的前置条件：处理器要读到新焦点状态，必须先定焦点）。
        // 当前目标在本次命中之外（含家族）则失焦；命中可聚焦元素且当前无主则领焦。
        // 命中自家族长（如输入框的右键菜单／候选下拉）视为境内，不失焦。
        Focusable prev = focusedTarget;
        if (prev instanceof FocusableElement<?> fe) {
            if (!fe.isInFocusFamily(hit)) fe.defocus();
        } else if (prev != null) {
            prev.defocus();
        }
        if (hit instanceof FocusableElement<?> fe && focusedTarget == null) {
            fe.claimFocus();
        }

        // 按下链：从命中元素自身开始，沿父链向上到树根（浮层层根无父，层与层之间父指针断开，
        // 所以不会串进根树或其它层）。消费者就是按下会话的 pressOwner。返回值语义维持现状：
        // 只有 mousePressed 链消费（if (...) return true），其它三条链是通知式。
        Element<?> consumer = null;
        for (Element<?> el = hit; el != null && consumer == null; el = el.getParent()) {
            if (el.mousePressed(e)) consumer = el;
        }
        boolean consumed = consumer != null;
        if (consumed) {
            pressOwner = consumer;
            pressButton = btn;
            pressFlags = consumer.pressFlags();
        }

        if (pendingCapture != null) {
            mouseCapture = pendingCapture;
            captureButton = btn;
            captureStartX = capturePrevX = captureLastX = mx;
            captureStartY = capturePrevY = captureLastY = my;
            captureHovered = false;
            pendingCapture = null;
            return true;
        }

        return consumed;
    }

    public boolean mouseReleased(MouseReleasedEvent e) {
        double mx = e.x();
        double my = e.y();
        int btn = e.button();
        if (mouseCapture != null) {
            if (mouseCapture == pressOwner) pressOwner = null;
            capturePrevX = captureLastX; capturePrevY = captureLastY;
            captureLastX = mx; captureLastY = my;

            if (captureHovered) {
                captureHovered = false;
                mouseCapture.mouseExited(new HoverEvent(e.x(), e.y(), e.modifiers()));
            }

            boolean consumed = mouseCapture.mouseReleased(e);
            releaseMouseCapture();
            if (consumed) return true;
        }

        if (suspendLevel > 0) return false;

        if (pressOwner != null && pressOwner != mouseCapture && pressButton == btn) {
            Element<?> owner = pressOwner;
            pressOwner = null;
            if ((pressFlags & Element.CAPTURE_RELEASE) != 0 && owner.isVisible())
                owner.mouseReleased(e);
            pressFlags = 0;
            return true;
        }

        // 外部交互关闭前移：分发前定去留（见 mousePressed）。
        dismissOverlaysOutside(mx, my);

        // 枝叶→主干：从命中元素自身开始，沿父链向上，通知式且不截断后续分发。
        Element<?> cur = topmostAnywhere(mx, my);
        while (cur != null) {
            cur.mouseReleased(e);
            cur = cur.getParent();
        }
        return false;
    }

    public boolean mouseDragged(MouseDraggedEvent e) {
        double mx = e.x();
        double my = e.y();
        int btn = e.button();
        double dx = e.dx();
        double dy = e.dy();
        if (mouseCapture != null) {
            if (!mouseCapture.isVisible()) { releaseMouseCapture(); return false; }

            boolean hovered = Element.isHit(mouseCapture, mx, my);
            if (hovered && !captureHovered) {
                captureHovered = true;
                mouseCapture.mouseEntered(new HoverEvent(e.x(), e.y(), e.modifiers()));
            } else if (!hovered && captureHovered) {
                captureHovered = false;
                mouseCapture.mouseExited(new HoverEvent(e.x(), e.y(), e.modifiers()));
            }

            capturePrevX = captureLastX; capturePrevY = captureLastY;
            captureLastX = mx; captureLastY = my;

            boolean consumed = mouseCapture.mouseDragged(new MouseDraggedEvent(mx, my, btn, captureLastX - capturePrevX, captureLastY - capturePrevY, e.modifiers()));
            if (consumed) return true;
        }

        if (suspendLevel > 0) return false;

        if (pressOwner != null && pressOwner != mouseCapture && pressButton == btn
                && (pressFlags & Element.CAPTURE_DRAG) != 0) {
            if (!pressOwner.isVisible()) { pressOwner = null; pressFlags = 0; }
            else {
                pressOwner.mouseDragged(e);
                return true;
            }
        }

        // 外部交互关闭前移：分发前定去留（见 mousePressed）。
        dismissOverlaysOutside(mx, my);

        // 枝叶→主干：从命中元素自身开始，沿父链向上，通知式且不截断后续分发。
        Element<?> cur = topmostAnywhere(mx, my);
        while (cur != null) {
            cur.mouseDragged(e);
            cur = cur.getParent();
        }
        return false;
    }

    public boolean mouseScrolled(MouseScrolledEvent e) {
        double mx = e.x();
        double my = e.y();
        double ha = e.horizontal();
        double va = e.vertical();
        if (mouseCapture != null) {
            if (mouseCapture.mouseScrolled(e)) return true;
        }

        if (suspendLevel > 0) return false;

        // 外部交互关闭前移：分发前定去留（见 mousePressed）。
        dismissOverlaysOutside(mx, my);

        // 枝叶→主干：从命中元素自身开始，沿父链向上，通知式且不截断后续分发。
        Element<?> cur = topmostAnywhere(mx, my);
        while (cur != null) {
            cur.mouseScrolled(e);
            cur = cur.getParent();
        }
        return false;
    }

    /** 每帧推进：根元素树与浮层树全部节点（不看可见性）。 */
    public void tick() {
        TickWalker.forEachElement(root, overlays != null ? overlays.tree() : null, Element::tick);
    }

    public boolean keyPressed(KeyPressedEvent e) {
        return dispatchKeyboard(kr -> kr.keyPressed(e));
    }

    public boolean keyReleased(KeyReleasedEvent e) {
        return dispatchKeyboard(kr -> kr.keyReleased(e));
    }

    public boolean charTyped(CharTypedEvent e) {
        return dispatchKeyboard(kr -> kr.charTyped(e));
    }

    public boolean preeditUpdated(PreeditUpdatedEvent e) {
        if (e == null) {
            return dispatchKeyboard(kr -> kr.preeditUpdated(null));
        }
        return dispatchKeyboard(kr -> kr.preeditUpdated(e));
    }

    /**
     * 四条键盘路由共用的分发实现：暂停期间直接丢弃；否则把动作施加到
     * 该收键盘的元素上；找不到这样的元素就返回 false（事件按现状丢弃）。
     */
    private boolean dispatchKeyboard(Predicate<KeyboardReceiver> action) {
        if (suspendLevel > 0) return false;
        KeyboardReceiver receiver = resolveKeyboardReceiver();
        return receiver != null && action.test(receiver);
    }

    /**
     * 解析本帧该收键盘的元素。主路径是焦点目标（须在焦点上且是键盘接收者）；
     * 焦点目标为空、未聚焦或不是键盘接收者时，从焦点目标（无则从根）沿
     * 父链向上找第一个仍在焦点上的键盘接收者——只取沿途第一个，不逐层投递；
     * 找不到返回 null。未聚焦的焦点接收者沿途一律跳过，保持"未聚焦不收键"。
     */
    private KeyboardReceiver resolveKeyboardReceiver() {
        if (focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver kr)
            return kr;
        Element<?> start = focusedTarget instanceof Element<?> el ? el : root;
        for (Element<?> p = start; p != null; p = p.getParent()) {
            if (p instanceof KeyboardReceiver kr
                    && (!(kr instanceof Focusable f) || f.isFocused()))
                return kr;
        }
        return null;
    }

    @SuppressWarnings("unused")
    public boolean requestMouseCapture(Element<?> owner) {
        if (owner == null) return false;
        pendingCapture = owner;
        return true;
    }

    public void releaseMouseCapture() {
        if (mouseCapture == null) return;
        if (captureHovered) {
            captureHovered = false;
            mouseCapture.mouseExited(new HoverEvent(captureLastX, captureLastY, Modifiers.NONE));
        }
        mouseCapture = null;
        // 本方法在释放后把七个捕获字段一并清零：否则无捕获期间
        // captureDX()／captureDeltaX() 等访问器仍返回上一段手势的残留增量与起点，
        // 下一段手势在重新领到捕获前读到的全是残留值。
        captureButton = 0;
        captureStartX = captureStartY = 0;
        capturePrevX = capturePrevY = 0;
        captureLastX = captureLastY = 0;
    }

    @SuppressWarnings("unused")
    public boolean isMouseCaptured() { return mouseCapture != null; }
    @SuppressWarnings("unused")
    public Element<?> mouseCaptureOwner() { return mouseCapture; }
    @SuppressWarnings("unused")
    public double captureStartX() { return captureStartX; }
    @SuppressWarnings("unused")
    public double captureStartY() { return captureStartY; }
    @SuppressWarnings("unused")
    public double captureDeltaX() { return captureLastX - captureStartX; }
    @SuppressWarnings("unused")
    public double captureDeltaY() { return captureLastY - captureStartY; }
    @SuppressWarnings("unused")
    public double captureDX() { return captureLastX - capturePrevX; }
    @SuppressWarnings("unused")
    public double captureDY() { return captureLastY - capturePrevY; }
    @SuppressWarnings("unused")
    public int captureButton() { return captureButton; }

    @SuppressWarnings("unused")
    public void suspend() { suspendLevel++; }
    @SuppressWarnings("unused")
    public void resume() { if (suspendLevel > 0) suspendLevel--; }
    @SuppressWarnings("unused")
    public boolean isSuspended() { return suspendLevel > 0; }

    /**
     * 如果 {@link #pressOwner} 当前指向 {@code owner}，则释放它，并清除 {@link #pressFlags}。
     * 供覆盖层关闭路径使用，这样陈旧的按压引用就不会渗入下一次点击。
     */
    public void releasePressOwnerIfOwnerIs(Element<?> owner) {
        if (pressOwner == owner) {
            pressOwner = null;
            pressFlags = 0;
        }
    }
}
