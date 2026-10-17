package xianfish.fluentui.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

/**
 * 自绘门面的调用入口：业务代码只用这一层，不必接触清单条目本身。
 *
 * <p>最常用的是横纵同时渐变，给四个角的颜色就够，中间值由光栅器插值：</p>
 * <pre>{@code
 * CustomDrawUtil.drawBilinear(e, x, y, x + w, y + h, topLeft, topRight, bottomLeft, bottomRight);
 * }</pre>
 *
 * <p>图案超出渐变时，退回用 {@link VertexPainter} 自己写顶点。矩形内任意图案都能表达，例如
 * 一个对角分界的双色块：</p>
 * <pre>{@code
 * CustomDrawUtil.draw(e, x, y, x + w, y + h, (vc, pose) -> {
 *     vc.addVertexWith2DPose(pose, x, y).setColor(c0);
 *     vc.addVertexWith2DPose(pose, x, y + h).setColor(c1);
 *     vc.addVertexWith2DPose(pose, x + w, y + h).setColor(c1);
 *     vc.addVertexWith2DPose(pose, x + w, y).setColor(c0);
 * });
 * }</pre>
 *
 * <p>这是 GUI 管线（无纹理、QUADS、顶点色混合）。要贴图就换带管线的重载，配
 * {@link TextureSetup#singleTexture} 并在 painter 里补 {@code setUv}。</p>
 */
public final class CustomDrawUtil {

	private CustomDrawUtil() {}

	/**
	 * 走 GUI 管线画一段自绘图元，矩形为 {@code [x0, x1) × [y0, y1)}。
	 *
	 * <p>坐标写在矩阵变换之前，跟 vanilla 的 {@code fill} 同一套口径，矩阵由框架快照。</p>
	 */
	public static void draw(
			final GuiGraphicsExtractor e,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final VertexPainter painter
	) {
		draw(e, RenderPipelines.GUI, TextureSetup.noTexture(), x0, y0, x1, y1, painter);
	}

	/** 指定管线与纹理设置的通用形态：贴图、光照等非默认混合都能从这里走。 */
	public static void draw(
			final GuiGraphicsExtractor e,
			final RenderPipeline pipeline,
			final TextureSetup textureSetup,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final VertexPainter painter
	) {
		if (e == null || painter == null) return;

		// pose 存的是快照而非活引用：buildVertices 要到 GuiRenderer 阶段才跑，
		// 那时 extractor 的矩阵栈早变了，所以这里复制一份构造时的姿态。
		e.guiRenderState.addGuiElement(new CustomElementRenderState(
				pipeline,
				textureSetup,
				new Matrix3x2f(e.pose()),
				x0,
				y0,
				x1,
				y1,
				e.scissorStack.peek(),
				painter
		));
	}

	/**
	 * 画一段横纵同时渐变的矩形，整段四个顶点，颜色由光栅器插值。
	 *
	 * <p>四个角的颜色按屏幕方位给：左上、右上、左下、右下。单向渐变不必另写方法，
	 * 横向就把上下两行取成同一个值，纵向就把左右两列取成同一个值。</p>
	 */
	public static void drawBilinear(
			final GuiGraphicsExtractor e,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final int topLeft,
			final int topRight,
			final int bottomLeft,
			final int bottomRight
	) {
		drawBilinear(e, RenderPipelines.GUI, TextureSetup.noTexture(), x0, y0, x1, y1,
				topLeft, topRight, bottomLeft, bottomRight);
	}

	/** {@link #drawBilinear} 指定管线与纹理设置的形态。 */
	public static void drawBilinear(
			final GuiGraphicsExtractor e,
			final RenderPipeline pipeline,
			final TextureSetup textureSetup,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final int topLeft,
			final int topRight,
			final int bottomLeft,
			final int bottomRight
	) {
		draw(e, pipeline, textureSetup, x0, y0, x1, y1,
				GradientPainter.bilinear(x0, y0, x1, y1, topLeft, topRight, bottomLeft, bottomRight));
	}

	/**
	 * 画一段横纵同时渐变的矩形，颜色在 CPU 上按双线性权重算好再铺网格。
	 *
	 * <p>顶点数量是四顶点版本的平方：{@code cells} 取默认的 8 时就是 64 格 256 个顶点。
	 * 一般情况下用四顶点那一枚就够，这条留给需要严格双线性值的场合。</p>
	 */
	public static void drawBilinearMeshed(
			final GuiGraphicsExtractor e,
			final int x0,
			final int y0,
			final int x1,
			final int y1,
			final int topLeft,
			final int topRight,
			final int bottomLeft,
			final int bottomRight,
			final int cells
	) {
		draw(e, RenderPipelines.GUI, TextureSetup.noTexture(), x0, y0, x1, y1,
				GradientPainter.bilinearMeshed(
						x0, y0, x1, y1, topLeft, topRight, bottomLeft, bottomRight, cells));
	}
}