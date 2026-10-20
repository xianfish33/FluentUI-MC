package xianfish.fluentui.client.ui.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2fc;

/**
 * 把图案编译成 {@link VertexPainter}。
 *
 * <p>自绘最先用到的图案是渐变，所以这里给一个横纵同时渐变的二维插值。四个角各给一个颜色，横纵两向同时变化。</p>
 *
 * <p>默认交给光栅器插值，CPU只提交 4 个顶点</p>
 *
 * <p>需要数学意义上严格的双线性时用 {@link #bilinearMeshed}，它在 CPU 上把颜色算好再铺网格，顶点数是前者的平方。</p>
 *
 * <p>每格吐四个顶点，顺序形同原版的 {@code ColoredRectangleRenderState}：
 * {@code (x0,y0) (x0,y1) (x1,y1) (x1,y0)}。因为 {@code RenderPipelines.GUI} 的顶点模式是
 * {@code QUADS}，顶点数不对会被当成坏数据丢掉。</p>
 */
public final class GradientPainter {

	private GradientPainter() {}

	/** 矩形退化成线或点时用这个，避免归一化除零算出 NaN 坐标。 */
	private static final VertexPainter EMPTY = (vc, pose) -> {};

	/** {@link #bilinearMeshed} 的默认网格密度：{@code 8 × 8} 共 64 格 256 个顶点。 */
	public static final int DEFAULT_CELLS = 8;

	/**
	 * 横纵同时渐变的二维插值，交给光栅器插值，整段只有四个顶点。
	 *
	 * <p>QUADS 把这四个顶点拆成两个三角形，两个三角形共用一条对角线。共用边的两个端点是同一个顶点，
	 * 颜色相同，所以两片在接缝处取到的值一样，看不出折痕。</p>
	 *
	 * <p>四个角的颜色按屏幕方位给：左上、右上、左下、右下。单向渐变不必另写方法，
	 * 横向就把上下两行取成同一个值，纵向就把左右两列取成同一个值。</p>
	 */
	public static VertexPainter bilinear(
			final int x0, final int y0, final int x1, final int y1,
			final int topLeft, final int topRight,
			final int bottomLeft, final int bottomRight
	) {
		if (x1 <= x0 || y1 <= y0) return EMPTY;

		return (vc, pose) -> {
			vc.addVertexWith2DPose(pose, x0, y0).setColor(topLeft);
			vc.addVertexWith2DPose(pose, x0, y1).setColor(bottomLeft);
			vc.addVertexWith2DPose(pose, x1, y1).setColor(bottomRight);
			vc.addVertexWith2DPose(pose, x1, y0).setColor(topRight);
		};
	}

	/**
	 * 横纵同时渐变的二维插值，颜色在 CPU 上按双线性权重算好，网格密度取 {@link #DEFAULT_CELLS}。
	 *
	 * <p>为什么需要这一条：光栅器只会在三角形内部做重心坐标插值，而双线性含 {@code uv} 交叉项，
	 * 三角形表达不出来。所以四顶点版本严格说是分片仿射，两片的斜率在共用对角线上对不上。
	 * 接缝处连续、看不出折痕，但中点取到的值与数学上的双线性差一些（顶色 0/100/200/255 时，
	 * 中心双线性给 138.75，四顶点版本给 127.5）。需要那个精确值才走这里。</p>
	 */
	public static VertexPainter bilinearMeshed(
			final int x0, final int y0, final int x1, final int y1,
			final int topLeft, final int topRight,
			final int bottomLeft, final int bottomRight
	) {
		return bilinearMeshed(x0, y0, x1, y1, topLeft, topRight, bottomLeft, bottomRight, DEFAULT_CELLS);
	}

