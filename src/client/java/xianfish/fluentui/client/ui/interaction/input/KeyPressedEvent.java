package xianfish.fluentui.client.ui.interaction.input;

/** 一次按键按下。 */
public record KeyPressedEvent(int key, int scancode, int modifiers) {}
