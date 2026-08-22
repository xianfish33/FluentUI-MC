package xianfish.fluentui.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.FocusableElement;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import java.util.*;
import java.util.function.Consumer;

public class AutoCompleteBox extends FocusableElement<AutoCompleteBox> {
    private static final int PAD = 6, ITEM_H = 18, DD_OFF = 2, MAX_DD_H = 150, MAX_UNDO = 50;

    public record AutoColors(int bg, int border, int text, int focusBorder,
                              int placeholder, int cursor, int selection,
                              int ddBg, int ddBorder, int ddText, int ddHoverBg, int ddHighlight) {
        public static AutoColors blue() {
            return new AutoColors(0xFF222222, 0xFF555555, 0xFFFFFFFF, 0xFF3B82F6,
                0xFF666666, 0xFFFFFFFF, 0x403B82F6,
                0xFF1E1E1E, 0xFF333333, 0xFFFFFFFF, 0x303B82F6, 0x403B82F6);
        }
    }

    private AutoColors colors;
    private final StringBuilder text = new StringBuilder();
    private Component placeholder;
    private int cursorPos, selStart = -1, selEnd = -1;
    private float scrollX;
    private final Animator cursorBlink = new Animator();
    private Consumer<String> onChanged;
    private Runnable onEnter;
    private int maxLength = 100;
    private long lastTyped;
    private boolean dragging;
    private IMEPreeditOverlay preeditOverlay;
    private ContextMenu contextMenu;
    private final Deque<String> undoStack = new ArrayDeque<>();

    private List<String> dataset = new ArrayList<>();
    private final List<String> filtered = new ArrayList<>();
    private boolean open;
    private int hoveredIdx = -1;
    private Consumer<String> onSelected;
    private DropdownOverlay dropdownOverlay;

    public AutoCompleteBox(int w, int h, AutoColors c) { super(w, h); this.colors = c; cursorBlink.set(1); }
    public AutoCompleteBox(int w, int h) { this(w, h, AutoColors.blue()); }

    public AutoCompleteBox dataset(List<String> v) { dataset = v; return this; }
    public List<String> dataset() { return dataset; }
    public AutoCompleteBox placeholder(String s) { placeholder = s == null ? null : Component.literal(s); return this; }
    public AutoCompleteBox placeholder(Component c) { placeholder = c; return this; }
    public AutoCompleteBox onChanged(Consumer<String> c) { onChanged = c; return this; }
    public AutoCompleteBox onEnter(Runnable r) { onEnter = r; return this; }
    public AutoCompleteBox onSelected(Consumer<String> c) { onSelected = c; return this; }
    public AutoCompleteBox maxLength(int len) { maxLength = len; return this; }
    public String text() { return text.toString(); }
    public AutoCompleteBox text(String s) { text.setLength(0); text.append(s); cursorPos = s.length(); clearSel(); closeDropdown(); return this; }
    public boolean isOpen() { return open; }
    public List<String> filteredItems() { return filtered; }
    public int dropdownX() { return getAbsoluteX(); }
    public int dropdownY() { return getAbsoluteY() + height + DD_OFF; }
    public int dropdownW() { return width; }
    int hoveredIndex() { return hoveredIdx; }

    private void clearSel() { selStart = -1; selEnd = -1; }
    private boolean hasSel() { return selStart >= 0 && selEnd >= 0 && selStart != selEnd; }
    private int selMin() { return hasSel() ? Math.min(selStart, selEnd) : cursorPos; }
    private int selMax() { return hasSel() ? Math.max(selStart, selEnd) : cursorPos; }
    private void deleteSel() {
        if (!hasSel()) return;
        int smin = selMin(), smax = selMax();
        text.delete(smin, smax);
        cursorPos = smin;
        clearSel();
    }
    private String selectedText() { return hasSel() ? text.substring(selMin(), selMax()) : ""; }
    private void pushUndo() { undoStack.push(text.toString()); while (undoStack.size() > MAX_UNDO) undoStack.removeLast(); }
    private void undo() { if (undoStack.isEmpty()) return; text.setLength(0); text.append(undoStack.pop()); cursorPos = text.length(); clearSel(); nc(); }

