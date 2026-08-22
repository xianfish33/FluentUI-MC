package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.TooltippingElement;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

// import com.mojang.logging.LogUtils;
// import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;

public class Tooltip extends Element<Tooltip> {
    // private static final Logger LOG = LogUtils.getLogger();
    private static final int PAD_X = 8, PAD_Y = 4, GAP = 10;
    private static final double SPEED = 0.75;        // 尺寸/透明度：指数逼近系数（非线性）
    private static final double SWITCH_SPEED = 0.3;  // 换边位移：滑动系数（非线性）
    private static final double TEXT_SPEED = 0.8;    // 文字过渡：每 tick 推进（快速）
    private static Tooltip current;

    public enum ExpandDir { LEFT_TO_RIGHT, RIGHT_TO_LEFT, TOP_TO_BOTTOM }

    private String rawText;
    private List<String> lines;
    private int bgColor = 0xE0222222, textColor = 0xFFFFFFFF, borderColor = 0x80DDDDDD;
    private double animAlpha = 0.0;
    private double animW, animH, animX, animY;
    private boolean hiding;
    private ExpandDir expandDir = ExpandDir.LEFT_TO_RIGHT;
    private int viewportW, viewportH;

    private boolean lastLeft;
    private boolean posSwitch;

    private boolean textTrans;
    private double textFade;
    private List<String> oldLines;

    private Tooltip(int x, int y, String text, int vw, int vh) {
        super(x, y, 0, 0);
        this.rawText = text;
        this.viewportW = vw;
        this.viewportH = vh;
        visible = true;
        zIndex(Integer.MAX_VALUE);
    }

    public Tooltip expand(ExpandDir d) { expandDir = d; return this; }

    public static void show(int x, int y, String text) { show(x, y, text, null); }

    public static void show(int x, int y, Component text) { show(x, y, text, null); }

    public static void show(int x, int y, String text, net.minecraft.client.gui.Font font) {
        showAt(x, y, text, font, 1920, 1080, ExpandDir.LEFT_TO_RIGHT);
    }

    public static void show(int x, int y, Component text, net.minecraft.client.gui.Font font) {
        showAt(x, y, text, font, 1920, 1080, ExpandDir.LEFT_TO_RIGHT);
    }

    public static void showAt(int cursorX, int cursorY, String text,
                              net.minecraft.client.gui.Font font, int vw, int vh) {
        showAt(cursorX, cursorY, text, font, vw, vh, ExpandDir.LEFT_TO_RIGHT);
    }

    public static void showAt(int cursorX, int cursorY, Component text,
                              net.minecraft.client.gui.Font font, int vw, int vh) {
        showAt(cursorX, cursorY, text, font, vw, vh, ExpandDir.LEFT_TO_RIGHT);
    }

    public static void showAt(int cursorX, int cursorY, Component text,
                              net.minecraft.client.gui.Font font, int vw, int vh, ExpandDir dir) {
        showAt(cursorX, cursorY, text != null ? text.getString() : null, font, vw, vh, dir);
    }

    public static void showAt(int cursorX, int cursorY, String text,
                              net.minecraft.client.gui.Font font, int vw, int vh, ExpandDir dir) {
        if (current != null && current.visible && text != null && text.equals(current.rawText)) {
            if (current.hiding) current.hiding = false;
            current.expandDir = dir;
            current.viewportW = vw;
            current.viewportH = vh;
            boolean onLeft = computeSide(font, text, cursorX, vw, current.lastLeft);
            boolean sideChanged = onLeft != current.lastLeft;
            current.lastLeft = onLeft;
            if (sideChanged) current.posSwitch = true;
            int avail = onLeft ? cursorX - GAP - PAD_X * 2 : vw - cursorX - GAP - PAD_X * 2;
            var lines = wrapTo(font, text, avail);
            if (!lines.equals(current.lines)) {
                if (sideChanged) current.startTextTransition();
                current.lines = lines;
                measure(current);
            }
            current.x = clamp(onLeft ? cursorX - GAP - maxLineW(font, current.lines) - PAD_X * 2 - 2
                               : Math.min(cursorX + GAP, vw - current.width), 0, vw - current.width);
            current.y = clamp(calcY(font, cursorY, vh, current.lines), 2, vh - current.height);
            return;
        }
        hideInstance();
        current = new Tooltip(0, 0, text, vw, vh);
        current.expandDir = dir;
        if (font != null) current.font = font;
        boolean onLeft = computeSide(font, text, cursorX, vw, null);
        current.lastLeft = onLeft;
        current.lines = wrapTo(font, text, onLeft ? cursorX - GAP - PAD_X * 2 : vw - cursorX - GAP - PAD_X * 2);
        measure(current);
        current.x = clamp(onLeft ? cursorX - GAP - maxLineW(font, current.lines) - PAD_X * 2 - 2
                                 : Math.min(cursorX + GAP, vw - current.width), 0, vw - current.width);
        current.y = clamp(calcY(font, cursorY, vh, current.lines), 2, vh - current.height);
        current.animX = current.x;
        current.animY = current.y;
        ExpandDir ed = current.effDir();
        current.animW = ed == ExpandDir.TOP_TO_BOTTOM ? current.width : 0;
        current.animH = ed == ExpandDir.TOP_TO_BOTTOM ? 0 : current.height;
        current.animAlpha = 0.0;
        current.posSwitch = false;
        current.hiding = false;
        current.textTrans = false;
        current.oldLines = null;
        current.textFade = 0;
        if (InteractionManager.active != null) InteractionManager.active.overlay(current);
    }

