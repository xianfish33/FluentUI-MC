package xianfish.fluentui.client.ui.tick;

import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.overlay.OverlayTree;

import java.util.function.Consumer;

/**
 * 中央 UI tick 调度遍历器：唯一的生命周期转发链。
 * <p>递归由本类对根树和浮层树统一执行，元素内部不写递归；所有元素的子代通过
 * {@link Element#children()} 向上暴露后才会被看见。
 * </p>
 */
public final class TickWalker {
    private TickWalker() {}

    /**
     * 对根树、全部浮层层根及其子孙按插入顺序投递 tick。
     * 可见性不限制，因为 UI 的 tick 动作是状态维护，与颜色/渲染可见性无关。
     */
    public static void forEachElement(Element<?> root, OverlayTree overlays, Consumer<Element<?>> action) {
        if (root != null) {
            visit(root, action);
        }
        if (overlays != null) {
            for (OverlayTree.Layer layer : overlays.layers()) {
                Element<?> el = layer.root();
                if (el != null) visit(el, action);
            }
        }
    }

    private static void visit(Element<?> el, Consumer<Element<?>> action) {
        action.accept(el);
        for (Element<?> c : el.children()) {
            if (c != null) visit(c, action);
        }
    }
}
