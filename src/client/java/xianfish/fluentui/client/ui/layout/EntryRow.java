package xianfish.fluentui.client.ui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.Layout;

public class EntryRow extends Layout<EntryRow> {
    private static final int GAP = 8;

    private int leftWidth;
    private int fullWidth;
    private int maxRightW = 260;

    public EntryRow(int fullWidth, int leftWidth) {
        super(fullWidth, 20);
        this.fullWidth = fullWidth;
        this.leftWidth = leftWidth;
    }

    public EntryRow left(Element<?> el) { addChild(el); return this; }
    public EntryRow right(Element<?> el) { addChild(el); return this; }
    public EntryRow maxRightWidth(int v) { maxRightW = v; return this; }

    @Override protected void measure() {
        int lh = children().isEmpty() ? 0 : children().get(0).getHeight();
        int rh = children().size() > 1 ? children().get(1).getHeight() : 0;
        height = Math.max(20, Math.max(lh, rh));
    }

    @Override protected void arrange() {
        if (children().isEmpty()) return;
        Element<?> left = children().get(0);
        left.setPos(0, (height - left.getHeight()) / 2);
        if (children().size() < 2) return;
        Element<?> right = children().get(1);
        int avail = leftWidth + (fullWidth - leftWidth - GAP);
        int w = Math.max(0, Math.min(right.getWidth(), avail));
        w = Math.min(w, maxRightW);
        if (right.getWidth() != w) right.size(w, right.getHeight());
        right.setPos(fullWidth - w, (height - right.getHeight()) / 2);
    }
}