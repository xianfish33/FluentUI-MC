package xianfish.fluentui.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.FocusableElement;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.input.CharTypedEvent;
import xianfish.fluentui.client.ui.interaction.input.HoverEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyPressedEvent;
import xianfish.fluentui.client.ui.interaction.input.Modifiers;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import xianfish.fluentui.client.ui.interaction.input.PreeditUpdatedEvent;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import java.util.*;
import java.util.function.Consumer;

/**
 * 自动补全输入框：输入框本体留在布局树，候选列表以内嵌浮层形式推入浮层层列表。
 *
 * <p>本体为可聚焦元素，候选面板 {@code DropdownOverlay} 本身是 {@link Element}（元素），
 * 不继承任何浮层专属类型；
 * 面板从不挂入布局树，无需 {@code removeFromParent()} 脱离布局父容器，
 * 有候选时直接经 {@code overlays().push} 完成根级推入。
 * 面板的鼠标事件方法只对命中自己的部分返回 {@code true}，其余放行，所以底下输入框照常收键盘。
 * 外部输入经层记录的 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子收起；
 * 不靠 hover 丢失收起，靠选中、按键与外部关闭收起，与其它浮层为多浮层共存（无竞争）关系。
 * 关闭统一走 {@link #close()} 关闭（{@code hide}/{@code dismiss} 为兼容别名），
 * 面板 {@code close()} 委托给属主 {@code closeDropdown()}，集中复位 {@code open=false} 并经
 * {@code overlays().remove} 卸载摘除浮层层记录。</p>
 */
public class AutoCompleteBox extends FocusableElement<AutoCompleteBox> {
    private static final int PAD = 6, ITEM_H = 18, DD_OFF = 2, MAX_DD_H = 150, MAX_UNDO = 50;

