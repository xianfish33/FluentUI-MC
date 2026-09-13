package xianfish.fluentui.client.ui.element;

import net.minecraft.client.input.PreeditEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 可聚焦元素基类：位置无关事件（键盘输入、IME 预编辑）的分发目标。
 *
 * <p>聚焦的取得与失去统一由点击链的焦点转移算法决定（读 L3 全 UI 命中＋
 * 家族归属），元素不得自查 {@code isHovered} 自领／自清焦点。
 * 家族（{@code focusFamily}）登记“点中也不算失焦”的自己人——典型如输入框
 * 弹出的右键菜单、候选下拉浮层：点击它们时命中落在家族内，焦点保持。
 */
public abstract class FocusableElement<T extends FocusableElement<T>> extends TooltippingElement<T>
        implements InteractionManager.Focusable, InteractionManager.KeyboardReceiver {

    protected boolean focused;
    /** 家族成员：点中也不失焦的自己人（同一性比较）。 */
    private final Set<Element<?>> focusFamily = Collections.newSetFromMap(new IdentityHashMap<>());

    public FocusableElement(int x, int y, int w, int h) { super(x, y, w, h); }
    public FocusableElement(int w, int h) { super(w, h); }

    @Override public boolean isFocused() { return focused; }

    /**
     * 命中是否算落在境内：命中本元素或任一家族成员即境内；
     * 命中为空（点空地）恒为境外。同一性比较。
     */
    public boolean isInFocusFamily(Element<?> hit) {
        return hit == this || (hit != null && focusFamily.contains(hit));
    }

    /** 登记家族成员（浮层弹出时调用）。 */
    public void addFocusFamily(Element<?> el) { if (el != null) focusFamily.add(el); }
    /** 摘除家族成员（浮层关闭时对称调用，避免泄漏已关闭的浮层引用）。 */
    public void removeFocusFamily(Element<?> el) { focusFamily.remove(el); }

    /**
     * 点击链命中时领焦，供 {@code InteractionManager} 焦点转移调用。
     * 元素自身的点击处理器不得调它自领——分发开始前转移算法已经定好焦点。
     */
    public void claimFocus() { setFocused(true); }

    @Override public void defocus() {
        if (!focused) return;
        focused = false;
        InteractionManager m = manager();
        if (m != null) m.clearFocusTarget(this);
        focusLost();
    }

    protected boolean setFocused(boolean v) {
        InteractionManager m = manager();
        if (v == focused) {
            // 已是该状态仍重申目标归属，保持 focused==true ⟺ 目标是我的不变式。
            if (v && m != null) m.setFocusTarget(this);
            return false;
        }
        focused = v;
        if (v) {
            if (m != null) m.setFocusTarget(this);
            focusGained();
        } else {
            if (m != null) m.clearFocusTarget(this);
            focusLost();
        }
        return true;
    }

    protected void focusGained() {}
    protected void focusLost() {}

    @Override public boolean keyPressed(int keyCode, int scanCode, int mods) { return false; }
    @Override public boolean charTyped(char chr) { return false; }
    @Override public boolean preeditUpdated(PreeditEvent event) { return false; }

    public InteractionManager manager() { return InteractionManager.active; }
}