	/**
	 * 横纵同时渐变的二维插值，颜色在 CPU 上算好，网格密度由 {@code cells} 指定，横纵两向同值。
	 *
	 * <p>网格坐标用除法而不是累加步长：末格正好落在 1.0，边缘不会因为浮点累积多出一丝。
	 * 相邻两格共用边上算出来的颜色是同一个表达式，所以格与格之间也没有缝。</p>
	 *
	 * @param cells 每方向的格数，小于 1 时按 1 处理
	 */
	public static VertexPainter bilinearMeshed(
			final int x0, final int y0, final int x1, final int y1,
			final int topLeft, final int topRight,
			final int bottomLeft, final int bottomRight,
			final int cells
	) {
		if (x1 <= x0 || y1 <= y0) return EMPTY;

		final int grid = Math.max(1, cells);
		final float spanX = x1 - x0;
		final float spanY = y1 - y0;

		return (vc, pose) -> {
			for (int row = 0; row < grid; row++) {
				final float vTop = row / (float) grid;
				final float vBottom = (row + 1) / (float) grid;
				final float yTop = y0 + spanY * vTop;
				final float yBottom = y0 + spanY * vBottom;

				for (int col = 0; col < grid; col++) {
					final float uLeft = col / (float) grid;
					final float uRight = (col + 1) / (float) grid;
					final float xLeft = x0 + spanX * uLeft;
					final float xRight = x0 + spanX * uRight;

					quad(vc, pose, xLeft, yTop, xRight, yBottom,
							bilinearColor(topLeft, topRight, bottomLeft, bottomRight, uLeft, vTop),
							bilinearColor(topLeft, topRight, bottomLeft, bottomRight, uLeft, vBottom),
							bilinearColor(topLeft, topRight, bottomLeft, bottomRight, uRight, vBottom),
							bilinearColor(topLeft, topRight, bottomLeft, bottomRight, uRight, vTop));
				}
			}
		};
	}

	/** 单格四顶点，顺序与 vanilla 的矩形条目一致，适配 QUADS 顶点模式。 */
	private static void quad(
			final VertexConsumer vc, final Matrix3x2fc pose,
			final float left, final float top, final float right, final float bottom,
			final int colorTopLeft, final int colorBottomLeft,
			final int colorBottomRight, final int colorTopRight
	) {
		vc.addVertexWith2DPose(pose, left, top).setColor(colorTopLeft);
		vc.addVertexWith2DPose(pose, left, bottom).setColor(colorBottomLeft);
		vc.addVertexWith2DPose(pose, right, bottom).setColor(colorBottomRight);
		vc.addVertexWith2DPose(pose, right, top).setColor(colorTopRight);
	}

	/** 双线性权重：四个角的颜色按 {@code (1-u)(1-v)、u(1-v)、(1-u)v、uv} 加权求和。 */
	private static int bilinearColor(
			final int topLeft, final int topRight,
			final int bottomLeft, final int bottomRight,
			final float u, final float v
	) {
		return weighted(
				topLeft, topRight, bottomLeft, bottomRight,
				(1f - u) * (1f - v), u * (1f - v), (1f - u) * v, u * v);
	}

	private static int weighted(
			final int color0, final int color1, final int color2, final int color3,
			final float weight0, final float weight1, final float weight2, final float weight3
	) {
		final int alpha = mix(color0 >>> 24, color1 >>> 24, color2 >>> 24, color3 >>> 24,
				weight0, weight1, weight2, weight3);
		final int red = mix(color0 >> 16 & 0xFF, color1 >> 16 & 0xFF, color2 >> 16 & 0xFF, color3 >> 16 & 0xFF,
				weight0, weight1, weight2, weight3);
		final int green = mix(color0 >> 8 & 0xFF, color1 >> 8 & 0xFF, color2 >> 8 & 0xFF, color3 >> 8 & 0xFF,
				weight0, weight1, weight2, weight3);
		final int blue = mix(color0 & 0xFF, color1 & 0xFF, color2 & 0xFF, color3 & 0xFF,
				weight0, weight1, weight2, weight3);
		return (alpha << 24) | (red << 16) | (green << 8) | blue;
	}

	private static int mix(
			final int a, final int b, final int c, final int d,
			final float weightA, final float weightB, final float weightC, final float weightD
	) {
		return Math.round(a * weightA + b * weightB + c * weightC + d * weightD);
	}
}