package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.util.Colors;
import xianfish.fluentui.client.ui.element.Element;

/**
 * 比例条（进度条）：背景 + 按 progress(0.0~1.0) 填充的前景色条。
 * 传入主题色后背景色会自动用 Colors.darken 加深；同时支持用 background(int) 来覆盖。
 */
public class ProgressBar extends Element<ProgressBar> {
    private float progress;
    private int bgColor = 0xFF3A2A2A;      // 背景色（深色）
    private int fillColor = 0xFFFF5555;    // 填充色

    public ProgressBar(int w, int h) {
        super(0, 0, w, h);
    }

    public ProgressBar(int w) {
        this(w, 6);
    }

    /** 主题色构造：背景色自动用 Colors.darken(accent, 0.55f) 加深 */
    public ProgressBar(int w, int h, int accent) {
        this(w, h);
        fillColor = accent;
        bgColor = Colors.darken(accent, 0.55f);
    }

    public ProgressBar progress(float v) {
        progress = Math.max(0f, Math.min(1f, v));
        markDirty();
        return this;
    }

    public float progress() { return progress; }

    public ProgressBar background(int c) {
        bgColor = c;
        markDirty();
        return this;
    }

    public ProgressBar fill(int c) {
        fillColor = c;
        markDirty();
        return this;
    }

    @Override
    protected void measure() {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return;
        layout();
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        e.fill(ax, ay, ax + width, ay + height, bgColor);
        int fw = Math.round(width * progress);
        if (fw > 0) e.fill(ax, ay, ax + fw, ay + height, fillColor);
    }
}