package xianfish.fluentui.client.ui.element;

import net.minecraft.client.input.PreeditEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public abstract class FocusableElement<T extends FocusableElement<T>> extends TooltippingElement<T>
        implements InteractionManager.Focusable, InteractionManager.KeyboardReceiver {

    protected boolean focused;
    private boolean registered;

    public FocusableElement(int x, int y, int w, int h) { super(x, y, w, h); }
    public FocusableElement(int w, int h) { super(w, h); }

    @Override public boolean isFocused() { return focused; }

    @Override public void defocus() {
        if (!focused) return;
        focused = false;
        focusLost();
    }

    protected boolean setFocused(boolean v) {
        if (v) ensureRegistered();
        if (v == focused) return false;
        focused = v;
        if (v) focusGained(); else focusLost();
        return true;
    }

    protected boolean handleFocusClick(double mx, double my) {
        ensureRegistered();
        return setFocused(isHovered(mx, my));
    }

    protected boolean handleFocusClick(double mx, double my, int btn) {
        if (btn != 0) return false;
        return handleFocusClick(mx, my);
    }

    private void ensureRegistered() {
        if (registered) return;
        InteractionManager m = manager();
        if (m != null) { m.registerFocusable(this); registered = true; }
    }

    protected void focusGained() {}
    protected void focusLost() {}

    @Override public boolean keyPressed(int keyCode, int scanCode, int mods) { return false; }
    @Override public boolean charTyped(char chr) { return false; }
    @Override public boolean preeditUpdated(PreeditEvent event) { return false; }

    public InteractionManager manager() { return InteractionManager.active; }
}
