package xianfish.fluentui.client.ui.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2fc;

/**
 * 顶点写入回调：拿到绘制期才存在的 {@link VertexConsumer} 与提交时快照的矩阵，按图案自己吐顶点。
 *
 * <p>为什么做成回调而不是把形状编码进状态类：渐变种类是无穷的（线性、径向、角向、锥形、
 * 网格采样…），能枚举的只有「一个矩形区域 + 四个顶点里每点的颜色」之外的自由度。把形状留给
 * 调用方，状态类就退化成一份「快照 + 提交」，任何图案都能塞进来。</p>
 *
 * <p>约定：坐标写在矩阵变换<b>之前</b>，用 {@link VertexConsumer#addVertexWith2DPose} 一步到位，
 * 不要自己乘矩阵 —— 那正是 {@code pose} 参数存在的理由。</p>
 */
@FunctionalInterface
public interface VertexPainter {

	/**
	 * 写入顶点。会被 {@code GuiRenderer} 在渲染阶段调用，届时 {@code vertexConsumer} 才可用。
	 *
	 * @param vertexConsumer 当前绘制批次，消费者，按 QUADS 模式成组写入
	 * @param pose           提交那一刻的 GUI 矩阵快照，渲染阶段矩阵早已推进，只能用它还原
	 */
	void paint(VertexConsumer vertexConsumer, Matrix3x2fc pose);
}