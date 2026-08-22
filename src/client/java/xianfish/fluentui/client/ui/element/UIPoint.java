package xianfish.fluentui.client.ui.element;

public record UIPoint(int x, int y) {
    public static final UIPoint ZERO = new UIPoint(0, 0);
}
