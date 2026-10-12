package xianfish.fluentui.client.ui.tick;

import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

/**
 * 独立 UI tick 驱动器：按渲染帧真实耗时插进固定步长，保证 UI 逻辑更新不依赖
 * Minecraft 的 20 TPS 逻辑节拍。每一步都按 {@link TickWalker} 走全 UI 森林，
 * 元素的 tick() 由本类统一驱动，不自行递归。
 */
public final class UiTickDriver {
    private static final double STEP_SECONDS = 1.0 / 120.0;
    private long lastNanos = -1L;
    private double accumulator = 0.0;

    /** 在每次渲染帧调用，把真实时间差累积后，按 120 TPS 步进。*/
    public void onRenderFrame(InteractionManager manager) {
        if (manager == null) return;
        long now = System.nanoTime();
        if (lastNanos < 0) lastNanos = now;
        long deltaNanos = now - lastNanos;
        lastNanos = now;
        double elapsed = deltaNanos / 1_000_000_000.0;
        if (elapsed > 0.25) elapsed = 0.25; // 防止后台长时间停帧后的大回补
        accumulator += elapsed;
        while (accumulator >= STEP_SECONDS) {
            TickWalker.forEachElement(manager.root(), manager.overlays().tree(), Element::tick);
            accumulator -= STEP_SECONDS;
        }
    }
}
