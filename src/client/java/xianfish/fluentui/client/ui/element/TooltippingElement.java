package xianfish.fluentui.client.ui.element;

import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.component.Tooltip;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import java.util.function.Supplier;

public abstract class TooltippingElement<T extends TooltippingElement<T>> extends Element<T> {
    protected Supplier<Component> tooltip;

    public TooltippingElement(int x, int y, int w, int h) { super(x, y, w, h); }
    public TooltippingElement(int w, int h) { super(w, h); }

    public Supplier<Component> tooltipCallback() { return tooltip; }
    @SuppressWarnings("unchecked")
    public T tooltip(String s) { tooltip = s == null ? null : () -> Component.literal(s); return (T) this; }
    @SuppressWarnings("unchecked")
    public T tooltip(Component c) { tooltip = c == null ? null : () -> c; return (T) this; }
    @SuppressWarnings("unchecked")
    public T tooltip(Supplier<Component> c) { tooltip = c; return (T) this; }

    @Override public void mouseEntered(HoverEvent e) {
        if (tooltip == null || isTooltipSuppressed()) {
            return;
        }
        Tooltip.track(this, font);
    }

    @Override public void mouseMoved(HoverEvent e) {
        if (tooltip == null || isTooltipSuppressed()) return;
        Tooltip.track(this, font);
    }

    @Override public void mouseExited(HoverEvent e) {
        if (Tooltip.owner() == this) {
            Tooltip.hide();
        }
    }

    public void dismissTooltip() {
        if (Tooltip.owner() == this) Tooltip.hide();
    }

    /**
     * Tooltip 压制钩子：返回 {@code true} 时悬停不唤起 tooltip。
     * 输入框类覆写它——右键菜单／候选下拉打开时 tooltip 会盖住菜单。
     */
    protected boolean isTooltipSuppressed() { return false; }
}
