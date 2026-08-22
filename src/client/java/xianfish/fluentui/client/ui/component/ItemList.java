package xianfish.fluentui.client.ui.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.layout.Orientation;
import xianfish.fluentui.client.ui.layout.StackPanel;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public class ItemList<T> extends ListView<T> { // ItemList不是ItemView的list，但是我不想动依赖了，其实是可编辑的ListView
    private static final int DEL_W = 14;

    private int delPad() { return Math.max(1, (itemHeight - DEL_W) / 2); }

    private BiConsumer<T, StackPanel> rowRenderer;
    private Consumer<ListView<T>> onChange;
    private int hoveredDel = -1;

    private boolean adding;
    private Consumer<String> onAdd;
    private Consumer<String> onDuplicate;
    private boolean dedupe;
    private final TextBox addBox = new TextBox(0, 16)
        .placeholder(net.minecraft.network.chat.Component.translatable("fluentui.itemlist.new_item"))
        .maxLength(48)
        .onEnter(this::confirmAdd);

    public ItemList(int w, int h) { super(w, h); }

    @Override public ItemList<T> items(List<T> v) { super.items(v); return this; }
    @Override public ItemList<T> itemHeight(int v) { super.itemHeight(v); return this; }
    @Override public ItemList<T> tooltip(String s) { super.tooltip(s); return this; }
    @Override public ItemList<T> tooltip(net.minecraft.network.chat.Component c) { super.tooltip(c); return this; }
    @Override public ItemList<T> tooltip(java.util.function.Supplier<net.minecraft.network.chat.Component> c) { super.tooltip(c); return this; }
    public ItemList<T> renderer(BiConsumer<T, StackPanel> r) { rowRenderer = r; return this; }
    public ItemList<T> onChange(Consumer<ListView<T>> c) { onChange = c; return this; }
    public ItemList<T> onAdd(Consumer<String> c) { onAdd = c; return this; }
    public ItemList<T> onDuplicate(Consumer<String> c) { onDuplicate = c; return this; }
    public ItemList<T> dedupe(boolean v) { dedupe = v; return this; }
    public boolean dedupe() { return dedupe; }

    @Override public ItemList<T> font(net.minecraft.client.gui.Font f) { super.font(f); return this; }

    @Override protected int contentHeight() { return Math.max(0, height - itemHeight); }

    private int addY() { return getAbsoluteY() + contentHeight(); }
    private boolean addHit(double mx, double my) {
        int ax = getAbsoluteX(), ay = addY();
        return mx >= ax && mx <= ax + width && my >= ay && my <= ay + itemHeight;
    }

    @Override protected void drawRow(T item, int idx, boolean sel, boolean hov,
                                      GuiGraphicsExtractor e, int ax, int iy, int ib) {
        int contentW = width - DEL_W - delPad() * 2 - PAD_X - (sel ? ACCENT_W : 0);

        if (rowRenderer != null) {
            var row = buildRow(item, contentW);
            row.topLeft(ax + PAD_X + (sel ? ACCENT_W : 0), iy + (ib - iy - row.getHeight()) / 2);
            row.extractRenderState(e, -999, -999, 0);
        } else {
            super.drawRow(item, idx, sel, hov, e, ax, iy, ib);
        }

        int dx = ax + width - DEL_W - delPad();
        int dy = iy + delPad();
        int dh = ib - iy - delPad() * 2;
        if (idx == hoveredDel) e.fill(dx, dy, dx + DEL_W, dy + dh, 0x80EF4444);
        int tx = dx + (DEL_W - font.width("×")) / 2;
        e.text(font, "×", tx, dy + (dh - font.lineHeight) / 2 + 1, 0xFFFF6666);
    }

    private final StackPanel rowCache = new StackPanel(Orientation.HORIZONTAL).spacing(4).bgColor(0);

    private StackPanel buildRow(T item, int maxW) {
        rowCache.children().clear();
        rowCache.size(maxW, itemHeight - 2);
        rowCache.setParent(null);
        if (rowRenderer != null) rowRenderer.accept(item, rowCache);
        rowCache.layout();
        return rowCache;
    }

    @Override protected boolean clickRow(double mx, double my, int btn) {
        int ax = getAbsoluteX(), ay = getAbsoluteY();
        if (hoveredIdx < 0 || hoveredIdx >= data.size()) return false;
        int iy = ay + hoveredIdx * itemHeight - (int) scrollAnim.get();
        int dx = ax + width - DEL_W - delPad();
        int dy = iy + delPad();
        int dh = itemHeight - delPad() * 2;
        if (mx >= dx && mx <= dx + DEL_W && my >= dy && my <= dy + dh) {
            data.remove(hoveredIdx);
            if (onChange != null) onChange.accept(this);
            return true;
        }
        return false;
    }

    @Override public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
        super.extractRenderState(e, mx, my, d);
        hoveredDel = -1;
        int ax = getAbsoluteX(), ay = getAbsoluteY(), ch = contentHeight();
        if (mx >= ax && mx <= ax + width && my >= ay && my <= ay + ch && hoveredIdx >= 0) {
            int iy = ay + hoveredIdx * itemHeight - (int) scrollAnim.get();
            int dx = ax + width - DEL_W - delPad();
            int dy = iy + delPad();
            int dh = itemHeight - delPad() * 2;
            if (mx >= dx && mx <= dx + DEL_W && my >= dy && my <= dy + dh)
                hoveredDel = hoveredIdx;
        }
        renderAddRow(e, ax, ay, ch, mx, my, d);
    }

    private void renderAddRow(GuiGraphicsExtractor e, int ax, int ay, int ch, int mx, int my, float d) {
        if (font == null) return;
        int ry = ay + ch;
        if (adding && font != null) {
            int boxW = width - DEL_W - delPad() * 3;
            addBox.topLeft(ax + delPad(), ry + delPad()).size(boxW, itemHeight - delPad() * 2);
            if (addBox.font() == null) addBox.font(font);
            addBox.extractRenderState(e, mx, my, d);

            int dx = ax + width - DEL_W - delPad();
            int dy = ry + delPad();
            int dh = itemHeight - delPad() * 2;
            boolean hov = mx >= dx && mx <= dx + DEL_W && my >= dy && my <= dy + dh;
            e.fill(dx, dy, dx + DEL_W, dy + dh, hov ? 0xFF3B82F6 : 0x8060A5FA);
            String check = "\u2714";
            int tx = dx + (DEL_W - font.width(check)) / 2;
            e.text(font, check, tx, dy + (dh - font.lineHeight) / 2 + 1, 0xFFFFFFFF);
        } else {
            e.fill(ax, ry, ax + width, ry + itemHeight, 0x303B82F6);
            net.minecraft.network.chat.Component label = net.minecraft.network.chat.Component.translatable("fluentui.itemlist.add_item");
            e.text(font, label, ax + PAD_X, ry + (itemHeight - font.lineHeight) / 2, 0xCCFFFFFF);
        }
    }

    private boolean confirmHit(double mx, double my) {
        int ax = getAbsoluteX(), ry = addY();
        int dx = ax + width - DEL_W - delPad();
        int dy = ry + delPad();
        int dh = itemHeight - delPad() * 2;
        return mx >= dx && mx <= dx + DEL_W && my >= dy && my <= dy + dh;
    }

    private void confirmAdd() {
        String val = addBox.text().trim();
        if (val.isEmpty()) return;
        if (dedupe) {
            for (T existing : data) {
                if (existing != null && existing.toString().equalsIgnoreCase(val)) {
                    if (onDuplicate != null) onDuplicate.accept(val);
                    return;
                }
            }
        }
        data.add((T) val);
        if (onAdd != null) onAdd.accept(val);
        if (onChange != null) onChange.accept(this);
        adding = false;
        addBox.defocus();
        addBox.text("");
    }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (!visible) return false;
        if (adding) {
            if (btn == 1) { if (addHit(mx, my)) { addBox.mouseClicked(mx, my, btn); return true; } return false; }
            if (btn != 0) return false;
            if (confirmHit(mx, my)) { confirmAdd(); return true; }
            if (addHit(mx, my)) { addBox.mouseClicked(mx, my, btn); return true; }
            return super.mouseClicked(mx, my, btn);
        }
        if (btn != 0) return false;
        if (addHit(mx, my)) { adding = true; addBox.focus(); return true; }
        return super.mouseClicked(mx, my, btn);
    }

    @Override public boolean mouseReleased(double mx, double my, int btn) {
        if (adding && addHit(mx, my)) return addBox.mouseReleased(mx, my, btn);
        return super.mouseReleased(mx, my, btn);
    }

    @Override public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (adding && addHit(mx, my)) return addBox.mouseDragged(mx, my, btn, dx, dy);
        return super.mouseDragged(mx, my, btn, dx, dy);
    }
}