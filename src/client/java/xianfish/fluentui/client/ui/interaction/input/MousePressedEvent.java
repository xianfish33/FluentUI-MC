package xianfish.fluentui.client.ui.interaction.input;

/** 一次鼠标按下。{@code button} 沿用框架现状：0 左 1 右。 */
public record MousePressedEvent(double x, double y, int button, int modifiers) {}
