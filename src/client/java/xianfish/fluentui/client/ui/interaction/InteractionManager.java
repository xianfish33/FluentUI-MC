package xianfish.fluentui.client.ui.interaction;

import net.minecraft.client.input.PreeditEvent;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.Layout;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.layout.Layer;

// import com.mojang.logging.LogUtils;
// import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InteractionManager {

    // private static final Logger LOG = LogUtils.getLogger();

    public interface Focusable {
        boolean isFocused();
        boolean isHovered(double mx, double my);
        void defocus();
    }

    public interface KeyboardReceiver {
        boolean keyPressed(int keyCode, int scanCode, int mods);
        boolean charTyped(char chr);
        default boolean preeditUpdated(PreeditEvent event) { return false; }
    }

    private final Layout<?> root;
    public Layout<?> root() { return root; }
    private Layer layer;
    private Element<?> overlay;
    private final List<Focusable> focusables = new ArrayList<>();

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

    public Element<?> hoverOwner() { return hoverOwner; }

    public void updateHover(double mx, double my) {
        Element<?> next = null;
        if (suspendLevel <= 0 && root != null) {
            next = topmostHit(root, mx, my);
        }
        if (next != hoverOwner) {
            // LOG.info("updateHover: {} -> {} @ ({}, {})",
            //         hoverOwner != null ? hoverOwner.getClass().getSimpleName() : "null",
            //         next != null ? next.getClass().getSimpleName() : "null", mx, my);
            if (hoverOwner != null) hoverOwner.mouseExited();
            hoverOwner = next;
            if (hoverOwner != null) hoverOwner.mouseEntered(mx, my);
        } else if (hoverOwner != null && (mx != lastHoverX || my != lastHoverY)) {
            hoverOwner.mouseMoved(mx, my);
        }
        lastHoverX = mx;
        lastHoverY = my;
    }

    public static boolean isHit(Element<?> el, double mx, double my) {
        return el.isHovered(mx, my) && el.isMouseWithinClipBounds(mx, my);
    }

    public static boolean isClipHit(Element<?> el, double mx, double my) {
        return el.isMouseWithinClipBounds(mx, my);
    }

    public static List<Element<?>> hitOrder(Layout<?> layout, double mx, double my) {
        List<Element<?>> out = new ArrayList<>();
        if (layout == null || !layout.isVisible()) return out;
        List<Element<?>> sorted = new ArrayList<>(layout.children());
        sorted.sort(Comparator.comparingInt(a -> a.getZIndex()));
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Element<?> c = sorted.get(i);
            if (!c.isVisible() || c.isPaused()) continue;
            if (isHit(c, mx, my)) out.add(c);
        }
        return out;
    }

    public static Element<?> topmostHit(Layout<?> layout, double mx, double my) {
        if (layout == null || !layout.isVisible()) return null;
        List<Element<?>> sorted = new ArrayList<>(layout.children());
        sorted.sort(Comparator.comparingInt(a -> a.getZIndex()));
        for (int i = sorted.size() - 1; i >= 0; i--) {
            Element<?> c = sorted.get(i);
            if (!c.isVisible() || c.isPaused()) continue;
            if (isHit(c, mx, my)) {
                if (c instanceof Layout<?> l) {
                    Element<?> inner = topmostHit(l, mx, my);
                    if (inner != null) return inner;
                }
                return c;
            }
        }
        return null;
    }

    public InteractionManager(Layout<?> root) {
        this.root = root;
        active = this;
        autoRegister(root);
        if (layer != null) autoRegister(layer);
    }

    private void autoRegister(Element<?> el) {
        if (el instanceof Focusable f) registerFocusable(f);
        if (el instanceof Layout<?> layout) {
            for (Element<?> c : layout.children()) autoRegister(c);
        }
    }

    public InteractionManager layer(Layer v) { layer = v; if (v != null) autoRegister(v); return this; }
    public Layer layer() { return layer; }

    public InteractionManager overlay(Element<?> v) { overlay = v; return this; }
    public Element<?> overlay() { return overlay; }

    public InteractionManager registerFocusable(Focusable f) { focusables.add(f); return this; }

    public boolean hasFocusedTextInput() {
        for (Focusable f : focusables) {
            if (f.isFocused() && f instanceof KeyboardReceiver) return true;
        }
        return false;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        if (pressOwner != null && (pressFlags & Element.BLOCK_RIGHT) != 0 && btn == 1) return true;

        pendingCapture = null;
        pressOwner = null;

        if (mouseCapture != null) {
            if (mouseCapture.mouseClicked(mx, my, btn)) return true;
        }

        if (suspendLevel > 0) return false;

        Element<?> clicked = root != null ? topmostHit(root, mx, my) : null;
        while (clicked != null) {
            if (clicked instanceof TooltippingElement<?> t) { t.dismissTooltip(); break; }
            clicked = clicked.getParent();
        }

        if (overlay != null && overlay.isVisible()) {
            if (overlay.mouseClicked(mx, my, btn)) return true;
        }
        if (layer != null && layer.isVisible()) {
            if (layer.mouseClicked(mx, my, btn)) return true;
            if (layer.isBlockingMouse()) return true;
        }

        for (Focusable f : focusables) {
            if (f.isFocused() && !f.isHovered(mx, my)) f.defocus();
        }

        boolean consumed = root != null && root.mouseClicked(mx, my, btn);
        if (consumed && root != null && root.pressOwner() != null) {
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

        if (overlay != null && overlay.isVisible()) {
            if (overlay.isHovered(mx, my) && overlay.mouseReleased(mx, my, btn)) return true;
        }
        if (layer != null && layer.isVisible()) {
            layer.mouseReleased(mx, my, btn);
            if (layer.isBlockingMouse()) return true;
        }
        if (root != null) root.mouseReleased(mx, my, btn);
        return false;
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mouseCapture != null) {
            if (!mouseCapture.isVisible()) { releaseMouseCapture(); return false; }

            boolean hovered = mouseCapture.isHovered(mx, my);
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

        if (overlay != null && overlay.isVisible()) {
            if (overlay.isHovered(mx, my) && overlay.mouseDragged(mx, my, btn, dx, dy)) return true;
        }
        if (layer != null && layer.isVisible()) {
            layer.mouseDragged(mx, my, btn, dx, dy);
            if (layer.isBlockingMouse()) return true;
        }
        return root != null && root.mouseDragged(mx, my, btn, dx, dy);
    }

    public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (mouseCapture != null) {
            if (mouseCapture.mouseScrolled(mx, my, ha, va)) return true;
        }

        if (suspendLevel > 0) return false;

        if (overlay != null && overlay.isVisible()) {
            if (overlay.isHovered(mx, my) && overlay.mouseScrolled(mx, my, ha, va)) return true;
        }
        if (layer != null && layer.isVisible()) {
            if (layer.isBlockingMouse()) { layer.mouseScrolled(mx, my, ha, va); return true; }
            if (layer.mouseScrolled(mx, my, ha, va)) return true;
        }
        return root != null && root.mouseScrolled(mx, my, ha, va);
    }

    public void tick() {
        if (root != null) root.tick();
        if (layer != null && layer.isVisible()) layer.tick();
        if (overlay != null) overlay.tick();
    }

    public void renderOverlay(net.minecraft.client.gui.GuiGraphicsExtractor e, int mx, int my, float d) {
        if (overlay != null && overlay.isVisible())
            overlay.extractRenderState(e, mx, my, d);
    }

    public boolean isMouseBlocked(double mx, double my) {
        if (suspendLevel > 0) return true;
        if (mouseCapture != null) return true;
        if (pressOwner != null && (pressFlags & Element.BLOCK_HOVER) != 0) return true;
        if (overlay != null && overlay.isVisible() && overlay.isHovered(mx, my)) return true;
        if (layer != null && layer.isVisible() && layer.isBlockingMouse()) return true;
        return false;
    }

    public int filterMouseX(int mx, int my) { return isMouseBlocked(mx, my) ? -999 : mx; }
    public int filterMouseY(int mx, int my) { return isMouseBlocked(mx, my) ? -999 : my; }

    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (suspendLevel > 0) return false;
        for (Focusable f : focusables) {
            if (f.isFocused() && f instanceof KeyboardReceiver kr)
                if (kr.keyPressed(keyCode, scanCode, mods)) return true;
        }
        return false;
    }

    public boolean charTyped(char chr) {
        if (suspendLevel > 0) return false;
        for (Focusable f : focusables) {
            if (f.isFocused() && f instanceof KeyboardReceiver kr)
                if (kr.charTyped(chr)) return true;
        }
        return false;
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (suspendLevel > 0) return false;
        for (Focusable f : focusables) {
            if (f.isFocused() && f instanceof KeyboardReceiver kr)
                if (kr.preeditUpdated(event)) return true;
        }
        return false;
    }

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

    public boolean isMouseCaptured() { return mouseCapture != null; }
    public Element<?> mouseCaptureOwner() { return mouseCapture; }
    public double captureStartX() { return captureStartX; }
    public double captureStartY() { return captureStartY; }
    public double captureDeltaX() { return captureLastX - captureStartX; }
    public double captureDeltaY() { return captureLastY - captureStartY; }
    public double captureDX() { return captureLastX - capturePrevX; }
    public double captureDY() { return captureLastY - capturePrevY; }
    public int captureButton() { return captureButton; }

    public void suspend() { suspendLevel++; }
    public void resume() { if (suspendLevel > 0) suspendLevel--; }
    public boolean isSuspended() { return suspendLevel > 0; }
}
