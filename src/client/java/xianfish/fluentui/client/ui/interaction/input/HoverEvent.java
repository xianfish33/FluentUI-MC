package xianfish.fluentui.client.ui.interaction.input;

/** 悬停位置变化。{@code mouseEntered} 与 {@code mouseMoved} 共用，{@code mouseExited} 也用它。 */
public record HoverEvent(double x, double y, int modifiers) {}
