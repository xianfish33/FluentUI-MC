package xianfish.fluentui.client.ui.element;

/**
 * 可聚焦元素：位置无关事件（键盘输入、IME 预编辑）的分发目标。
 *
 * <p>语义收窄为“放弃位置无关事件接收权”——不表达开关、下拉收起等
 * 位置相关状态。内外判定由点击链按 L3 命中统一计算，元素不自查
 * 裸矩形决定自己的聚焦，悬停归属统一经 {@code hovering} 悬停仲裁。
 */
public interface Focusable {
    boolean isFocused();
    void defocus();
}
