package xianfish.fluentui.client.ui.interaction.input;

import java.util.List;
import net.minecraft.client.input.PreeditEvent;

/** IME 预编辑状态更新。字段顺序与语义沿用平台同名事件的现状。 */
public record PreeditUpdatedEvent(String text, int caretPosition, List<String> blocks, int focusedBlock) {

    /**
     * 回译成平台 {@link PreeditEvent}，供 {@code IMEPreeditOverlay} 直接使用。
     * IME 有官方支持，本事件类型直接对接 MC。
     */
    public PreeditEvent toPlatformPreedit() {
        return new PreeditEvent(text, caretPosition, blocks, focusedBlock);
    }
}
