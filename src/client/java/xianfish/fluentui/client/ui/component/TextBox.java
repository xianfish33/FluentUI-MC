package xianfish.fluentui.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.FocusableElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

public class TextBox extends FocusableElement<TextBox> {
    private static final int PAD = 6, MAX_UNDO = 50;

    public record TextBoxColors(int bg, int border, int text, int focusBorder,
                                int placeholder, int cursor, int selection) {
        public static TextBoxColors blue() {
            return new TextBoxColors(0xFF222222, 0xFF555555, 0xFFFFFFFF,
                0xFF3B82F6, 0xFF666666, 0xFFFFFFFF, 0x403B82F6);
        }
    }

    protected TextBoxColors colors;
    protected StringBuilder text = new StringBuilder();
    protected Component placeholder;
    protected int cursorPos, selStart = -1, selEnd = -1;
    protected float scrollX;
    protected final Animator cursorBlink = new Animator();
    protected Consumer<String> onChanged;
    protected Runnable onEnter;
    protected int maxLength = 100;
    private long lastTyped;
    private boolean dragging;
    private IMEPreeditOverlay preeditOverlay;
    private ContextMenu contextMenu;
    private final Deque<String> undoStack = new ArrayDeque<>();

    public TextBox(int w, int h, TextBoxColors c) { super(w, h); this.colors = c; cursorBlink.set(1); }
    public TextBox(int w, int h) { this(w, h, TextBoxColors.blue()); }

    public TextBox text(String s) { text.setLength(0); text.append(s); cursorPos = s.length(); clearSel(); return this; }
    public String text() { return text.toString(); }
    public TextBox placeholder(String s) { placeholder = s == null ? null : Component.literal(s); return this; }
    public TextBox placeholder(Component c) { placeholder = c; return this; }
    public TextBox onChanged(Consumer<String> c) { onChanged = c; return this; }
    public TextBox onEnter(Runnable r) { onEnter = r; return this; }
    public TextBox maxLength(int len) { maxLength = len; return this; }

    public TextBox focus() { setFocused(true); return this; }

    @Override protected void focusLost() {
        clearSel();
        closeMenu();
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(false);
    }

    public boolean preeditUpdated(PreeditEvent event) {
        if (event == null || event.fullText().isEmpty()) {
            preeditOverlay = null;
        } else {
            preeditOverlay = new IMEPreeditOverlay(event, font, font.lineHeight + 1);
        }
        return true;
    }

    @Override protected void focusGained() {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(true);
        preeditOverlay = null;
    }

    private void clearSel() { selStart = -1; selEnd = -1; }
    private boolean hasSel() { return selStart >= 0 && selEnd >= 0 && selStart != selEnd; }
    private int selMin() { return hasSel() ? Math.min(selStart, selEnd) : cursorPos; }
    private int selMax() { return hasSel() ? Math.max(selStart, selEnd) : cursorPos; }

    private void pushUndo() {
        undoStack.push(text.toString());
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        text.setLength(0);
        text.append(undoStack.pop());
        cursorPos = text.length();
        clearSel();
        nc();
    }

    private void closeMenu() {
        if (contextMenu != null) contextMenu.hide();
    }

    /**
     * 右键菜单打开期间压制 tooltip：菜单与 tooltip 同为浮层，
     * 不压制则后唤起的 tooltip 会盖在菜单上。
     */
    @Override protected boolean isTooltipSuppressed() {
        return contextMenu != null && contextMenu.isVisible();
    }

