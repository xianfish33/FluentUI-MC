package xianfish.fluentui.client.ui.interaction.input;

/**
 * 修饰键位掩码。位值由本包自定。
 *
 * <p>掩码而不是四个布尔，原因有两个：一是传递成本低，二是组合不分配对象。
 * 四个判定方法把位运算关在这个类内部，调用方看到的是" shift(m) "这种意图，而不是一串移位。
 */
public final class Modifiers {
    public static final int NONE = 0;
    public static final int SHIFT = 1;
    public static final int CONTROL = 1 << 1;
    public static final int ALT = 1 << 2;
    public static final int SUPER = 1 << 3;

    private Modifiers() {}

    public static boolean shift(int modifiers) { return (modifiers & SHIFT) != 0; }
    public static boolean control(int modifiers) { return (modifiers & CONTROL) != 0; }
    public static boolean alt(int modifiers) { return (modifiers & ALT) != 0; }
    public static boolean superKey(int modifiers) { return (modifiers & SUPER) != 0; }
}
