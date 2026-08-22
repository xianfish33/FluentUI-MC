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
