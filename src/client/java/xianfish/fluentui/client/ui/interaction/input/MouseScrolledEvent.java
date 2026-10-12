package xianfish.fluentui.client.ui.interaction.input;

/** 一次滚轮。横向在前纵向在后，沿用现状 {@code mouseScrolled(mx, my, ha, va)} 的顺序。 */
public record MouseScrolledEvent(double x, double y, double horizontal, double vertical, int modifiers) {}
