package xianfish.fluentui.client.ui.overlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.LayoutElement;
import xianfish.fluentui.client.ui.interaction.Hit;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Element} 浮层的层列表。
 *
 * <p>取代单槽位 {@code InteractionManager.overlay} 字段，以结构化列表支持多浮层共存
 * （例如开在另一层之上的 {@code SelectDropdown}、悬停于 {@code ContextMenu} 内的 {@code Tooltip}）。
 * 多根共存（后加入的在上）。
 *
 * <p>视觉堆叠顺序：后加入的元素渲染在上。输入分发与命中测试按最上层优先
 * （列表逆序）访问。加入顺序=由下到上。
 *
 * <p>渲染（根按加入顺序、父先子后）与输入分发（最上层优先）互逆。
 */
public final class OverlayTree {
    private static final Logger LOG = LoggerFactory.getLogger("FluentUI/OverlayTree");

    /**
     * 一层浮层内记录：
     * <ul>
     * <li>{@code root} 层根元素，浮层的根元素；</li>
     * <li>{@code dismissOnOutsideClick} 外部点击是否关闭；</li>
     * <li>{@code parent} 该层的父层，创建时可选赋值，可用于避免子层操作关闭浮层。</li>
     * </ul>
     */
    public record Layer(Element<?> root, boolean dismissOnOutsideClick, Element<?> parent) {}

    /** 浮层层列表，按加入顺序排列。最后一项为最上层。 */
    private final List<Layer> layers = new ArrayList<>();

    // ──────────────── 增删 (add / remove) ────────────────

    /**
     * 把 {@code child}（本次要作为新层根元素的浮层）推入浮层列表。重推等于移动：先摘除再挂上去。
     * 层根的树内父指针恒为 {@code null}，浮层元素不作为任何布局容器的子节点，
     * 所以永不被裁剪、{@code getAbsoluteX} 也不上溯。
     * <p>{@code dismissOnOutsideClick} 是外部点击是否关闭该浮层的标志。
     */
    public Layer push(Element<?> child, boolean dismissOnOutsideClick) {
        return push(null, child, dismissOnOutsideClick);
    }

    /**
     * 把 {@code child}（本次要作为新层根元素的浮层）推入浮层列表。重推等于移动：先摘除再挂上去。
     * 层根的树内父指针恒为 {@code null}，浮层元素不作为任何布局容器的子节点，
     * 所以永不被裁剪、{@code getAbsoluteX} 也不上溯。
     * <p>{@code parent} 是层间父子里的直系父浮层元素（不是最外层父，也不是隔层父），
     * 只用于关闭路径：关父层时连带关闭本层，点中本层时不关闭父层。
     * <p>{@code dismissOnOutsideClick} 是外部点击是否关闭该浮层的标志。
     */
    public Layer push(Element<?> parent, Element<?> child, boolean dismissOnOutsideClick) {
        // 1. 先摘除：树内父节点非空就从树内父容器的孩子里摘，为空就在层列表里按同一性扫出来摘掉。
        if (child.getParent() != null) {
            child.getParent().children().remove(child);
            child.setParent(null);
        } else {
            layers.removeIf(layer -> layer.root() == child);
        }

        // 2. 挂上去：树内父指针保持 null，只记层间父子。
        Layer layer = new Layer(child, dismissOnOutsideClick, parent);
        layers.add(layer);
        LOG.debug("[push] {} → layers={}", child.getClass().getSimpleName(), layers.size());
        return layer;
    }

    /**
     * 从层列表移除 {@code child}（要摘掉的层根元素）。若 {@code child} 存在返回 {@code true}。
     * 移除后 {@code child} 的树内父指针置空。
     * <p>若该层还有层间意义上的子孙层，也会被一并摘除；只影响该层旗下的子树，
     * 不会向上影响它的父层。
     */
    public boolean remove(Element<?> child) {
        boolean removed = false;
        // collect descendants that must be removed too
        List<Element<?>> toRemove = new ArrayList<>();
        toRemove.add(child);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Layer layer : layers) {
                Element<?> root = layer.root();
                if (toRemove.contains(root)) continue;
                Element<?> parent = layer.parent();
                if (parent != null && toRemove.contains(parent)) {
                    toRemove.add(root);
                    changed = true;
                }
            }
        }
        for (Element<?> r : toRemove) {
            removed |= layers.removeIf(layer -> layer.root() == r);
            r.setParent(null);
        }
        if (removed) {
            LOG.debug("[remove] {} → layers={}", child.getClass().getSimpleName(), layers.size());
        }
        return removed;
    }

    // ──────────────── 查询 (queries) ────────────────

    /** 若层列表为空返回 true。 */
    public boolean isEmpty() {
        return layers.isEmpty();
    }

    /** 层数量。 */
    public int size() {
        return layers.size();
    }

    /** 层根元素按加入顺序的快照（新的可变列表）。 */
    public List<Element<?>> roots() {
        List<Element<?>> result = new ArrayList<>(layers.size());
        for (Layer layer : layers) result.add(layer.root());
        return result;
    }

    /** 层记录列表的快照（新的可变列表）。 */
    public List<Layer> layers() {
        return new ArrayList<>(layers);
    }

    /** {@code element} 当前是否在层列表中（同一性比较）。 */
    public boolean contains(Element<?> el) {
        for (Layer layer : layers) {
            if (layer.root() == el) return true;
        }
        return false;
    }

    /** 按层根元素查回层记录；不存在返回 {@code null}（同一性比较）。 */
    public Layer findLayer(Element<?> root) {
        for (Layer layer : layers) {
            if (layer.root() == root) return layer;
        }
        return null;
    }

    // ──────────────── 遍历 (traversals) ────────────────

    /**
     * 返回在 {@code (mx, my)} 处命中测试成功的最上层元素；若无元素被命中则返回 {@code null}。
     * 判定走 L1 裁剪命中（{@code Element.isHit}），与根树分发同标准。
     * 命中浮层根后继续下钻到其子树最深处（{@code Hit.topmost}）。
     */
    public Element<?> topmostInTree(double mx, double my) {
        for (int i = layers.size() - 1; i >= 0; i--) {
            Element<?> el = layers.get(i).root();
            if (!el.isVisible()) continue;
            if (!Element.isHit(el, mx, my)) continue;
            Element<?> inner = el instanceof LayoutElement<?> l ? Hit.topmost(l, mx, my) : null;
            return inner != null ? inner : el;
        }
        return null;
    }

    /**
     * 若给定元素在 {@code (mx, my)} 处被命中返回 {@code true}。
     * 用于判断“该浮层是否仍在光标下”，供外部关闭路由使用。判定走 L1 裁剪命中。
     */
    public boolean subtreeHit(Element<?> el, double mx, double my) {
        if (!el.isVisible()) return false;
        return Element.isHit(el, mx, my);
    }

}
