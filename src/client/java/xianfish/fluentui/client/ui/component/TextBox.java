package xianfish.fluentui.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.FocusableElement;
import xianfish.fluentui.client.ui.interaction.input.CharTypedEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyPressedEvent;
import xianfish.fluentui.client.ui.interaction.input.Modifiers;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.PreeditUpdatedEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
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
    /** 预编辑浮层是否已经交给 MC：只撤回自己交出去过的那一次，不误伤别的输入框正在提交的浮层。 */
    private boolean preeditSubmitted;
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
        preeditOverlay = null;
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(false);
    }

    @Override
    public boolean preeditUpdated(PreeditUpdatedEvent event) {
        if (event == null || event.text().isEmpty()) {
            preeditOverlay = null;
        } else {
            preeditOverlay = new IMEPreeditOverlay(event.toPlatformPreedit(), font, font.lineHeight + 1);
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
        if (!visible || font == null) { retractPreedit(e); return; }
        layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean hovered = InteractionManager.hovering(this, mx, my);
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
        }

        // 预编辑浮层交给 MC 之后，它就一直被 MC 持有着，不会因为本框下一帧没再提交就自动撤销。
        // 所以候选上屏或失焦那几帧必须显式补一次 null，否则浮层会留在屏幕上。
        if (focused && preeditOverlay != null) {
            int cx = tx + font.width(disp.substring(0, cursorPos));
            preeditOverlay.updateInputPosition(cx, ty);
            e.setPreeditOverlay(preeditOverlay);
            preeditSubmitted = true;
        } else {
            retractPreedit(e);
        }
    }

    /**
     * 撤回预编辑浮层：只有 {@link #preeditSubmitted} 为真时才发那一次 null，
     * 这样只收回自己交出去过的浮层，不会把别的输入框正在提交的那一个冲掉。
     *
     * <p>渲染早退（隐藏、无字体）也走这里：浮层已经交出去了，元素这一帧不画也得收回来。</p>
     */
    private void retractPreedit(GuiGraphicsExtractor e) {
        if (!preeditSubmitted) return;
        e.setPreeditOverlay(null);
        preeditSubmitted = false;
    }

    @Override public boolean mousePressed(MousePressedEvent e) {
        if (!visible) return false;
        if (e.button() == 1) { showContextMenu((int) e.x(), (int) e.y()); return true; }
        if (e.button() != 0) return false;
        // 焦点由点击链转移算法在分发前定好：命中本框已领焦，命中外部已失焦。
        // 这里只读 focused；裸矩形自领与悬停判定由交互管理器仲裁。
        if (focused && font != null) {
            clearSel();
            cursorPos = findCursor((int) e.x() - getAbsoluteX() - PAD + (int) scrollX);
            selStart = cursorPos;
            dragging = true;
            lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        }
        return focused;
    }

    @Override public boolean mouseDragged(MouseDraggedEvent e) {
        if (!focused || !dragging || e.button() != 0) return false;
        int cp = findCursor((int) e.x() - getAbsoluteX() - PAD + (int) scrollX);
        if (cp != cursorPos) { selEnd = cp; lastTyped = System.currentTimeMillis(); cursorBlink.set(1); }
        return true;
    }

    @Override public boolean mouseReleased(MouseReleasedEvent e) {
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

    public boolean charTyped(CharTypedEvent e) {
        if (!focused || text.length() >= maxLength) return false;
        pushUndo();
        deleteSel();
        text.insert(cursorPos++, (char) e.codepoint());
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

    public boolean keyPressed(KeyPressedEvent e) {
        if (!focused) return false;
        boolean ctrl = Modifiers.control(e.modifiers());
        int BS = 259, DEL = 261, L = 263, R = 262, ENT = 257, HOME = 268, END = 269;
        int A = 65, C = 67, V = 86, X = 88, Z = 90;
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);

        if (ctrl && e.key() == Z) { undo(); return true; }
        if (ctrl && e.key() == A) { selStart = 0; selEnd = text.length(); return true; }
        if (ctrl && e.key() == C) { String sel = selectedText(); if (!sel.isEmpty()) clipboard(sel); return true; }
        if (ctrl && e.key() == X) { String sel = selectedText(); if (!sel.isEmpty()) { pushUndo(); clipboard(sel); deleteSel(); nc(); } return true; }
        if (ctrl && e.key() == V) { String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); } return true; }

        if (e.key() == BS) { if (hasSel()) { pushUndo(); deleteSel(); nc(); return true; } if (cursorPos > 0) { pushUndo(); text.deleteCharAt(--cursorPos); nc(); return true; } }
        if (e.key() == DEL) { if (hasSel()) { pushUndo(); deleteSel(); nc(); return true; } if (cursorPos < text.length()) { pushUndo(); text.deleteCharAt(cursorPos); nc(); return true; } }
        if (e.key() == L) { if (hasSel()) { cursorPos = selMin(); clearSel(); } else if (cursorPos > 0) cursorPos--; return true; }
        if (e.key() == R) { if (hasSel()) { cursorPos = selMax(); clearSel(); } else if (cursorPos < text.length()) cursorPos++; return true; }
        if (e.key() == HOME) { cursorPos = 0; clearSel(); return true; }
        if (e.key() == END) { cursorPos = text.length(); clearSel(); return true; }
        if (e.key() == ENT && onEnter != null) { onEnter.run(); return true; }
        return false;
    }

    private static void clipboard(String s) { Minecraft.getInstance().keyboardHandler.setClipboard(s); }
    private static String getClipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    private void nc() { if (onChanged != null) onChanged.accept(text.toString()); }
}
