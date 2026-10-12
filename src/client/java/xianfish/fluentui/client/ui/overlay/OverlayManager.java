package xianfish.fluentui.client.ui.overlay;

import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

/**
 * 浮层管理器：Screen 直接持有的浮层系统所有者。
 *
 * <p>封装 {@link OverlayTree}（层列表）＋push／remove 入口＋渲染顺序＋固定渲染入口。
 * 所属权在 Screen（构造并持有本实例）；{@link InteractionManager} 只收引用做路由事务（分发／清扫／命中遍历），不拥有本实例、不管理生命周期。
 */
public final class OverlayManager {
    private final OverlayTree tree = new OverlayTree();

    /** 借出底层树供路由遍历（分发／清扫／命中）：只读遍历，不转移所有权。 */
    public OverlayTree tree() { return tree; }

    /** 推入一层顶层浮层，外部点击时关闭（默认口径）；{@code child} 是要作为新层根元素的浮层元素。 */
    public OverlayTree.Layer push(Element<?> child) {
        return tree.push(child, true);
    }

    /** 推入一层顶层浮层，{@code child} 是要作为新层根元素的浮层元素；{@code dismissOnOutsideClick} 控制外部点击时是否关闭该浮层。 */
    public OverlayTree.Layer push(Element<?> child, boolean dismissOnOutsideClick) {
        return tree.push(child, dismissOnOutsideClick);
    }

    /** 推入一层浮层，{@code parent} 是它的直系父浮层元素，{@code child} 是要作为新层根元素的浮层元素；外部点击时关闭。 */
    public OverlayTree.Layer push(Element<?> parent, Element<?> child) {
        return tree.push(parent, child, true);
    }

    /** 推入一层浮层，{@code parent} 是它的直系父浮层元素，{@code child} 是要作为新层根元素的浮层元素；{@code dismissOnOutsideClick} 控制外部点击时是否关闭该浮层。 */
    public OverlayTree.Layer push(Element<?> parent, Element<?> child, boolean dismissOnOutsideClick) {
        return tree.push(parent, child, dismissOnOutsideClick);
    }

    /** 移除一层浮层，{@code el} 是要摘掉的层根元素；它旗下的所有子层也会一起被摘除。 */
    public boolean remove(Element<?> el) { return tree.remove(el); }

    /**
     * 浮层固定渲染入口：根按加入顺序、父先子后。
     * 与输入分发（最上层优先，最上层的先拿事件）互逆——渲染自下而上盖，输入自上而下收。
     */
    public void renderOverlay(net.minecraft.client.gui.GuiGraphicsExtractor e, int mx, int my, float d) {
        for (OverlayTree.Layer layer : tree.layers()) {
            Element<?> root = layer.root();
            if (root.isVisible()) {
                root.extractRenderState(e, mx, my, d);
            }
        }
    }
}
