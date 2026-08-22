package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.FocusableElement;
import java.util.function.Consumer;
import java.util.function.Function;

public class Stepper extends FocusableElement<Stepper> {
    public enum Mode { FIXED, PERCENT, FREE }

    private static final int BTN_W = 18, BAR_H = 2, PAD = 5;

    public record SpinColors(int bg, int border, int text, int btnBg, int btnText,
                              int btnHover, int focusBorder, int pctBar) {
        public static SpinColors blue() {
            return new SpinColors(0xFF222222, 0xFF555555, 0xFFFFFFFF, 0x30000000,
                0xAAAAAAAA, 0x503B82F6, 0xFF3B82F6, 0x803B82F6);
        }
    }

    private SpinColors colors;
    private Mode mode = Mode.FREE;
    private double value;
    private double step = 1;
    private double min = -Double.MAX_VALUE, max = Double.MAX_VALUE;
    private Component suffix = Component.empty();
    private Component[] fixedLabels;
    private int fixedIdx;
    private boolean leftHovered, rightHovered;
    private double pct;
    private final Animator percentAnim = new Animator();
    private Consumer<Double> onChanged;
    private Function<Double, Component> displayFunc;

    private boolean allowInput;
    private boolean editing;
    private StringBuilder editBuf = new StringBuilder();
    private int editCursor;
    private boolean editCommitted;
    private final Animator cursorBlink = new Animator();
    private long lastEditTyped;

    public Stepper(int w, SpinColors c) { super(w, 22); this.colors = c; percentAnim.set(0); cursorBlink.set(1); }
    public Stepper(int w) { this(w, SpinColors.blue()); }

    public Stepper mode(Mode m) {
        mode = m;
        if (m == Mode.PERCENT) { min = 0; max = 100; step = 1; }
        return this;
    }
    public Mode mode() { return mode; }

    public Stepper value(double v) { value = clamp(v); pct = (mode == Mode.PERCENT ? clampPct(value) / 100.0 : 0); percentAnim.set((float)pct); return this; }
    public double value() { return value; }

    public Stepper step(double s) { step = s; return this; }
    public Stepper range(double min, double max) { this.min = min; this.max = max; return this; }
    public Stepper suffix(String s) { suffix = s == null ? Component.empty() : Component.literal(s); return this; }
    public Stepper suffix(Component c) { suffix = c == null ? Component.empty() : c; return this; }
    public Stepper fixedLabels(String... labels) {
        Component[] arr = new Component[labels.length];
        for (int i = 0; i < labels.length; i++) arr[i] = Component.literal(labels[i]);
        return fixedLabels(arr);
    }
    public Stepper fixedLabels(Component... labels) {
        fixedLabels = labels;
        if (labels.length > 0) {
            min = 0; max = labels.length - 1; step = 1;
            if (fixedIdx >= labels.length) fixedIdx = 0;
            value = fixedIdx;
        }
        return this;
    }
    public Stepper fixedIndex(int idx) { if (fixedLabels != null && idx >= 0 && idx < fixedLabels.length) { fixedIdx = idx; value = idx; } return this; }
    public Stepper onChanged(Consumer<Double> c) { onChanged = c; return this; }
    public Stepper displayFormatter(Function<Double, String> f) { displayFunc = f == null ? null : v -> Component.literal(f.apply(v)); return this; }
    public Stepper displayComponentFormatter(Function<Double, Component> f) { displayFunc = f; return this; }
    public Stepper allowInput(boolean v) { allowInput = v; return this; }

    @Override protected void focusLost() { editing = false; }

    private double clamp(double v) { return Math.max(min, Math.min(max, v)); }
    private double clampPct(double v) { return Math.max(0, Math.min(100, v)); }

    private Component displayText() {
        if (editing) return Component.literal(editBuf.toString());
        if (displayFunc != null) return displayFunc.apply(value);
        if (mode == Mode.FIXED && fixedLabels != null) {
            if (fixedIdx >= 0 && fixedIdx < fixedLabels.length) return fixedLabels[fixedIdx];
            return Component.literal(String.valueOf((int) value));
        }
        if (mode == Mode.PERCENT) return Component.literal(((int) Math.round(value)) + "%");
        if (value == (long) value) return Component.literal(String.valueOf((long) value)).append(suffix);
        return Component.literal(String.format("%.1f", value)).append(suffix);
    }

    private void apply(double v) {
        double clamped = clamp(v);
        if (clamped == value) return;
        value = clamped;
        if (mode == Mode.PERCENT) { pct = clampPct(value) / 100.0; percentAnim.animate(percentAnim.get(), (float) pct, 150, Easing.EASE_OUT_CUBIC); }
        if (mode == Mode.FIXED && fixedLabels != null) {
            int idx = (int) Math.round(clamped);
            if (idx >= 0 && idx < fixedLabels.length) fixedIdx = idx;
        }
        if (onChanged != null) onChanged.accept(value);
    }

    private void inc() { apply(value + step); }
    private void dec() { apply(value - step); }

    private void startEdit() {
        if (!allowInput || mode == Mode.FIXED) return;
        editing = true;
        editBuf.setLength(0);
        editBuf.append(formatRaw(value));
        editCursor = editBuf.length();
        editCommitted = false;
        lastEditTyped = System.currentTimeMillis();
        cursorBlink.set(1);
    }

    private void commitEdit() {
        if (!editing) return;
        try {
            double v = Double.parseDouble(editBuf.toString());
            apply(v);
        } catch (NumberFormatException ignored) {}
        editing = false;
        editCommitted = true;
    }

