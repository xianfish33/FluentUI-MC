package xianfish.fluentui.client.ui.interaction;

import net.minecraft.client.input.PreeditEvent;
import xianfish.fluentui.client.ui.component.Tooltip;
import xianfish.fluentui.client.ui.element.*;
import xianfish.fluentui.client.ui.overlay.OverlayManager;
import xianfish.fluentui.client.ui.overlay.OverlayTree;

import java.util.List;
import java.util.function.Predicate;

public class InteractionManager {


    /**
     * 可聚焦元素：位置无关事件（键盘输入、IME 预编辑）的分发目标。
     *
     * <p>语义收窄为“放弃位置无关事件接收权”——不再表达开关、下拉收起等
     * 位置相关状态。内外判定由点击链按 L3 命中统一计算，元素不再自查
     * {@code isHovered} 决定自己的聚焦（见 {@code mouseClicked}）。
     */
    public interface Focusable {
        boolean isFocused();
        void defocus();
    }

    public interface KeyboardReceiver {
        boolean keyPressed(int keyCode, int scanCode, int mods);
        boolean charTyped(char chr);
        default boolean preeditUpdated(PreeditEvent event) { return false; }
    }

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

    public void updateHover(double mx, double my) {
        Element<?> next = null;
        if (suspendLevel <= 0) {
            next = topmostAnywhere(mx, my);
        }
        if (next != hoverOwner) {
            if (hoverOwner != null) hoverOwner.mouseExited();
            hoverOwner = next;
            if (hoverOwner != null) hoverOwner.mouseEntered(mx, my);
        } else if (hoverOwner != null && (mx != lastHoverX || my != lastHoverY)) {
            hoverOwner.mouseMoved(mx, my);
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
        return root != null ? HitTest.topmost(root, mx, my) : null;
    }

    public InteractionManager(LayoutElement<?> root, OverlayManager overlays) {
        this.root = root;
        this.overlays = overlays;
        active = this;
    }

    /** 返回 Screen 持有的浮层管理器（借用引用）。 */
    @SuppressWarnings("unused")
    public OverlayManager overlays() { return overlays; }

    /**
     * 对每棵根浮层做输入分发逆序的 DFS，通知其中设置了 {@code dismissOnOutsideClick}
     * 且子树不在光标下的节点发生了外部交互：每个这样的节点触发一次
     * {@link Element#onOutsideInteraction} 外部交互钩子。由四个鼠标分发器在
     * 明确的用户输入（点击／松开／拖拽／滚轮）分发<b>前</b>调用，悬停有意不走这里。
     *
     * <p>清扫跑在分发前：分发中诞生的新浮层在清扫时还不存在，自然免疫，
     * 无需豁免机制（去留是 (输入, 分发前状态) 的函数）。
     */
    private void dismissOverlaysOutside(double mx, double my) {
        for (OverlayTree.OverlayNode root : overlays.tree().roots()) {
            dismissOutsideDFS(root, mx, my);
        }
    }
    private void dismissOutsideDFS(OverlayTree.OverlayNode node, double mx, double my) {
        // 先递归子浮层。光标下的子浮层会“遮罩”其父浮层（经由子浮层，父浮层即视为在光标下）。因此：任一后代被悬停，即视为该节点在光标内。
        for (int i = node.children.size() - 1; i >= 0; i--) {
            dismissOutsideDFS(node.children.get(i), mx, my);
        }
        if (!node.element.isVisible()) return;
        if (overlays.tree().subtreeHit(node, mx, my)) return;  // 光标落在本节点子树内
        if (!node.dismissOnOutsideClick) return;
        node.element.onOutsideInteraction();
    }

    /** 推入浮层：转发到 Screen 持有的 {@link OverlayManager}，本类不拥有浮层树。 */
    public OverlayTree.OverlayNode pushOverlay(Element<?> el) {
        OverlayTree.OverlayNode n = overlays.push(el);
        return n;
    }
    /**
     * 推入阻断鼠标穿透的浮层——像模态幕布一样，未被消费的事件不再下放给下层浮层
     * 或根元素树。适用于对话框、上下文菜单，以及整个命中区域都应对输入不透明的
     * 任何浮层。外部交互关闭默认 {@code true}（多数弹窗都需要）。
     */
    public OverlayTree.OverlayNode pushOverlay(Element<?> el, boolean blocksMouse) {
        OverlayTree.OverlayNode n = overlays.push(el, null, blocksMouse, true);
        return n;
    }
    /**
     * 全参推入，可同时控制两个标志。{@code dismissOnOutsideClick=true} 时，
     * {@code InteractionManager} 会把落在本浮层子树之外的明确用户输入
     * （点击／松开／拖拽／滚轮）路由到 {@link Element#onOutsideInteraction}
     * 外部交互钩子。
     */
    public OverlayTree.OverlayNode pushOverlay(Element<?> el, boolean blocksMouse, boolean dismissOnOutsideClick) {
        OverlayTree.OverlayNode n = overlays.push(el, null, blocksMouse, dismissOnOutsideClick);
        return n;
    }
    /** 从浮层树移除浮层，连带摘除其整棵子树。 */
    public boolean removeOverlay(Element<?> el) { return overlays.remove(el); }

    /** 当前焦点目标（可为 {@code null}）；供元素领焦／清焦与键盘分发使用。 */
    public Focusable focusedTarget() { return focusedTarget; }
    /**
     * 登记焦点目标：由 {@code FocusableElement} 领焦时调用。
     * 直接覆盖旧目标——调用方（点击链转移算法）负责先让旧目标失焦。
     */
    public void setFocusTarget(Focusable f) { focusedTarget = f; }
    /** 清除焦点目标：仅当当前目标同一性等于 {@code f} 时清零，防止误删新目标。 */
    public void clearFocusTarget(Focusable f) { if (focusedTarget == f) focusedTarget = null; }

    public boolean hasFocusedTextInput() {
        return focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (pressOwner != null && (pressFlags & Element.BLOCK_RIGHT) != 0 && btn == 1) return true;

        pendingCapture = null;
        pressOwner = null;

        if (mouseCapture != null) {
            if (mouseCapture.mouseClicked(mx, my, btn)) return true;
        }

        if (suspendLevel > 0) return false;

        // 外部交互关闭前移：先按分发前状态定各浮层去留，再分发。
        // 分发中诞生的新浮层在清扫时还不存在，自然免疫——这就是豁免机制被删除的原因。
        dismissOverlaysOutside(mx, my);

        // L3 全 UI 命中一次算出，向下复用：Tooltip 消除与焦点转移读同一份命中，
        // 不再各算各的（判定分裂的根源之一）。
        Element<?> hit = topmostAnywhere(mx, my);
        Element<?> clicked = hit;
        while (clicked != null) {
            if (clicked instanceof TooltippingElement<?> t) { t.dismissTooltip(); break; }
            clicked = clicked.getParent();
        }
        // 全链统一收起 tooltip：模板化后被动节点不再收到 miss 调用，过去"分发恰好路过
        // Tooltip 节点"的隐式隐藏改在此处显式执行（点击 tooltip 自身同样收起；无 tooltip 时空操作）。
        Tooltip.hide();

        // 焦点转移（分发的前置条件：处理器要读到新焦点状态，必须先定焦点）。
        // 旧目标在本次命中之外（含家族）则失焦；命中可聚焦元素且当前无主则领焦。
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

        // 浮层树输入分发（最上层优先 DFS）：根浮层逆序、子浮层先于父浮层。
        List<OverlayTree.OverlayNode> rootList = overlays.tree().roots();
        if (!rootList.isEmpty()) {
            for (int i = rootList.size() - 1; i >= 0; i--) {
                OverlayTree.OverlayNode n = rootList.get(i);
                if (dispatchOverlayDFS(n, mx, my,
                        o -> o.element.isVisible() && (Element.isHit(o.element, mx, my) || o.blocksMouse),
                        el -> el.mouseClicked(mx, my, btn),
                        o -> o.blocksMouse && o.element.isVisible() && Element.isHit(o.element, mx, my))) return true;
            }
        }

        boolean consumed = root != null && root.mouseClicked(mx, my, btn);
        if (consumed && root.pressOwner() != null) {
            pressOwner = root.pressOwner();
            pressButton = btn;
            pressFlags = pressOwner.pressFlags();
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

    /** 浮层树分发动作：把节点元素映射为消费与否。坐标等经 lambda 捕获。 */
    @FunctionalInterface
    private interface OverlayAction { boolean handle(Element<?> el); }

    /**
     * 浮层树分发的唯一 DFS 骨架（最上层优先：子浮层先收，视觉最上层的最先拿到事件）。
     * 四条链只配三处：{@code guard} 节点预检、{@code action} 元素处理、{@code absorb} 幕布兜底。
     *
     * <p>clicked 链的 guard 为"可见且（命中或幕布）"：幕布节点保留 miss 可达，
     * 延续 ContextMenu 未命中即收起＋吞左键的区域政策；被动节点仅命中可达——
     * 它们过去收到的 miss 调用要么是空操作（MessageFlyout／下拉），要么是
     * Tooltip 的隐式"点击即藏"（现由 mouseClicked 显式收起替代，见转移段）。
     * 其余三链的 guard 沿用现状（命中即进），不补可见门，保持行为一致。
     */
    private boolean dispatchOverlayDFS(OverlayTree.OverlayNode node, double mx, double my,
                                       Predicate<OverlayTree.OverlayNode> guard,
                                       OverlayAction action,
                                       Predicate<OverlayTree.OverlayNode> absorb) {
        for (int i = node.children.size() - 1; i >= 0; i--) {
            if (dispatchOverlayDFS(node.children.get(i), mx, my, guard, action, absorb)) return true;
        }
        if (guard.test(node) && action.handle(node.element)) return true;
        return absorb.test(node);
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        if (mouseCapture != null) {
            if (mouseCapture == pressOwner) pressOwner = null;
            capturePrevX = captureLastX; capturePrevY = captureLastY;
            captureLastX = mx; captureLastY = my;

            if (captureHovered) {
                captureHovered = false;
                mouseCapture.mouseExited();
            }

            boolean consumed = mouseCapture.mouseReleased(mx, my, btn);
            releaseMouseCapture();
            if (consumed) return true;
        }

        if (suspendLevel > 0) return false;

        if (pressOwner != null && pressOwner != mouseCapture && pressButton == btn) {
            Element<?> owner = pressOwner;
            pressOwner = null;
            if ((pressFlags & Element.CAPTURE_RELEASE) != 0 && owner.isVisible())
                owner.mouseReleased(mx, my, btn);
            pressFlags = 0;
            return true;
        }

        // 外部交互关闭前移：分发前定去留（见 mouseClicked）。
        dismissOverlaysOutside(mx, my);

        // 浮层树输入分发（最上层优先 DFS，通知式：不截断后续分发）。
        List<OverlayTree.OverlayNode> rootList = overlays.tree().roots();
        if (!rootList.isEmpty()) {
            for (int i = rootList.size() - 1; i >= 0; i--) {
                OverlayTree.OverlayNode n = rootList.get(i);
                dispatchOverlayDFS(n, mx, my,
                        o -> Element.isHit(o.element, mx, my),
                        el -> el.mouseReleased(mx, my, btn),
                        o -> o.blocksMouse && o.element.isVisible());
            }
        }
        if (root != null) root.mouseReleased(mx, my, btn);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mouseCapture != null) {
            if (!mouseCapture.isVisible()) { releaseMouseCapture(); return false; }

            boolean hovered = Element.isHit(mouseCapture, mx, my);
            if (hovered && !captureHovered) {
                captureHovered = true;
                mouseCapture.mouseEntered(mx, my);
            } else if (!hovered && captureHovered) {
                captureHovered = false;
                mouseCapture.mouseExited();
            }

            capturePrevX = captureLastX; capturePrevY = captureLastY;
            captureLastX = mx; captureLastY = my;

            boolean consumed = mouseCapture.mouseDragged(mx, my, btn, captureLastX - capturePrevX, captureLastY - capturePrevY);
            if (consumed) return true;
        }

        if (suspendLevel > 0) return false;

        if (pressOwner != null && pressOwner != mouseCapture && pressButton == btn
                && (pressFlags & Element.CAPTURE_DRAG) != 0) {
            if (!pressOwner.isVisible()) { pressOwner = null; pressFlags = 0; }
            else {
                pressOwner.mouseDragged(mx, my, btn, dx, dy);
                return true;
            }
        }

        // 外部交互关闭前移：分发前定去留（见 mouseClicked）。
        dismissOverlaysOutside(mx, my);

        // 浮层树输入分发（最上层优先 DFS，通知式：不截断后续分发）。
        List<OverlayTree.OverlayNode> rootList = overlays.tree().roots();
        if (!rootList.isEmpty()) {
            for (int i = rootList.size() - 1; i >= 0; i--) {
                OverlayTree.OverlayNode n = rootList.get(i);
                dispatchOverlayDFS(n, mx, my,
                        o -> Element.isHit(o.element, mx, my),
                        el -> el.mouseDragged(mx, my, btn, dx, dy),
                        o -> o.blocksMouse && o.element.isVisible());
            }
        }
        return root != null && root.mouseDragged(mx, my, btn, dx, dy);
    }

    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (mouseCapture != null) {
            if (mouseCapture.mouseScrolled(mx, my, ha, va)) return true;
        }

        if (suspendLevel > 0) return false;

        // 外部交互关闭前移：分发前定去留（见 mouseClicked）。
        dismissOverlaysOutside(mx, my);

        // 浮层树输入分发（最上层优先 DFS，通知式：不截断后续分发）。
        List<OverlayTree.OverlayNode> rootList = overlays.tree().roots();
        if (!rootList.isEmpty()) {
            for (int i = rootList.size() - 1; i >= 0; i--) {
                OverlayTree.OverlayNode n = rootList.get(i);
                dispatchOverlayDFS(n, mx, my,
                        o -> Element.isHit(o.element, mx, my),
                        el -> el.mouseScrolled(mx, my, ha, va),
                        o -> o.blocksMouse && o.element.isVisible());
            }
        }
        return root != null && root.mouseScrolled(mx, my, ha, va);
    }

    /** 每帧推进：根元素树与浮层树全部节点（不看可见性）。 */
    public void tick() {
        if (root != null) root.tick();
        overlays.tree().tickAll();
    }

    /**
     * 鼠标是否被浮层阻断：可见的幕布（{@code blocksMouse}）节点命中光标即阻断根元素树。
     * 被动浮层（Tooltip／通知）不再阻断——过去"任一浮层命中即阻断"把幕布语义稀释了。
     * 阻断判定经 H5 {@code OverlayTree.anyNode} 组合子表达。
     */
    public boolean isMouseBlocked(double mx, double my) {
        if (suspendLevel > 0) return true;
        if (mouseCapture != null) return true;
        if (pressOwner != null && (pressFlags & Element.BLOCK_HOVER) != 0) return true;
        // 仅幕布命中阻断：可见＋幕布＋子树命中三者合取。
        return overlays.tree().anyNode(n -> n.element.isVisible() && n.blocksMouse
                && Element.isHit(n.element, mx, my));
    }

    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (suspendLevel > 0) return false;
        if (focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver kr)
            return kr.keyPressed(keyCode, scanCode, mods);
        return false;
    }

    public boolean charTyped(char chr) {
        if (suspendLevel > 0) return false;
        if (focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver kr)
            return kr.charTyped(chr);
        return false;
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (suspendLevel > 0) return false;
        if (focusedTarget != null && focusedTarget.isFocused()
                && focusedTarget instanceof KeyboardReceiver kr)
            return kr.preeditUpdated(event);
        return false;
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
            mouseCapture.mouseExited();
        }
        mouseCapture = null;
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
     * Release {@link #pressOwner} if it currently points at {@code owner}, and
     * clear {@link #pressFlags}. Used by overlay close paths so a stale press
     * reference doesn't bleed into the next click — e.g. after a SelectDropdown
     * option-select closes its panel, the pressOwner from the click that opened
     * the panel can otherwise leak across mouseReleased and confuse the next
     * interaction.
     */
    public void releasePressOwnerIfOwnerIs(Element<?> owner) {
        if (pressOwner == owner) {
            pressOwner = null;
            pressFlags = 0;
        }
    }
}
