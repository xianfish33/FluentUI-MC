package xianfish.fluentui.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * 自绘元素状态：清单里的一条自定义条目，形状由 {@link VertexPainter} 现场写。
 *
 * <p>形态形同原版的 {@code ColoredRectangleRenderState}
 * 差别只有一处：顶点不写死成两色四边形，改为交给 {@link #painter()}。
 *
 * <p>{@code bounds} 是构造时算好的缓存值，不是每次 {@code bounds()} 实时计算</p>
 *
 * <p>顶点为什么能在渲染阶段才写：{@code buildVertices} 拿到的是渲染期的 {@code VertexConsumer}，而矩阵是提交那一刻的快照。
 * 两者都不是提交瞬间能拿到的，所以只能延迟到渲染期再吐顶点。</p>
 */
public record CustomElementRenderState(
		RenderPipeline pipeline,
		TextureSetup textureSetup,
		Matrix3x2fc pose,
		int x0,
		int y0,
		int x1,
		int y1,
		@Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds,
		VertexPainter painter
) implements GuiElementRenderState {

	/**
	 * 常用形态：{@code bounds} 由矩形、矩阵、scissor 三者推出来。
	 *
	 * <p>{@code scissorArea} 为 null 表示没有裁剪；非 null 时 {@code bounds} 收窄到与它的交集，
	 * 这样裁剪区外的部分既不会被排序逻辑当成可见，也不会被后续层级误判成重叠。</p>
	 */
	public CustomElementRenderState(
			final RenderPipeline pipeline,
			final TextureSetup textureSetup,
			final Matrix3x2fc pose,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final @Nullable ScreenRectangle scissorArea,
			final VertexPainter painter
	) {
		this(pipeline, textureSetup, pose, x0, y0, x1, y1, scissorArea,
				getBounds(x0, y0, x1, y1, pose, scissorArea), painter);
	}

	@Override
	public void buildVertices(final VertexConsumer vertexConsumer) {
		this.painter().paint(vertexConsumer, this.pose());
	}

	private static @Nullable ScreenRectangle getBounds(
			final int x0, final int y0, final int x1, final int y1,
			final Matrix3x2fc pose, final @Nullable ScreenRectangle scissorArea
	) {
		ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
		return scissorArea != null ? scissorArea.intersection(bounds) : bounds;
	}
}