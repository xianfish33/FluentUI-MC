package xianfish.fluentui.client.ui.interaction.input;

/**
 * 一个输入字符。{@code codepoint} 是完整 Unicode 码点，可能大于 0xFFFF；
 * 它不是 UTF-16 码元，转换时不要经过 {@code char}。
 */
public record CharTypedEvent(int codepoint) {}
