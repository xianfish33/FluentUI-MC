package xianfish.fluentui.client.ui.element;

import xianfish.fluentui.client.ui.interaction.input.CharTypedEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyPressedEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.PreeditUpdatedEvent;

/** 可接收键盘与 IME 事件的元素契约。 */
public interface KeyboardReceiver {
    boolean keyPressed(KeyPressedEvent e);
    boolean keyReleased(KeyReleasedEvent e);
    boolean charTyped(CharTypedEvent e);
    default boolean preeditUpdated(PreeditUpdatedEvent e) { return false; }
}
