package xianfish.fluentui.client.ui.interaction;

import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.LayoutElement;

import java.util.ArrayList;
import java.util.List;

/**
 * H3 单树命中搜索：纯静态函数，只认识 {@link Element} 及其子类，不依赖
 * {@code InteractionManager} 实例（OverlayTree 的"独立于 IM"不变量经由本类保持——
 * 浮层下钻调这里，OverlayTree 不内联镜像 {@code topmostHit}）。
 *
 * <p>三方法共享同一候选集（加入顺序＋可见／暂停过滤），排序＋过滤只写一份：
 * {@link #hitOrder}（ breadth ：本层全部命中，自上而下）、{@link #hitPath}
 * （depth：根→最深路径）、{@link #topmost}（路径末端）。
 */
public final class Hit {
    private Hit() {}

    /**
     * 候选集：子元素按加入顺序排列，调用方自上而下（逆序）消费。
     * 不可见的子树整枝跳过——与分发"看不见、点不着"一致。
     */
    private static List<Element<?>> candidates(LayoutElement<?> layoutElement) {
        if (layoutElement == null || !layoutElement.isVisible()) return List.of();
        List<Element<?>> sorted = new ArrayList<>(layoutElement.children());
        sorted.removeIf(c -> !c.isVisible());
        return sorted;
    }

    /**
     * 本层全部命中（自上而下）：供 {@code LayoutElement} 分发逐个试消费。
     * 语义同原 {@code InteractionManager.hitOrder}。
     */
    public static List<Element<?>> hitOrder(LayoutElement<?> layoutElement, double mx, double my) {
        List<Element<?>> out = new ArrayList<>();
        List<Element<?>> all = candidates(layoutElement);
        for (int i = all.size() - 1; i >= 0; i--) {
            Element<?> c = all.get(i);
            if (Element.isHit(c, mx, my)) out.add(c);
        }
        return out;
    }

    /**
     * 根→最深命中路径：一次遍历同时回答"谁最上"与"经过谁"。
     * 命中布局则继续下钻；布局内无更深命中则路径止于该布局——
     * 与原 {@code InteractionManager.topmostHit} 逐层语义一致。
     */
    public static List<Element<?>> hitPath(LayoutElement<?> layoutElement, double mx, double my) {
        List<Element<?>> path = new ArrayList<>();
        LayoutElement<?> cur = layoutElement;
        while (cur != null && cur.isVisible()) {
            Element<?> next = null;
            List<Element<?>> all = candidates(cur);
            for (int i = all.size() - 1; i >= 0; i--) {
                Element<?> c = all.get(i);
                if (Element.isHit(c, mx, my)) { next = c; break; }
            }
            if (next == null) break;
            path.add(next);
            cur = next instanceof LayoutElement<?> l ? l : null;
        }
        return path;
    }

    /** 路径末端：最深命中，无则 {@code null}。语义同原 {@code InteractionManager.topmostHit}。 */
    public static Element<?> topmost(LayoutElement<?> layoutElement, double mx, double my) {
        List<Element<?>> path = hitPath(layoutElement, mx, my);
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    /**
     * 最深的、矩形含点的布局容器。
     *
     * <p>{@link #topmost} 在没命中任何子元素时返回 {@code null}，那正是「点在容器空白处」。
     * 本方法给出这种情况下的分发起点：空白处归容器自己，按末位优先往下钻，返回含点的最深布局容器。</p>
     *
     * @param root 根布局容器
     * @param mx   屏幕横坐标
     * @param my   屏幕纵坐标
     * @return 含点的最深布局容器；点不在 {@code root} 矩形内时返回 {@code null}
     */
    public static LayoutElement<?> deepestLayoutAt(LayoutElement<?> root, double mx, double my) {
        if (root == null || !root.isVisible()) return null;
        if (!Element.isHit(root, mx, my)) return null;
        List<Element<?>> all = candidates(root);
        for (int i = all.size() - 1; i >= 0; i--) {
            if (all.get(i) instanceof LayoutElement<?> l) {
                LayoutElement<?> deeper = deepestLayoutAt(l, mx, my);
                if (deeper != null) return deeper;
            }
        }
        return root;
    }
}
