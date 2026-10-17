package xianfish.fluentui.testmod;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;
import xianfish.fluentui.testmod.DevGalleryScreen;

/**
 * FluentUI 手工核对用测试源集（testmod）的客户端入口。
 *
 * <p>这个包不进 {@code src/test}，也不是 Fabric 的 gametest（那是给 CI 冒烟测试用的
 * 自动化源集）；它是 {@code build.gradle} 里自建并挂进类路径分组的独立测试源集。
 * 入口只做一件事：注册一个按键绑定（默认 G，可在按键设置改），按下后打开
 * DevGallery 展示页；组件行为的手工核对全在那页里。</p>
 */
public class FluentUiDevGalleryClient implements ClientModInitializer {

	/** 默认 G；KeyMapping.Category.MISC 自带译文，category 不需要 lang 键。 */
	private static final KeyMapping DEV_GALLERY_KEY = KeyMappingHelper.registerKeyMapping(
		new KeyMapping("key.fluentui.devgallery", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, KeyMapping.Category.MISC));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick() 把积压的按下次数弹掉，while 循环防止一tick连开多屏。
			// 不需要按键屏蔽判断：屏幕打开后按键由 Screen 吃掉，这里天然收不到重复触发。
			while (DEV_GALLERY_KEY.consumeClick()) {
				if (client.screen == null) {
					client.setScreen(new DevGalleryScreen());
				}
			}
		});
	}
}