    private static final int LINE_GAP = 2;

    private static void measure(Tooltip t) {
        int lh = t.font != null ? t.font.lineHeight + LINE_GAP : 0;
        int w = 0;
        for (String ln : t.lines) w = Math.max(w, t.font.width(ln));
        t.width = w + PAD_X * 2;
        t.height = lh * t.lines.size() + PAD_Y * 2;
    }

    private static boolean computeSide(net.minecraft.client.gui.Font f, String text, int cx, int vw, Boolean prevLeft) {
        // 默认显示右侧：换行宽度以最右视口边缘为界（vw - cx - GAP - PAD），
        // 折行后工具条右缘恰好贴住视口最右缘，因此右侧总是放得下，不会提前换边。
        int avail = vw - cx - GAP - PAD_X * 2;
        if (avail < 40) avail = 200;
        List<String> lines = f != null ? wrapTo(f, text, avail) : new ArrayList<>();
        int toolW = maxLineW(f, lines) + PAD_X * 2;
        if (toolW <= vw - cx - GAP) return false;
        // 右侧放不下（仅当光标贴近视口右缘、avail 被钳到最小宽度时）才换到左侧
        if (toolW <= cx - GAP) return true;
        return prevLeft != null && prevLeft;
    }

    private static int calcY(net.minecraft.client.gui.Font f, int cy, int vh, List<String> lines) {
        int th = (f != null ? f.lineHeight + LINE_GAP : 0) * lines.size() + PAD_Y * 2;
        if (cy + GAP + th > vh) return Math.max(2, cy - GAP - th);
        return cy + GAP;
    }

    private static int maxLineW(net.minecraft.client.gui.Font f, List<String> lines) {
        int m = 0;
        for (String l : lines) m = Math.max(m, f.width(l));
        return m;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static List<String> wrapTo(net.minecraft.client.gui.Font f, String text, int available) {
        List<String> out = new ArrayList<>();
        if (f == null || text == null) return out;
        if (available < 40) available = 200;
        int start = 0, cur = 0, width = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            if (cp == '\n') {
                out.add(text.substring(start, i));
                start = i + 1;
                cur = start;
                width = 0;
                i = start;
                continue;
            }
            int cw = f.width(new String(Character.toChars(cp)));
            if (width + cw > available && cur > start) {
                out.add(text.substring(start, cur));
                start = cur;
                width = 0;
                i = start;
                continue;
            }
            width += cw;
            cur = i + Character.charCount(cp);
            i += Character.charCount(cp);
        }
        if (start < text.length()) out.add(text.substring(start));
        if (out.isEmpty()) out.add(text);
        return out;
    }

    public static void hide() {
        if (current == null || current.hiding) return;
        current.hiding = true;
    }

    private static TooltippingElement<?> currentOwner;
    private static int curX, curY, curW = 1920, curH = 1080;

    public static void cursor(int x, int y, int vw, int vh) {
        curX = x; curY = y; curW = vw; curH = vh;
        if (InteractionManager.active != null) InteractionManager.active.updateHover(x, y);
    }

    public static TooltippingElement<?> owner() { return currentOwner; }

    public static void track(TooltippingElement<?> el, net.minecraft.client.gui.Font f) {
        if (el == null) return;
        // if (LOG.isDebugEnabled()) LOG.debug("track: el={} vis={} hit={} owner={} overlay={}",
        //         el.getClass().getSimpleName(), el.isVisible(),
        //         InteractionManager.active != null && InteractionManager.isHit(el, curX, curY),
        //         currentOwner != null ? currentOwner.getClass().getSimpleName() : null,
        //         InteractionManager.active != null && InteractionManager.active.overlay() != null ? InteractionManager.active.overlay().getClass().getSimpleName() : null);
        if (InteractionManager.active != null
                && InteractionManager.active.overlay() != null
                && !(InteractionManager.active.overlay() instanceof Tooltip)) {
            if (currentOwner != null) { currentOwner = null; hide(); }
            return;
        }
        if (el.isVisible() && InteractionManager.isHit(el, curX, curY)) {
            var cb = el.tooltipCallback();
            Component c = cb != null ? cb.get() : null;
            String t = c != null ? c.getString() : null;
            // if (LOG.isDebugEnabled()) LOG.debug("track: text={}", t);
            if (t != null && !t.isEmpty()) {
                if (currentOwner != el) {
                    currentOwner = el;
                    hideInstance();
                }
                showAt(curX, curY, t, f, curW, curH);
                return;
            }
        }
        if (currentOwner == el) { currentOwner = null; hide(); }
    }