    /**
     * 自动补全配色：前七项是输入框本体，与 {@link TextBox} 逐值对齐；后五项是浮层下拉面板，
     * 同样取输入框那一套（面板底色 {@code 0xFF222222}、边框 {@code 0xFF555555}，
     * 高亮底色与选区同值同不透明度 {@code 0x403B82F6}），不再用更暗的半透明面板底。
     */
    public record AutoColors(int bg, int border, int text, int focusBorder,
                              int placeholder, int cursor, int selection,
                              int ddBg, int ddBorder, int ddText, int ddHoverBg, int ddHighlight) {
        public static AutoColors blue() {
            return new AutoColors(0xFF222222, 0xFF555555, 0xFFFFFFFF, 0xFF3B82F6,
                0xFF666666, 0xFFFFFFFF, 0x403B82F6,
                0xFF222222, 0xFF555555, 0xFFFFFFFF, 0x403B82F6, 0x403B82F6);
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
    /** 预编辑浮层是否已经交给 MC：只撤回自己交出去过的那一次，不误伤别的输入框正在提交的浮层。 */
    private boolean preeditSubmitted;
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
        preeditOverlay = null;
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(false);
    }

    @Override
    public boolean preeditUpdated(PreeditUpdatedEvent event) {
        if (event == null || event.text().isEmpty()) preeditOverlay = null;
        else preeditOverlay = new IMEPreeditOverlay(event.toPlatformPreedit(), font, font.lineHeight + 1);
        return true;
    }

    @Override protected void focusGained() {
        Minecraft.getInstance().textInputManager().onTextInputFocusChange(true);
        preeditOverlay = null;
    }

    private void closeMenu() { if (contextMenu != null) contextMenu.hide(); }

    /**
     * 右键菜单／候选下拉打开期间压制 tooltip：两者与 tooltip 同为浮层，
     * 不压制则后唤起的 tooltip 会盖在菜单／下拉上。
     * （{@code dropdownOverlay} 非空即打开中，关闭时由 {@code closeDropdown} 置空。）
     */
    @Override protected boolean isTooltipSuppressed() {
        return (contextMenu != null && contextMenu.isVisible()) || dropdownOverlay != null;
    }

    private void showContextMenu(int mx, int my) {
        if (contextMenu == null) { contextMenu = new ContextMenu().font(font); contextMenu.setParent(null); addFocusFamily(contextMenu); }
        List<ContextMenu.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.paste"), () -> {
            String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); filter(); }
        }, getClipboard() != null && !getClipboard().isEmpty()));
        if (hasSel()) {
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.copy"), () -> { String s = selectedText(); if (!s.isEmpty()) clipboard(s); }, true));
            items.add(new ContextMenu.MenuItem(Component.translatable("fluentui.textbox.cut"), () -> { String s = selectedText(); if (!s.isEmpty()) { pushUndo(); clipboard(s); deleteSel(); nc(); filter(); } }, true));
        }
        contextMenu.items(items);
        dismissTooltip();
        contextMenu.show(mx, my);
    }

    /**
     * 关闭候选下拉的唯一收口：开始收窄动画，动画走完由面板经 {@code tick} 回调
     * {@link #detachDropdown} 摘除节点并复位状态。
     *
     * <p>关闭分两段：本方法只启动 {@code heightAnim} 到 0 的非线性收窄（与展开对称），
     * 面板在动画期间继续挂载渲染；高度归零后 {@code DropdownOverlay.tick} 触发
     * 真正的卸载。选中、失焦、按键与外部关闭各条路径收敛到同一形状。</p>
     */
    private void closeDropdown() {
        hoveredIdx = -1;
        if (dropdownOverlay != null) {
            dropdownOverlay.beginClose();
        } else {
            open = false;
        }
    }

    /**
     * 收窄动画走完后的实际卸载：经 {@code overlays().remove} 摘除浮层树节点，
     * 置空 {@code dropdownOverlay} 并复位 {@code open=false}。
     * 仅由 {@code DropdownOverlay.tick} 在动画归零时调用。
     */
    private void detachDropdown() {
        open = false;
        if (dropdownOverlay != null) {
            removeFocusFamily(dropdownOverlay);
            if (InteractionManager.active != null) InteractionManager.active.overlays().remove(dropdownOverlay);
            dropdownOverlay = null;
        }
    }

    /**
     * 打开候选下拉：有过滤结果时置 {@code open=true}，无面板则新建并做根级推入，已有则刷新高度。
     *
     * <p>打开路径常发生在输入事件调用栈内（字符输入、粘贴、按键），清扫跑在分发前，
     * 新面板在清扫时还不存在，自然免疫，无需豁免；下一次输入恢复正常关闭判定。</p>
     */
    private void showDropdown() {
        if (filtered.isEmpty()) return;
        open = true; hoveredIdx = -1;
        if (dropdownOverlay == null) {
            dropdownOverlay = new DropdownOverlay(this);
            if (InteractionManager.active != null) InteractionManager.active.overlays().push(dropdownOverlay);
            // 候选面板是焦点家族成员：点击它不算外部，不失焦不收起。
            addFocusFamily(dropdownOverlay);
        } else {
            dropdownOverlay.cancelClose();
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
        int ax = getAbsoluteX();
        // 焦点由点击链转移算法在分发前定好，这里只读 focused。
        if (focused && font != null) {
            clearSel();
            cursorPos = findCursor((int) e.x() - ax - PAD + (int) scrollX);
            selStart = cursorPos;
            dragging = true;
            lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        }
        return focused;
    }

    @Override public boolean mouseDragged(MouseDraggedEvent e) {
        if (!focused || !dragging || e.button() != 0) return false;
        int ax = getAbsoluteX();
        int cp = findCursor((int) e.x() - ax - PAD + (int) scrollX);
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
        pushUndo(); deleteSel();
        text.insert(cursorPos++, (char) e.codepoint());
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);
        nc(); filter();
        return true;
    }

    public boolean keyPressed(KeyPressedEvent e) {
        if (!focused) return false;
        boolean ctrl = Modifiers.control(e.modifiers());
        int BS = 259, DEL = 261, L = 263, R = 262, ENT = 257, TAB = 258, ESC = 256;
        int UP = 265, DOWN = 264, HOME = 268, END = 269;
        int A = 65, C = 67, V = 86, X = 88, Z = 90;
        lastTyped = System.currentTimeMillis(); cursorBlink.set(1);

        if (ctrl && e.key() == Z) { undo(); return true; }
        if (ctrl && e.key() == A) { selStart = 0; selEnd = text.length(); return true; }
        if (ctrl && e.key() == C) { String s = selectedText(); if (!s.isEmpty()) clipboard(s); return true; }
        if (ctrl && e.key() == X) { String s = selectedText(); if (!s.isEmpty()) { pushUndo(); clipboard(s); deleteSel(); nc(); filter(); } return true; }
        if (ctrl && e.key() == V) { String cb = getClipboard(); if (cb != null && !cb.isEmpty()) { pushUndo(); deleteSel(); for (char ch : cb.toCharArray()) { if (text.length() < maxLength) text.insert(cursorPos++, ch); } nc(); filter(); } return true; }

        if (open && !filtered.isEmpty()) {
            if (e.key() == TAB || e.key() == DOWN) { hoveredIdx = (hoveredIdx + 1) % filtered.size(); pageTo(hoveredIdx); return true; }
            if (e.key() == UP) { hoveredIdx = (hoveredIdx - 1 + filtered.size()) % filtered.size(); pageTo(hoveredIdx); return true; }
            if (e.key() == ENT) { selectFiltered(Math.max(0, hoveredIdx)); return true; }
            if (e.key() == ESC) { closeDropdown(); return true; }
        }
        if (e.key() == ESC) { closeDropdown(); return false; }

        if (e.key() == BS) { if (hasSel()) { pushUndo(); deleteSel(); nc(); filter(); return true; } if (cursorPos > 0) { pushUndo(); text.deleteCharAt(--cursorPos); nc(); filter(); return true; } }
        if (e.key() == DEL) { if (hasSel()) { pushUndo(); deleteSel(); nc(); filter(); return true; } if (cursorPos < text.length()) { pushUndo(); text.deleteCharAt(cursorPos); nc(); filter(); return true; } }
        if (e.key() == L) { if (hasSel()) { cursorPos = selMin(); clearSel(); } else if (cursorPos > 0) cursorPos--; return true; }
        if (e.key() == R) { if (hasSel()) { cursorPos = selMax(); clearSel(); } else if (cursorPos < text.length()) cursorPos++; return true; }
        if (e.key() == HOME) { cursorPos = 0; clearSel(); return true; }
        if (e.key() == END) { cursorPos = text.length(); clearSel(); return true; }
        if (e.key() == ENT) { if (onEnter != null) onEnter.run(); return true; }
        return false;
    }

    private void pageTo(int idx) {
        if (dropdownOverlay != null) dropdownOverlay.pageTo(idx);
    }

    private static void clipboard(String s) { Minecraft.getInstance().keyboardHandler.setClipboard(s); }
    private static String getClipboard() { return Minecraft.getInstance().keyboardHandler.getClipboard(); }
    private void nc() { if (onChanged != null) onChanged.accept(text.toString()); }

    private class DropdownOverlay extends Element<DropdownOverlay> {
        private final AutoCompleteBox box;
        private final Animator heightAnim = new Animator();
        private final Animator scrollAnim = new Animator();
        /** 收窄中：高度动画走完前面板继续挂载渲染，但不接受选中。 */
        private boolean closing;

        /**
         * {@code close()} 关闭：面板自身不直接摘除节点，而是委托给属主 {@code closeDropdown()}，
         * 由属主启动收窄动画，动画走完再经 {@code detachDropdown} 卸载并复位 {@code open=false}。
         * 外部关闭经 {@code dismissOnOutsideClick} 路由到 {@code onOutsideInteraction()} 外部交互钩子，
         * 最终同样回到本方法（{@code hide}/{@code dismiss} 为兼容别名，优先使用 {@code close()}）。
         */
        public void close() { closeDropdown(); }

        @Override public void onOutsideInteraction() { close(); }
        DropdownOverlay(AutoCompleteBox box) {
            super(box.dropdownX(), box.dropdownY(), box.dropdownW(), 0);
            this.box = box;
            visible = true;
            heightAnim.set(0); scrollAnim.set(0);
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

        /** 开始收窄：高度非线性动画到 0（与展开对称），归零后经 tick 卸载。 */
        void beginClose() {
            closing = true;
            heightAnim.animate(heightAnim.get(), 0, 200, Easing.EASE_OUT_CUBIC);
        }

        /** 取消收窄（收窄期间又有输入）：回到展开状态，后续 refresh 重新定高。 */
        void cancelClose() {
            closing = false;
        }

        @Override public void tick() {
            if (closing && heightAnim.get() < 0.01f) {
                closing = false;
                box.detachDropdown();
            }
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

            // 绘制高度恒等于动画高度：收窄时背景与边框跟着动画一起收。
            // 若用 min(curH, fullH)，收窄瞬间 fullH 已是新小高度，
            // 剪刀直接卡到终点，200ms 的 curH 动画全被裁掉，看起来就是闪变。
            int drawH = curH;
            int sc = (int) scrollAnim.get();

            e.nextStratum();
            e.enableScissor(ax, baseY, ax + width, baseY + drawH);
            e.fill(ax, baseY, ax + width, baseY + drawH, box.colors.ddBg);
            e.outline(ax, baseY, width, drawH, box.colors.ddBorder);

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

            // 悬停索引不在渲染期写：box.hoveredIdx 由 refreshHover（输入路径）独占写入，
            // 这里只按 box.hoveredIdx 读出高亮行（上面 h = i == box.hoveredIdx）。
            e.disableScissor();
        }

        /**
         * 悬停索引刷新：写的是属主 {@link AutoCompleteBox#hoveredIdx}（不是本面板自己的字段），
         * 由带坐标的输入方法用事件坐标当场算，是它在鼠标侧的唯一写入点。
         *
         * <p>算法与原 {@code extractRenderState} 末尾被移走的那段同源：面板仍展开
         * （{@code items} 非空且 {@code isOpen()}）、光标落在
         * {@code [baseY, baseY + curH]} 内时按 {@code (my - baseY + sc) / ITEM_H} 就地求行号；
         * 与原实现一致，命中范围之外不复位（保留最后一次命中）。{@code ax/baseY} 用
         * {@link AutoCompleteBox#dropdownX()}/{@link AutoCompleteBox#dropdownY()}，
         * {@code curH} 用 {@code heightAnim}、{@code sc} 用 {@code scrollAnim}——与渲染路径同一批取值表达式。</p>
         */
        private void refreshHover(double mx, double my) {
            List<String> items = box.filteredItems();
            if (items.isEmpty() || !box.isOpen()) return;
            int curH = Math.max(1, (int) heightAnim.get());
            int ax = box.dropdownX();
            int baseY = box.dropdownY();
            int sc = (int) scrollAnim.get();
            // 事件坐标是 double、渲染入参是 int：入口处收窄一次，后续整数算式与渲染路径逐字相同。
            int cx = (int) mx, cy = (int) my;
            if (cx >= ax && cx <= ax + width && cy >= baseY && cy <= baseY + curH) {
                int ri = (cy - baseY + sc) / ITEM_H;
                if (ri >= 0 && ri < items.size()) box.hoveredIdx = ri;
            }
        }

        @Override public void mouseEntered(HoverEvent e) { super.mouseEntered(e); refreshHover(e.x(), e.y()); }
        @Override public void mouseMoved(HoverEvent e) { super.mouseMoved(e); refreshHover(e.x(), e.y()); }
        @Override public void mouseExited(HoverEvent e) { super.mouseExited(e); refreshHover(e.x(), e.y()); }

        @Override public boolean mousePressed(MousePressedEvent e) {
            if (closing) return false;
            if (e.button() != 0) return false;
            // 先按事件坐标当场算，再读 box.hoveredIdx：选中的是这一刻光标压着的那一项。
            refreshHover(e.x(), e.y());
            if (box.hoveredIdx >= 0 && box.hoveredIdx < box.filteredItems().size()) {
                box.selectFiltered(box.hoveredIdx); return true;
            }
            if (shapeHit(e.x(), e.y())) return true;
            return false;
        }

        @Override public boolean mouseScrolled(MouseScrolledEvent e) {
            List<String> items = box.filteredItems();
            if (items.isEmpty()) return false;
            int curH = Math.max(1, (int) heightAnim.get());
            float maxScroll = Math.max(0, items.size() * ITEM_H - curH);
            float target = Math.max(0, Math.min(maxScroll, scrollAnim.get() - (float) e.vertical() * 20));
            scrollAnim.animate(scrollAnim.get(), target, 150, Easing.EASE_OUT_CUBIC);
            return true;
        }

        @Override public boolean shapeHit(double mx, double my) {
            if (heightAnim.get() < 0.01f) return false;
            int ax = getAbsoluteX(), ay = getAbsoluteY();
            int curH = Math.max(1, (int) heightAnim.get());
            return mx >= ax && mx <= ax + width && my >= ay && my <= ay + curH;
        }
    }
}