    private void cancelEdit() { editing = false; }

    private static String formatRaw(double v) {
        if (v == (long) v) return String.valueOf((long) v);
        String s = String.format("%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || font == null) return; layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();

        boolean hov = isHovered(mx, my);
        e.fill(ax, ay, ax + width, ay + height, colors.bg);
        e.outline(ax, ay, width, height, focused ? colors.focusBorder : hov ? 0xFF777777 : colors.border);

        if (mode == Mode.PERCENT) {
            float ap = percentAnim.get();
            int fillW = (int) (width * ap);
            e.fill(ax, ay + height - BAR_H, ax + width, ay + height, 0x303B82F6);
            e.fill(ax, ay + height - BAR_H, ax + fillW, ay + height, colors.pctBar);
        }

        int lx = ax, lw = BTN_W, rx = ax + width - BTN_W, rw = BTN_W;
        leftHovered = mx >= lx && mx <= lx + lw && my >= ay && my <= ay + height;
        rightHovered = mx >= rx && mx <= rx + rw && my >= ay && my <= ay + height;
        boolean lh = leftHovered, rh = rightHovered;

        if (lh) e.fill(lx, ay + 1, lx + lw, ay + height - 1, colors.btnHover);
        else e.fill(lx, ay + 1, lx + lw, ay + height - 1, colors.btnBg);
        int arrowOff = (height - font.lineHeight) / 2;
        e.text(font, "<", lx + PAD, ay + arrowOff, colors.btnText);

        if (rh) e.fill(rx, ay + 1, rx + rw, ay + height - 1, colors.btnHover);
        else e.fill(rx, ay + 1, rx + rw, ay + height - 1, colors.btnBg);
        e.text(font, ">", rx + PAD, ay + arrowOff, colors.btnText);

        int centerAreaX = lx + lw, centerAreaW = width - lw - rw;
        Component display = displayText();
        int textW = font.width(display);
        int tx = ax + (width - textW) / 2;
        e.text(font, display, tx, ay + arrowOff, colors.text);

        if (editing) {
            long now = System.currentTimeMillis();
            if (!cursorBlink.isRunning() && now - lastEditTyped > 450)
                cursorBlink.to(cursorBlink.get() > 0.5f ? 0 : 1, 450, Easing.LINEAR);
            if (cursorBlink.get() > 0.5f) {
                String pre = editBuf.substring(0, Math.min(editCursor, editBuf.length()));
                int cx = tx + font.width(pre);
                e.fill(cx, ay + arrowOff, cx + 1, ay + arrowOff + font.lineHeight, colors.focusBorder);
            }
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible || btn != 0) return false;
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        boolean inBox = mx >= ax && mx <= ax + width && my >= ay && my <= ay + height;

        if (!inBox) { setFocused(false); return false; }

        int lx = ax, lw = BTN_W, rx = ax + width - BTN_W;
        if (mx >= lx && mx <= lx + lw) { dec(); return true; }
        if (mx >= rx && mx <= rx + BTN_W) { inc(); return true; }

        setFocused(true);
        if (!editing && allowInput && mode != Mode.FIXED) {
            startEdit();
        } else if (editing && editCommitted) {
            startEdit();
        }
        return true;
    }

    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) {
        if (!visible || !isHovered(mx, my)) return false;
        if (editing) return false;
        if (va > 0) inc(); else if (va < 0) dec();
        return true;
    }

    @Override public boolean isHovered(double mx, double my) {
        return super.isHovered(mx, my);
    }

    public boolean charTyped(char chr) {
        if (!editing || !allowInput) return false;
        if (chr == '-') {
            if (editBuf.length() == 0 || editBuf.charAt(0) == '-') {
                if (editBuf.length() == 0) { editBuf.append('-'); editCursor = 1; }
                else if (editBuf.charAt(0) == '-') { editBuf.deleteCharAt(0); editCursor = 0; }
                lastEditTyped = System.currentTimeMillis(); cursorBlink.set(1);
            }
            return true;
        }
        if (chr == '.' && !editBuf.toString().contains(".")) {
            if (editBuf.length() == 0 || (editBuf.length() == 1 && editBuf.charAt(0) == '-')) return true;
            editBuf.insert(editCursor++, '.');
            lastEditTyped = System.currentTimeMillis(); cursorBlink.set(1);
            return true;
        }
        if (chr >= '0' && chr <= '9') {
            editBuf.insert(editCursor++, chr);
            lastEditTyped = System.currentTimeMillis(); cursorBlink.set(1);
            return true;
        }
        return true;
    }

    public boolean keyPressed(int keyCode, int scanCode, int mods) {
        int BS = 259, DEL = 261, L = 263, R = 262, ENT = 257, ESC = 256;

        if (!editing) {
            if (keyCode == ENT || keyCode == ESC) return false;
            return false;
        }

        lastEditTyped = System.currentTimeMillis(); cursorBlink.set(1);
        if (keyCode == ENT) { commitEdit(); return true; }
        if (keyCode == ESC) { cancelEdit(); return true; }
        if (keyCode == BS) { if (editCursor > 0) { editBuf.deleteCharAt(--editCursor); } return true; }
        if (keyCode == DEL) { if (editCursor < editBuf.length()) { editBuf.deleteCharAt(editCursor); } return true; }
        if (keyCode == L) { if (editCursor > 0) editCursor--; return true; }
        if (keyCode == R) { if (editCursor < editBuf.length()) editCursor++; return true; }
        return false;
    }
}
