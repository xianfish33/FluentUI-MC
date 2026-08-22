package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Colors;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SelectDropdown extends TooltippingElement<SelectDropdown> implements InteractionManager.Focusable {
    private static final int DD_OFF = 2, OPT_H = 20, PAD_X = 8, BTN_H = 22;

    public record DropdownColors(int btnBg, int btnBorder, int btnText,
                                  int ddBg, int ddBorder, int optText,
                                  int optHoverBg, int optSelBg, int optAccent) {
        public static DropdownColors blue() {
            return new DropdownColors(0x30000000, 0xFF3B82F6, 0xFFFFFFFF,
                0xFF1E1E1E, 0xFF333333, 0xFFFFFFFF, 0x303B82F6, 0x303B82F6, 0xFF3B82F6);
        }
    }

    protected DropdownColors colors;
    protected List<Component> options = new ArrayList<>();
    protected int selIdx = -1, maxDdH = 160;
    protected boolean open;
    protected Consumer<Integer> onChanged;
    protected DropPanel dropPanel;

    public SelectDropdown(int w, DropdownColors c) { super(w, BTN_H); this.colors = c; zIndex(1); }
    public SelectDropdown(int w) { this(w, DropdownColors.blue()); }

    public SelectDropdown options(List<String> v) { options.clear(); v.forEach(s -> options.add(Component.literal(s))); return this; }
    public SelectDropdown options(Component... v) { options.clear(); for (Component c : v) options.add(c); return this; }
    public SelectDropdown selected(int v) { selIdx = v; return this; }
    public SelectDropdown onChanged(Consumer<Integer> c) { onChanged = c; return this; }
    public SelectDropdown maxDropdownHeight(int v) { maxDdH = v; return this; }
    public SelectDropdown maxVisible(int count) { maxDdH = count * OPT_H; return this; }
    public int selectedIndex() { return selIdx; }
    public Component selectedText() { return selIdx >= 0 && selIdx < options.size() ? options.get(selIdx) : Component.empty(); }
    public boolean isOpen() { return open; }

    @Override
    public boolean isFocused() { return open; }

    @Override
    public boolean isHovered(double mx, double my) {
        return super.isHovered(mx, my);
    }

    @Override
    public void defocus() { if (open) toggle(); }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean hov = open || (mx >= ax && mx <= ax + width && my >= ay && my <= ay + BTN_H);
        int bg = hov ? Colors.lerp(colors.btnBg, 0x50000000, 0.6f) : colors.btnBg;
        e.fill(ax, ay, ax + width, ay + BTN_H, bg);
        e.outline(ax, ay, width, BTN_H, colors.btnBorder);
        Component sel = selectedText();
        Component label = sel.getString().isEmpty() ? Component.literal("\u25BC")
                : sel.copy().append(" \u25BC");
        e.text(font, label, ax + PAD_X, ay + (BTN_H - font.lineHeight) / 2, colors.btnText);
    }

    public void toggle() {
        open = !open;
        if (open) {
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            dropPanel = new DropPanel(ax, ay + BTN_H + DD_OFF, width);
            if (InteractionManager.active != null) InteractionManager.active.overlay(dropPanel);
        } else {
            closePanel();
        }
    }

    private void closePanel() {
        if (dropPanel != null && InteractionManager.active != null && InteractionManager.active.overlay() == dropPanel)
            InteractionManager.active.overlay(null);
        dropPanel = null;
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) return false;
        int ay = getAbsoluteY();
        if (my >= ay && my <= ay + BTN_H && mx >= getAbsoluteX() && mx <= getAbsoluteX() + width) {
            toggle(); return true;
        }
        return false;
    }

    private class DropPanel extends Element<DropPanel> {
        final Animator revealAnim = new Animator();
        final Animator scrollAnim = new Animator();
        int hoveredIdx = -1;

        DropPanel(int px, int py, int pw) {
            super(px, py, pw, 10);
            revealAnim.set(0); scrollAnim.set(0);
            zIndex(Integer.MAX_VALUE);
            visible = true;
            font = SelectDropdown.this.font;
            float fullH = Math.min(options.size() * OPT_H, maxDdH);
            height = (int) fullH;
            float target = Math.max(0, Math.min(selIdx * OPT_H, options.size() * OPT_H - Math.min(options.size() * OPT_H, maxDdH)));
            scrollAnim.set(target);
            revealAnim.animate(0, 1, 200, Easing.EASE_OUT_QUINT);
        }

        @Override protected void measure() {}

        @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
            if (!visible || font == null) return;
            float revealT = revealAnim.get();
            if (revealT < 0.01f) return;

            int px = getAbsoluteX(), py = getAbsoluteY();
            x = SelectDropdown.this.getAbsoluteX();
            y = SelectDropdown.this.getAbsoluteY() + BTN_H + DD_OFF;

            float contentH = options.size() * OPT_H, fullH = Math.min(contentH, maxDdH);
            float curH = fullH * revealT;
            if (curH < 1) return;

            e.nextStratum();
            e.enableScissor(px, py, px + width, py + (int) curH);
            e.fill(px, py, px + width, py + (int) fullH, colors.ddBg);
            e.outline(px, py, width, (int) fullH, colors.ddBorder);

            for (int i = 0; i < options.size(); i++) {
                int oy = py + i * OPT_H - (int) scrollAnim.get(), ob = oy + OPT_H;
                if (ob < py || oy > py + (int) curH) continue;
                boolean sel = i == selIdx, h = mx >= px && mx <= px + width && my >= oy && my <= ob;
                int optBg = sel ? colors.optSelBg : h ? colors.optHoverBg : 0x00000000;
                if (optBg != 0) e.fill(px + 1, oy, px + width - 1, ob, optBg);
                if (sel) e.fill(px + 1, oy, px + 4, ob, colors.optAccent);
                e.text(font, options.get(i), px + PAD_X + (sel ? 4 : 0), oy + (OPT_H - font.lineHeight) / 2, colors.optText);            }

            hoveredIdx = -1;
            if (my >= py && my <= py + curH) { int ri = (int) ((my - py + scrollAnim.get()) / OPT_H); if (ri >= 0 && ri < options.size()) hoveredIdx = ri; }
            e.disableScissor();
        }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0) return false;
            if (hoveredIdx >= 0) {
                selIdx = hoveredIdx; if (onChanged != null) onChanged.accept(selIdx);
                closePanel(); open = false; return true;
            }
            if (isHovered(mx, my)) { closePanel(); open = false; return true; }
            return false;
        }

        @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
            if (!visible) return false;
            float ch = options.size() * OPT_H, fh = Math.min(ch, maxDdH), ms = Math.max(0, ch - fh);
            float target = Math.max(0, Math.min(ms, scrollAnim.get() - (float) va * 20));
            scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
            return true;
        }

        @Override public boolean isHovered(double mx, double my) {
            if (revealAnim.get() < 0.01f) return false;
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            int curH = (int) (Math.min(options.size() * OPT_H, maxDdH) * revealAnim.get());
            return mx >= ax && mx <= ax + width && my >= ay && my <= ay + curH;
        }
    }
}