    private static void hideInstance() {
        if (current != null) {
            current.visible = false;
            if (InteractionManager.active != null && InteractionManager.active.overlay() == current)
                InteractionManager.active.overlay(null);
            current = null;
        }
    }

    private ExpandDir effDir() {
        if (expandDir == ExpandDir.TOP_TO_BOTTOM) return ExpandDir.TOP_TO_BOTTOM;
        return lastLeft ? ExpandDir.RIGHT_TO_LEFT : ExpandDir.LEFT_TO_RIGHT;
    }

    private static double approach(double cur, double target) {
        double d = target - cur;
        if (d > -0.01 && d < 0.01) return target;
        return cur + d * SPEED;
    }

    private void startTextTransition() {
        if (hiding) return;
        if (animAlpha > 0.4 && !textTrans) {
            oldLines = lines;
            textTrans = true;
            textFade = 0;
        } else if (animAlpha <= 0.4) {
            textTrans = false;
            oldLines = null;
        }
    }

    @Override public void tick() {
        if (hiding) {
            animW = approach(animW, 0);
            animH = approach(animH, 0);
            animAlpha = approach(animAlpha, 0);
            if (animW < 0.5 && animH < 0.5 && animAlpha < 0.01) hideInstance();
            return;
        }
        animAlpha = approach(animAlpha, 1);
        animW = approach(animW, width);
        animH = approach(animH, height);
        if (posSwitch) {
            double dx = x - animX, dy = y - animY;
            animX += dx * SWITCH_SPEED;
            animY += dy * SWITCH_SPEED;
            if (dx > -0.5 && dx < 0.5 && dy > -0.5 && dy < 0.5) {
                animX = x;
                animY = y;
                posSwitch = false;
            }
        } else {
            animX = x;
            animY = y;
        }
        if (textTrans) {
            textFade += TEXT_SPEED;
            if (textFade >= 2.0) {
                textTrans = false;
                oldLines = null;
            }
        }
    }

    @Override protected void measure() { /* measured externally via static measure() */ }

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible || lines == null || lines.isEmpty() || font == null) return;
        layout();
        int sw = (int) animW, sh = (int) animH;
        double a = animAlpha;
        if (sw < 1 || sh < 1 || a < 0.01) return;

        int ax, ay;
        if (posSwitch || hiding) {
            ax = (int) animX; ay = (int) animY;
        } else if (effDir() == ExpandDir.RIGHT_TO_LEFT) {
            ax = (int) (x + width - animW); ay = (int) animY;
        } else {
            ax = (int) animX; ay = (int) animY;
        }

        int bg = blend(bgColor, a);
        int bd = blend(borderColor, a);

        e.nextStratum();
        if (bd != 0 && sw > 2 && sh > 2) {
            e.fill(ax - 1, ay - 1, ax + sw + 1, ay, bd);
            e.fill(ax - 1, ay + sh, ax + sw + 1, ay + sh + 1, bd);
            e.fill(ax - 1, ay, ax, ay + sh, bd);
            e.fill(ax + sw, ay, ax + sw + 1, ay + sh, bd);
        }
        e.fill(ax, ay, ax + sw, ay + sh, bg);

        e.enableScissor(ax, ay, ax + sw, ay + sh);
        if (textTrans && textFade < 1.0) {
            double ta = (1.0 - textFade) * a;
            if (ta > 0.01 && oldLines != null) renderLines(e, oldLines, ta, ax, ay, sh);
        } else {
            double ta = textTrans ? (textFade - 1.0) * a : a;
            renderLines(e, lines, ta, ax, ay, sh);
        }
        e.disableScissor();
    }

    private void renderLines(GuiGraphicsExtractor e, List<String> lns, double a, int ax, int ay, int sh) {
        if (lns == null || lns.isEmpty()) return;
        int tc = blend(textColor, a);
        if (tc == 0) return;
        int lh = font.lineHeight + LINE_GAP;
        for (int i = 0; i < lns.size(); i++) {
            if (ay + PAD_Y + (i + 1) * lh > ay + sh) break;
            e.text(font, lns.get(i), ax + PAD_X, ay + PAD_Y + i * lh, tc);
        }
    }

    private static int blend(int color, double a) {
        int orig = (color >> 24) & 0xFF;
        int na = (int) (orig * a);
        if (na > 255) na = 255; if (na < 0) na = 0;
        return (na << 24) | (color & 0xFFFFFF);
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) { hide(); return false; }
    @Override public boolean mouseReleased(double mx, double my, int btn) { return false; }
    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) { return false; }
    @Override public boolean mouseScrolled(double mx, double my, double ha, double va) { return false; }
}