    @Override protected void focusLost() {
        clearSel();
        closeMenu();
        closeDropdown();
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(false);
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (event == null || event.fullText().isEmpty()) preeditOverlay = null;
        else preeditOverlay = new IMEPreeditOverlay(event, font, font.lineHeight + 1);
        return true;
    }

    @Override protected void focusGained() {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(true);
        preeditOverlay = null;
    }

    private void closeMenu() { if (contextMenu != null) contextMenu.hide(); }

    private void showContextMenu(int mx, int my) {
        setFocused(true);
        if (contextMenu == null) { contextMenu = new ContextMenu().font(font); contextMenu.setParent(null); }
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.paste"), () -> {
            String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); filter(); }
        }, getClipboard() != null && !getClipboard().isEmpty()));
        if (hasSel()) {
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.copy"), () -> { String s = selectedText(); if (!s.isEmpty()) clipboard(s); }, true));
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.cut"), () -> { String s = selectedText(); if (!s.isEmpty()) { pushUndo(); clipboard(s); deleteSel(); nc(); filter(); } }, true));
        }
        contextMenu.items(items);
        contextMenu.show(mx, my);
    }

    private void closeDropdown() {
        open = false; hoveredIdx = -1;
        if (dropdownOverlay != null) {
            if (InteractionManager.active != null && InteractionManager.active.overlay() == dropdownOverlay) InteractionManager.active.overlay(null);
            dropdownOverlay = null;
        }
    }

    private void showDropdown() {
        if (filtered.isEmpty()) return;
        open = true; hoveredIdx = -1;
        if (dropdownOverlay == null) {
            dropdownOverlay = new DropdownOverlay(this);
            if (InteractionManager.active != null) InteractionManager.active.overlay(dropdownOverlay);
        } else {
            dropdownOverlay.refresh();
        }
    }

    private void filter() {
        filtered.clear();
        String query = text.toString().toLowerCase();
        if (query.isEmpty()) { closeDropdown(); return; }
        for (String item : dataset) {
            if (item.toLowerCase().contains(query)) filtered.add(item);
            if (filtered.size() >= 20) break;
        }
        if (!filtered.isEmpty()) showDropdown(); else closeDropdown();
    }

    void selectFiltered(int idx) {
        if (idx < 0 || idx >= filtered.size()) return;
        pushUndo();
        text.setLength(0); text.append(filtered.get(idx));
        cursorPos = text.length(); clearSel();
        closeDropdown();
        nc();
        if (onSelected != null) onSelected.accept(text.toString());
    }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean hovered = isHovered(mx, my);
        e.fill(ax, ay, ax + width, ay + height, colors.bg);
        e.outline(ax, ay, width, height,
            focused ? colors.focusBorder : hovered ? 0xFF777777 : colors.border);

        boolean clip = font.width(text.toString()) + PAD * 2 > width;
        if (clip) e.enableScissor(ax + PAD, ay, ax + width - PAD, ay + height);
        int tx = ax + PAD - (int) scrollX, ty = ay + (height - font.lineHeight) / 2;
        String disp = text.toString();
        if (disp.isEmpty() && !focused && placeholder != null) {
            e.text(font, placeholder, tx, ty, colors.placeholder);
        } else {            if (focused && hasSel()) {
                int smin = selMin(), smax = selMax();
                int sx = tx + font.width(disp.substring(0, smin));
                int sw = font.width(disp.substring(smin, smax));
                e.fill(sx, ty, sx + sw, ty + font.lineHeight, colors.selection);
            }
            e.text(font, disp, tx, ty, colors.text);
            if (focused && !hasSel() && cursorPos <= disp.length() && cursorBlink.get() > 0.5f && preeditOverlay == null) {
                int cx = tx + font.width(disp.substring(0, cursorPos));
                e.fill(cx, ty, cx + 1, ty + font.lineHeight, colors.cursor);
            }
        }
        if (clip) e.disableScissor();

        if (focused) {
            long now = System.currentTimeMillis();
            if (!cursorBlink.isRunning() && now - lastTyped > 450)
                cursorBlink.to(cursorBlink.get() > 0.5f ? 0 : 1, 450, Easing.LINEAR);
            if (preeditOverlay != null) {
                int cx = tx + font.width(disp.substring(0, cursorPos));
                preeditOverlay.updateInputPosition(cx, ty);
                e.setPreeditOverlay(preeditOverlay);
            }
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        if (btn == 1) { showContextMenu((int) mx, (int) my); return true; }
        if (btn != 0) return false;
        int ax = getAbsoluteX();
        setFocused(isHovered(mx, my));
        if (focused && font != null) {
            clearSel();
            cursorPos = findCursor((int) mx - ax - PAD + (int) scrollX);
            selStart = cursorPos;
            dragging = true;
            lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        }
        return focused;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!focused || !dragging || btn != 0) return false;
        int ax = getAbsoluteX();
        int cp = findCursor((int) mx - ax - PAD + (int) scrollX);
        if (cp != cursorPos) { selEnd = cp; lastTyped = System.currentTimeMillis(); cursorBlink.set(1); }
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (dragging && focused && hasSel()) cursorPos = selMax();
        dragging = false;
        return false;
    }

    @Override public boolean isHovered(double mx, double my) { return super.isHovered(mx, my); }

    private int findCursor(int relX) {
        String s = text.toString();
        for (int i = 0; i <= s.length(); i++)
            if (font.width(s.substring(0, i)) >= relX) return i;
        return s.length();
    }

    public boolean charTyped(char chr) {
        if (!focused || text.length() >= maxLength) return false;
        pushUndo(); deleteSel();
        text.insert(cursorPos++, chr);
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        nc(); filter();
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!focused) return false;
        boolean ctrl = (mods & 2) != 0;
        int BS = 259, DEL = 261, L = 263, R = 262, ENT = 257, TAB = 258, ESC = 256;
        int UP = 265, DOWN = 264, HOME = 268, END = 269;
        int A = 65, C = 67, V = 86, X = 88, Z = 90;
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);

        if (ctrl && keyCode == Z) { undo(); return true; }
        if (ctrl && keyCode == A) { selStart = 0; selEnd = text.length(); return true; }
        if (ctrl && keyCode == C) { String s = selectedText(); if (!s.isEmpty()) clipboard(s); return true; }
        if (ctrl && keyCode == X) { String s = selectedText(); if (!s.isEmpty()) { pushUndo(); clipboard(s); deleteSel(); nc(); filter(); } return true; }
        if (ctrl && keyCode == V) { String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); filter(); } return true; }

        if (open && !filtered.isEmpty()) {
            if (keyCode == TAB || keyCode == DOWN) { hoveredIdx = (hoveredIdx + 1) % filtered.size(); pageTo(hoveredIdx); return true; }
            if (keyCode == UP) { hoveredIdx = (hoveredIdx - 1 + filtered.size()) % filtered.size(); pageTo(hoveredIdx); return true; }
            if (keyCode == ENT) { selectFiltered(Math.max(0, hoveredIdx)); return true; }
            if (keyCode == ESC) { closeDropdown(); return true; }
        }
        if (keyCode == ESC) { closeDropdown(); return false; }

        if (keyCode == BS) { if (hasSel()) { pushUndo(); deleteSel(); nc(); filter(); return true; } if (cursorPos > 0) { pushUndo(); text.deleteCharAt(--cursorPos); nc(); filter(); return true; } }
        if (keyCode == DEL) { if (hasSel()) { pushUndo(); deleteSel(); nc(); filter(); return true; } if (cursorPos < text.length()) { pushUndo(); text.deleteCharAt(cursorPos); nc(); filter(); return true; } }
        if (keyCode == L) { if (hasSel()) { cursorPos = selMin(); clearSel(); } else if (cursorPos > 0) cursorPos--; return true; }
        if (keyCode == R) { if (hasSel()) { cursorPos = selMax(); clearSel(); } else if (cursorPos < text.length()) cursorPos++; return true; }
        if (keyCode == HOME) { cursorPos = 0; clearSel(); return true; }
        if (keyCode == END) { cursorPos = text.length(); clearSel(); return true; }
        if (keyCode == ENT) { if (onEnter != null) onEnter.run(); return true; }
        return false;
    }

    private void pageTo(int idx) {
        if (dropdownOverlay != null) dropdownOverlay.pageTo(idx);
    }

    private static void clipboard(String s) { Minecraft.getInstance().keyboardHandler.setClipboard(s); }
    private static String getClipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    private void nc() { if (onChanged != null) onChanged.accept(text.toString()); }

    private static class DropdownOverlay extends Element<DropdownOverlay> {
        private final AutoCompleteBox box;
        private final Animator heightAnim = new Animator();
        private final Animator scrollAnim = new Animator();

        DropdownOverlay(AutoCompleteBox box) {
            super(box.dropdownX(), box.dropdownY(), box.dropdownW(), 0);
            this.box = box;
            visible = true;
            heightAnim.set(0); scrollAnim.set(0);
            zIndex(Integer.MAX_VALUE);
            font = box.font;
            refresh();
        }

        void refresh() {
            int targetH = Math.min(box.filteredItems().size() * ITEM_H, MAX_DD_H);
            if (heightAnim.get() > 0.01f)
                heightAnim.animate(heightAnim.get(), targetH, 200, Easing.EASE_OUT_CUBIC);
            else
                heightAnim.to(targetH, 200, Easing.EASE_OUT_CUBIC);
        }

        void pageTo(int idx) {
            List<String> items = box.filteredItems();
            int itemsCount = items.size();
            if (itemsCount <= 0) return;
            int curH = Math.max(1, (int) heightAnim.get());
            int visibleCount = Math.max(1, curH / ITEM_H);
            int targetIdx = Math.max(0, Math.min(idx, itemsCount - visibleCount));
            scrollAnim.animate(scrollAnim.get(), targetIdx * ITEM_H, 150, Easing.EASE_OUT_CUBIC);
        }

        @Override protected void measure() {}

        @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
            List<String> items = box.filteredItems();
            if (items.isEmpty() || !box.isOpen()) return;

            int curH = Math.max(1, (int) heightAnim.get());
            height = curH;
            if (curH < 1) return;

            int ax = box.dropdownX();
            int baseY = box.dropdownY();
            x = ax;
            y = baseY;

            int fullH = Math.min(items.size() * ITEM_H, MAX_DD_H);
            int drawH = Math.min(curH, fullH);
            int sc = (int) scrollAnim.get();

            e.nextStratum();
            e.enableScissor(ax, baseY, ax + width, baseY + drawH);
            e.fill(ax, baseY, ax + width, baseY + fullH, box.colors.ddBg);
            e.outline(ax, baseY, width, fullH, box.colors.ddBorder);

            for (int i = 0; i < items.size(); i++) {
                int oy = baseY + i * ITEM_H - sc;
                int ob = oy + ITEM_H;
                if (ob < baseY || oy > baseY + drawH) continue;
                boolean h = i == box.hoveredIdx;
                int bg = h ? box.colors.ddHoverBg : 0x00000000;
                if (bg != 0) e.fill(ax + 1, oy, ax + width - 1, ob, bg);
                if (h) e.fill(ax + 1, oy, ax + 4, ob, box.colors.ddHighlight);
                e.text(font, items.get(i), ax + PAD + 4, oy + (ITEM_H - font.lineHeight) / 2, box.colors.ddText);
            }

            if (mx >= ax && mx <= ax + width && my >= baseY && my <= baseY + drawH) {
                int ri = (my - baseY + sc) / ITEM_H;
                if (ri >= 0 && ri < items.size()) box.hoveredIdx = ri;
            }
            e.disableScissor();
        }

        @Override public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0) return false;
            if (box.hoveredIdx >= 0 && box.hoveredIdx < box.filteredItems().size()) {
                box.selectFiltered(box.hoveredIdx); return true;
            }
            if (isHovered(mx, my)) return true;
            return false;
        }

        @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
            List<String> items = box.filteredItems();
            if (items.isEmpty()) return false;
            int curH = Math.max(1, (int) heightAnim.get());
            float maxScroll = Math.max(0, items.size() * ITEM_H - curH);
            float target = Math.max(0, Math.min(maxScroll, scrollAnim.get() - (float) va * 20));
            scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
            return true;
        }

        @Override public boolean isHovered(double mx, double my) {
            if (heightAnim.get() < 0.01f) return false;
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            int curH = Math.max(1, (int) heightAnim.get());
            return mx >= ax && mx <= ax + width && my >= ay && my <= ay + curH;
        }
    }
}
