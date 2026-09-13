package xianfish.fluentui.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import xianfish.fluentui.client.ui.interaction.InteractionManager;

public abstract class FluentScreen extends Screen {

    protected FluentScreen(Component title) {
        super(title);
    }

    public boolean isTextInputFocused() {
        InteractionManager manager = InteractionManager.active;
        return manager != null && manager.hasFocusedTextInput();
    }

    /**
     * 悬停事件的一级驱动：鼠标移动即刷新悬停归属（enter／exit／move）。
     * 过去悬停只靠 {@code Tooltip.cursor} 轮询间接触发；此处接上后 tooltip 退为纯消费者。
     * （{@code updateHover} 同坐标重复调用为空操作，与 cursor 轮询共存安全。）
     */
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        InteractionManager manager = InteractionManager.active;
        if (manager != null) manager.updateHover(mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (minecraft.level == null) {
            // 非游戏内：与所有菜单页面一致的动态全景背景 + 直接模糊
            extractPanorama(graphics, a);
            extractBlurredBackground(graphics);
        } else {
            // 游戏内：先模糊世界画面，再叠加半透明渐变透出
            extractBlurredBackground(graphics);
        }
    }
}
