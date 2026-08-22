package xianfish.fluentui.client.ui.element;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface UIRenderable {
    void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float delta);
}
