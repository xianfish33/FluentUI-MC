package xianfish.fluentui.testmod.component;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.render.CustomDrawUtil;

/**
 * 二维渐变控件：矩形内横纵同时渐变，四个角各一个颜色。
 *
 * <p>这层封装只做两件事：把元素自己的矩形翻译成屏幕绝对坐标，再交给 {@link CustomDrawUtil}
 * 提交自绘图元。渐变本身的插值、顶点顺序、排序契约都在工具层，控件不重复那些口径。</p>
 *
 * <p>渐变覆盖整个控件矩形，没有内边距。要看到内边距效果就把控件本身调大、渐变区域用
 * {@link #inset} 收进去。</p>
 */
public class GradientBox extends Element<GradientBox> {

	private int topLeft = 0xFF1D4ED8;
	private int topRight = 0xFF3B82F6;
	private int bottomLeft = 0xFF0F172A;
	private int bottomRight = 0xFF60A5FA;

	private int inset;

	public GradientBox(final int w, final int h) {
		super(0, 0, w, h);
	}

	/**
	 * 一次设齐四个角的颜色。
	 *
	 * @param topLeft 左上角
	 * @param topRight 右上角
	 * @param bottomLeft 左下角
	 * @param bottomRight 右下角
	 */
	public GradientBox corners(
			final int topLeft, final int topRight,
			final int bottomLeft, final int bottomRight
	) {
		this.topLeft = topLeft;
		this.topRight = topRight;
		this.bottomLeft = bottomLeft;
		this.bottomRight = bottomRight;
		markDirty();
		return this;
	}

	/** 只改上排两个角，下排保持原样。 */
	public GradientBox top(final int left, final int right) {
		this.topLeft = left;
		this.topRight = right;
		markDirty();
		return this;
	}

	/** 只改下排两个角，上排保持原样。 */
	public GradientBox bottom(final int left, final int right) {
		this.bottomLeft = left;
		this.bottomRight = right;
		markDirty();
		return this;
	}

	/** 四边内缩的像素数，渐变区域因此小于控件矩形。内缩后区域退化成线或点时不绘制。 */
	public GradientBox inset(final int pixels) {
		this.inset = Math.max(0, pixels);
		markDirty();
		return this;
	}

	@Override protected void measure() {}

	@Override
	public void extractRenderState(final GuiGraphicsExtractor e, final int mx, final int my, final float d) {
		if (!visible) return;
		layout();
		int left = getAbsoluteX() + inset;
		int top = getAbsoluteY() + inset;
		int right = getAbsoluteX() + width - inset;
		int bottom = getAbsoluteY() + height - inset;
		CustomDrawUtil.drawBilinear(e, left, top, right, bottom,
				topLeft, topRight, bottomLeft, bottomRight);
	}
}