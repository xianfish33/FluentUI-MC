package xianfish.fluentui.client.ui.interaction.input;

/** 一次按键松开。框架此前没有这条链，本次新增。 */
public record KeyReleasedEvent(int key, int scancode, int modifiers) {}
