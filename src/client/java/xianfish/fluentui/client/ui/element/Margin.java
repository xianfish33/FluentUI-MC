package xianfish.fluentui.client.ui.element;

public record Margin(int top, int right, int bottom, int left) {
    public static final Margin NONE = new Margin(0, 0, 0, 0);

    public static Margin all(int value) {
        return new Margin(value, value, value, value);
    }

    public static Margin symmetric(int vertical, int horizontal) {
        return new Margin(vertical, horizontal, vertical, horizontal);
    }

    public int horizontal() { return left + right; }
    public int vertical() { return top + bottom; }
}
