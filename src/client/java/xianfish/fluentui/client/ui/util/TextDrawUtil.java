package xianfish.fluentui.client.ui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 字体绘制的工具方法，支持自定义大小。
 * 封装缩放矩阵 + 文字绘制
 */
public final class TextDrawUtil {

    private TextDrawUtil() {}

    /**
     * 按给定缩放画一行字，缩放以 {@code (x, y)} 为左上角锚点。
     *
     * @param scale 缩放倍数，{@code 1} 表示原始大小；{@code <= 0} 按 {@code 1} 处理
     */
    public static void draw(GuiGraphicsExtractor e, Font font, String text, int x, int y, int color, float scale) {
        draw(e, font, Component.literal(text), x, y, color, scale);
    }

    /** 同 {@link #draw(GuiGraphicsExtractor, Font, String, int, int, int, float)}，但接受 {@link Component}。 */
    public static void draw(GuiGraphicsExtractor e, Font font, Component text, int x, int y, int color, float scale) {
        if (e == null || font == null || text == null) return;
        if (scale <= 0f) {
            e.text(font, text, x, y, color);
            return;
        }
        if (scale == 1f) {
            e.text(font, text, x, y, color);
            return;
        }
        e.pose().pushMatrix().translate(x, y).scale(scale, scale);
        e.text(font, text, 0, 0, color);
        e.pose().popMatrix();
    }

    /** 缩放之后、字形实际占的宽度。 */
    public static int scaledWidth(Font font, String text, float scale) {
        if (font == null || text == null) return 0;
        return Math.round(font.width(text) * (scale > 0f ? scale : 1f));
    }

    /** 缩放之后、字形实际占的高度：一行字的高度就是 {@code font.lineHeight} 乘缩放。 */
    public static int scaledHeight(Font font, float scale) {
        if (font == null) return 0;
        return Math.round(font.lineHeight * (scale > 0f ? scale : 1f));
    }

    /** 把 {@code (x, y, w, h)} 这块矩形按 {@code scale} 围绕自己的左上角放大。 */
    public static void drawScaledRect(GuiGraphicsExtractor e, int x, int y, int w, int h, int color, float scale) {
        if (e == null || scale <= 0f) return;
        if (scale == 1f) {
            e.fill(x, y, x + w, y + h, color);
            return;
        }
        e.pose().pushMatrix().translate(x, y).scale(scale, scale);
        e.fill(0, 0, w, h, color);
        e.pose().popMatrix();
    }
}