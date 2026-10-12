package xianfish.fluentui.client.ui.interaction.input;

/** 一次拖动。{@code x} {@code y} 是当前点，{@code dx} {@code dy} 是本帧增量。 */
public record MouseDraggedEvent(double x, double y, int button, double dx, double dy, int modifiers) {}