    private void showContextMenu(int mx, int my) {
        if (contextMenu == null) {
            contextMenu = new ContextMenu().font(font);
            contextMenu.setParent(null);
            // 右键菜单是焦点家族成员：点击它不算外部，不失焦（命中即境内）。
            // 菜单对象缓存复用，注册一次即可；隐藏的成员不可能被命中，无需摘除。
            addFocusFamily(contextMenu);
        }
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.paste"), () -> {
            String cb = getClipboard();
            if (cb != null && !cb.isEmpty()) { deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); }
        }, getClipboard() != null && !getClipboard().isEmpty()));
        if (hasSel()) {
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.copy"), () -> {
                String sel = selectedText();
                if (!sel.isEmpty()) clipboard(sel);
            }, true));
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.cut"), () -> {
                String sel = selectedText();
                if (!sel.isEmpty()) { clipboard(sel); deleteSel(); nc(); }
            }, true));
        }
        contextMenu.items(items);
        dismissTooltip();
        contextMenu.show(mx, my);
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
        // 焦点由点击链转移算法在分发前定好：命中本框已领焦，命中外部已失焦。
        // 这里只读 focused，不再自查 isHovered 自领。
        if (focused && font != null) {
            clearSel();
            cursorPos = findCursor((int) mx - getAbsoluteX() - PAD + (int) scrollX);
            selStart = cursorPos;
            dragging = true;
            lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        }
        return focused;
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (!focused || !dragging || btn != 0) return false;
        int cp = findCursor((int) mx - getAbsoluteX() - PAD + (int) scrollX);
        if (cp != cursorPos) { selEnd = cp; lastTyped = System.currentTimeMillis(); cursorBlink.set(1); }
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (dragging && focused && hasSel()) cursorPos = selMax();
        dragging = false;
        return false;
    }

    private int findCursor(int relX) {
        String s = text.toString();
        for (int i = 0; i <= s.length(); i++)
            if (font.width(s.substring(0, i)) >= relX) return i;
        return s.length();
    }

    public boolean charTyped(char chr) {
        if (!focused || text.length() >= maxLength) return false;
        pushUndo();
        deleteSel();
        text.insert(cursorPos++, chr);
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        nc();
        return true;
    }

    private void deleteSel() {
        if (!hasSel()) return;
        int smin = selMin(), smax = selMax();
        text.delete(smin, smax);
        cursorPos = smin;
        clearSel();
    }

    private String selectedText() {
        if (!hasSel()) return "";
        return text.substring(selMin(), selMax());
    }

    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        if (!focused) return false;
        boolean ctrl = (mods & 2) != 0;
        int BS = 259, DEL = 261, L = 263, R = 262, ENT = 257, HOME = 268, END = 269;
        int A = 65, C = 67, V = 86, X = 88, Z = 90;
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);

        if (ctrl && keyCode == Z) { undo(); return true; }
        if (ctrl && keyCode == A) { selStart = 0; selEnd = text.length(); return true; }
        if (ctrl && keyCode == C) { String sel = selectedText(); if (!sel.isEmpty()) clipboard(sel); return true; }
        if (ctrl && keyCode == X) { String sel = selectedText(); if (!sel.isEmpty()) { pushUndo(); clipboard(sel); deleteSel(); nc(); } return true; }
        if (ctrl && keyCode == V) { String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); } return true; }

        if (keyCode == BS) { if (hasSel()) { pushUndo(); deleteSel(); nc(); return true; } if (cursorPos > 0) { pushUndo(); text.deleteCharAt(--cursorPos); nc(); return true; } }
        if (keyCode == DEL) { if (hasSel()) { pushUndo(); deleteSel(); nc(); return true; } if (cursorPos < text.length()) { pushUndo(); text.deleteCharAt(cursorPos); nc(); return true; } }
        if (keyCode == L) { if (hasSel()) { cursorPos = selMin(); clearSel(); } else if (cursorPos > 0) cursorPos--; return true; }
        if (keyCode == R) { if (hasSel()) { cursorPos = selMax(); clearSel(); } else if (cursorPos < text.length()) cursorPos++; return true; }
        if (keyCode == HOME) { cursorPos = 0; clearSel(); return true; }
        if (keyCode == END) { cursorPos = text.length(); clearSel(); return true; }
        if (keyCode == ENT && onEnter != null) { onEnter.run(); return true; }
        return false;
    }

    private static void clipboard(String s) { Minecraft.getInstance().keyboardHandler.setClipboard(s); }
    private static String getClipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    private void nc() { if (onChanged != null) onChanged.accept(text.toString()); }
}
