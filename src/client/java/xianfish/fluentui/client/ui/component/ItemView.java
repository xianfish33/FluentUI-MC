package xianfish.fluentui.client.ui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import xianfish.fluentui.client.ui.animation.Animator;
import xianfish.fluentui.client.ui.animation.Easing;
import xianfish.fluentui.client.ui.element.Colors;
import xianfish.fluentui.client.ui.element.TooltippingElement;

import java.util.ArrayList;
import java.util.List;

/**
 * notice: 创建该组件的屏幕必须处于游戏内（不然哪来的ItemStack）游戏实例就会爆炸
 */
public class ItemView extends TooltippingElement<ItemView> {
    private static final int ITEM_SIZE = 16;

    public enum Size {
        SMALL(18), LARGE(26);
        private final int pixels;
        Size(int pixels) { this.pixels = pixels; }
        public int pixels() { return pixels; }
    }

    /** 右键菜单的生成模式 */
    public enum MenuMode {
        SIMPLE,   // 只显示物品名称
        ADVANCED, // 显示 getTooltipLines(…) 获取到的完整信息（含附加的 / 设置启用的动态内容）
        CUSTOM    // 第一行物品名称，下面支持自定义内容（Component，内置富文本/翻译键）
    }

    protected Item item;
    protected ItemStack stackSource;
    protected int stackCount = 1;
    protected boolean hovered;
    protected boolean pressed;
    protected boolean showDecorations = true;
    protected int bgColor = 0x28000000;
    protected int hoverColor = 0x55FFFFFF;
    protected final Animator hoverAnim = new Animator();
    private float hoverAlpha;

    protected MenuMode menuMode = MenuMode.SIMPLE;
    protected List<Component> customMenuLines = new ArrayList<>();
    protected ContextMenu contextMenu;

    public ItemView(ItemStack stack, Size size) { super(size.pixels(), size.pixels()); this.stackSource = stack != null ? stack : ItemStack.EMPTY; hoverAnim.set(0); initTooltip(); }
    public ItemView(Item item, Size size) { super(size.pixels(), size.pixels()); this.item = item; hoverAnim.set(0); initTooltip(); }
    public ItemView(ItemStack stack) { this(stack, Size.LARGE); }
    public ItemView(Item item) { this(item, Size.LARGE); }

    private void initTooltip() { tooltip(() -> provideStack().getHoverName()); }

    private ItemStack provideStack() {
        try {
            if (item != null) return new ItemStack(item, stackCount);
            if (stackSource != null && !stackSource.isEmpty()) return stackCount == 1 ? stackSource.copy() : stackSource.copyWithCount(stackCount);
        } catch (Throwable t) {
            // 组件尚未绑定（例如标题画面），在下一帧重试
        }
        return ItemStack.EMPTY;
    }

    public ItemView item(ItemStack s) { item = null; stackSource = s != null ? s : ItemStack.EMPTY; stackCount = 1; initTooltip(); markDirty(); return this; }
    public ItemView item(Item i) { item = i; stackSource = null; initTooltip(); markDirty(); return this; }
    public ItemView count(int c) { stackCount = Math.max(1, c); markDirty(); return this; }
    public ItemStack itemStack() { return provideStack(); }

    /** 设置右键菜单模式 */
    public ItemView contextMenu(MenuMode mode) { menuMode = mode; markDirty(); return this; }
    /** CUSTOM 模式下追加一条自定义行（Component 支持翻译键与富文本样式） */
    public ItemView customMenuLine(Component line) { customMenuLines.add(line); markDirty(); return this; }

    public ItemView decorations(boolean v) { showDecorations = v; markDirty(); return this; }
    public ItemView bgColor(int c) { bgColor = c; markDirty(); return this; }
    public ItemView hoverColor(int c) { hoverColor = c; markDirty(); return this; }

    @Override protected void measure() {}

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        if (!visible) return;
        layout();
        hoverAlpha = hoverAnim.get();

        boolean h = isHovered(mx, my);
        if (h != hovered) {
            hovered = h;
            hoverAnim.animate(hovered ? 0 : 1, hovered ? 1 : 0, 120, Easing.EASE_OUT_CUBIC);
        }

        int ax = getAbsoluteX(), ay = getAbsoluteY();
        if ((bgColor >>> 24) != 0) {
            e.fill(ax, ay, ax + width, ay + height, bgColor);
        }

        if (hoverAlpha > 0.01f) {
            int tint = Colors.lerp(0x00000000, hoverColor, Math.min(1f, hoverAlpha));
            e.fill(ax, ay, ax + width, ay + height, tint);
        }

        ItemStack render = provideStack();
        if (!render.isEmpty()) {
            int ix = ax + (width - ITEM_SIZE) / 2;
            int iy = ay + (height - ITEM_SIZE) / 2;
            e.item(render, ix, iy);
            if (showDecorations && font != null) {
                if (render.getCount() > 1) {
                    String countText = String.valueOf(render.getCount());
                    int tx = ax + width - font.width(countText);
                    int ty = ay + height - font.lineHeight;
                    e.text(font, countText, tx, ty, 0xFFFFFFFF, true);
                }
            }
        }
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0 && btn != 1) return false;
        if (!isHovered(mx, my)) return false;
        if (btn == 1) { openContextMenu((int) mx, (int) my); return true; }
        pressed = true;
        hoverAnim.animate(1, 1.4f, 60, Easing.EASE_IN_CUBIC);
        return true;
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (btn != 0) return false;
        boolean wp = pressed; pressed = false;
        hoverAnim.animate(1.4f, hovered ? 1 : 0, 100, Easing.EASE_OUT_CUBIC);
        return wp;
    }

    private void openContextMenu(int x, int y) {
        ItemStack stack = provideStack();
        if (font == null || stack.isEmpty()) return;
        if (contextMenu == null) { contextMenu = new ContextMenu(); contextMenu.setParent(null); }
        contextMenu.font(font);

        List<ContextMenu.MenuItem> items = new ArrayList<>();
        Component name = stack.getHoverName();
        switch (menuMode) {
            case SIMPLE -> items.add(item(name));
            case ADVANCED -> {
                for (Component line : tooltipLines(stack)) {
                    items.add(item(line));
                }
            }
            case CUSTOM -> {
                items.add(item(name));
                for (Component line : customMenuLines) items.add(item(line));
            }
        }

        contextMenu.items(items);
        contextMenu.show(x, y);
    }

    private List<Component> tooltipLines(ItemStack stack) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return List.of();
            net.minecraft.world.item.Item.TooltipContext ctx = net.minecraft.world.item.Item.TooltipContext.of(mc.player.level());
            return stack.getTooltipLines(ctx, mc.player, TooltipFlag.ADVANCED);
        } catch (Throwable t) {
            return List.of();
        }
    }

    private ContextMenu.MenuItem item(Component line) {
        return new ContextMenu.MenuItem(line, () -> {}, true);
    }
}