package xianfish.fluentui.testmod;

import static xianfish.fluentui.client.ui.interaction.input.Modifiers.ALT;
import static xianfish.fluentui.client.ui.interaction.input.Modifiers.CONTROL;
import static xianfish.fluentui.client.ui.interaction.input.Modifiers.NONE;
import static xianfish.fluentui.client.ui.interaction.input.Modifiers.SHIFT;
import static xianfish.fluentui.client.ui.interaction.input.Modifiers.SUPER;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import xianfish.fluentui.client.FluentScreen;
import xianfish.fluentui.client.ui.component.AutoCompleteBox;
import xianfish.fluentui.client.ui.component.Button;
import xianfish.fluentui.client.ui.component.ContextMenu;
import xianfish.fluentui.client.ui.component.ItemList;
import xianfish.fluentui.client.ui.component.ItemView;
import xianfish.fluentui.client.ui.component.ListView;
import xianfish.fluentui.client.ui.component.MessageFlyout;
import xianfish.fluentui.client.ui.component.ProgressBar;
import xianfish.fluentui.client.ui.component.SelectBox;
import xianfish.fluentui.client.ui.component.SelectDropdown;
import xianfish.fluentui.client.ui.component.Slider;
import xianfish.fluentui.client.ui.component.Stepper;
import xianfish.fluentui.client.ui.component.TextBox;
import xianfish.fluentui.client.ui.component.Toggle;
import xianfish.fluentui.client.ui.component.Tooltip;
import xianfish.fluentui.client.ui.element.Element;
import xianfish.fluentui.client.ui.element.layout.EntryRow;
import xianfish.fluentui.client.ui.element.layout.Orientation;
import xianfish.fluentui.client.ui.element.layout.OverflowMode;
import xianfish.fluentui.client.ui.element.layout.StackPanel;
import xianfish.fluentui.client.ui.interaction.InteractionManager;
import xianfish.fluentui.client.ui.interaction.input.CharTypedEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyPressedEvent;
import xianfish.fluentui.client.ui.interaction.input.KeyReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseDraggedEvent;
import xianfish.fluentui.client.ui.interaction.input.MousePressedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseReleasedEvent;
import xianfish.fluentui.client.ui.interaction.input.MouseScrolledEvent;
import xianfish.fluentui.client.ui.interaction.input.PreeditUpdatedEvent;
import xianfish.fluentui.client.ui.overlay.OverlayManager;
import xianfish.fluentui.client.ui.tick.UiTickDriver;
import java.util.List;

/**
 * DevGallery：一行一个组件的展示页，用于手工核对 FluentUI 的内置组件。
 *
 * <p>布局是一条竖向滚动的 {@link StackPanel}（{@code SCROLL} + 滚动条），每个组件占一行
 * {@link EntryRow}：左列是自制的最小文字元素，右列是组件本体。{@code EntryRow} 的行高按
 * 左右子元素取最大值，所以 22 高的 Stepper 与 9×9 的 SelectBox 能共处同一列。</p>
 *
 * <p>输入转发要跨过一层类型差：Minecraft 侧是 {@link MouseButtonEvent} /
 * {@link KeyEvent} / {@link CharacterEvent} / {@link PreeditEvent}，框架侧是自带的事件
 * record，所以每个方法里都得现造一个（见 {@link #toMods(int)} 的修饰键换算）。</p>
 */
public class DevGalleryScreen extends FluentScreen {

	private static final int ROW_W = 380;
	private static final int LABEL_W = 100;

	private StackPanel root;
	private OverlayManager overlays;
	private InteractionManager interactionManager;
	private final UiTickDriver uiTickDriver = new UiTickDriver();

	// 这几个控件要在回调里读自己的状态，所以留字段。
	private Toggle toggle;
	private SelectBox checkbox;
	private Slider slider;
	private Stepper stepper, percentStepper;
	private SelectDropdown dropdown;
	private ItemList<String> itemList;
	private Button menuBtn, flyoutBtn;

	public DevGalleryScreen() {
		super(Component.literal("FluentUI DevGallery"));
	}

	@Override
	protected void init() {
		root = new StackPanel(Orientation.VERTICAL)
			.topLeft(12, 12)
			.size(width - 24, height - 24)
			.bgColor(0x00000000)
			.spacing(6)
			.overflow(OverflowMode.SCROLL)
			.scrollbar(true);

		buildRows();

		overlays = new OverlayManager();
		interactionManager = new InteractionManager(root, overlays);
		InteractionManager.active = interactionManager;
	}

	private void buildRows() {
		row("Button", new Button("点我", 140, 20)
			.font(font)
			.action(() -> flyout("Button 被点了")));

		row("Toggle", toggle = new Toggle(0, 0)
			.on(false)
			.onChange(() -> flyout("Toggle → " + toggle.isOn())));

		row("SelectBox", checkbox = new SelectBox()
			.onClick(() -> flyout("SelectBox → " + checkbox.isSelected())));

		row("Slider", slider = new Slider(150)
			.value(0.4f)
			.steps(11)
			.tooltip("悬停出现的提示")
			.onChanged(v -> flyout("Slider → " + String.format("%.2f", v))));

		row("Stepper", stepper = new Stepper(140)
			.mode(Stepper.Mode.FREE)
			.step(1)
			.value(5)
			.allowInput(true)
			.suffix(" 单位")
			.font(font)
			.onChanged(v -> flyout("Stepper → " + v)));

		row("Stepper%", percentStepper = new Stepper(140)
			.mode(Stepper.Mode.PERCENT)
			.value(42)
			.font(font)
			.onChanged(v -> flyout("Percent → " + v)));

		row("TextBox", new TextBox(200, 20)
			.placeholder("输入点什么…")
			.maxLength(64)
			.font(font)
			.onChanged(s -> { })
			.onEnter(() -> flyout("TextBox 回车")));

		row("AutoComplete", new AutoCompleteBox(200, 20)
			.dataset(List.of("apple", "apricot", "banana", "cherry", "grape"))
			.placeholder("试着打 a…")
			.font(font)
			.onSelected(s -> flyout("选中 " + s)));

		row("SelectDropdown", dropdown = new SelectDropdown(200)
			.options(List.of("红", "绿", "蓝", "黄"))
			.selected(0)
			.font(font)
			.onChanged(i -> flyout("下拉选 " + i)));

		row("ProgressBar", new ProgressBar(200)
			.progress(0.66f));

		row("ListView", new ListView<String>(200, 64)
			.itemHeight(20)
			.items(List.of("第一行", "第二行", "第三行", "第四行"))
			.onSelected(i -> flyout("List 选 " + i)));

		row("ItemList", itemList = new ItemList<String>(200, 64)
			.itemHeight(20)
			.items(List.of("甲", "乙", "丙"))
			.renderer((item, cell) -> cell.add(new GalleryLabel(item))));

		row("ItemView", new ItemView(ItemStack.EMPTY)
			.decorations(false));

		// Tooltip 挂按钮的悬停，跟着光标走；下面两个是浮层型组件，用按钮触发。
		// Tooltip 还得先 .font(font)，量宽度和折行都靠它，没有字体量出来的提示是空的。
		row("Tooltip", new Button("悬停看提示", 140, 20)
			.font(font)
			.tooltip("Tooltip.show(x, y, text)"));

		row("ContextMenu", menuBtn = new Button("在此打开", 140, 20)
			.font(font)
			.action(this::openContextMenu));

		row("MessageFlyout", flyoutBtn = new Button("弹一条", 140, 20)
			.font(font)
			.action(() -> flyout("MessageFlyout：1.2 秒后自动收起")));
	}

	/** 在按钮下方打开一份右键菜单：含可用项、禁用项、点了不关的项三种形态。 */
	private void openContextMenu() {
		if (menuBtn == null) return;
		// items(...) 会整体替换，所以四种形态一次给全；addItem 只支持到三参（不含 closeOnClick）。
		ContextMenu menu = new ContextMenu().font(font).items(List.of(
			new ContextMenu.MenuItem("可用项", () -> flyout("点了可用项"), true),
			new ContextMenu.MenuItem("禁用项", () -> flyout("不该出现"), false),
			new ContextMenu.MenuItem("点了不关", () -> flyout("closeOnClick=false"), true, false)
		));
		menu.show(menuBtn.getAbsoluteX(), menuBtn.getAbsoluteY() + 22);
	}

	/** 一条 EntryRow：左列文字，右列组件本体。 */
	private void row(String label, Element<?> control) {
		EntryRow entry = new EntryRow(ROW_W, LABEL_W)
			.left(new GalleryLabel(label).font(font));
		if (control != null) {
			// 右列控件统一在这里拿字体：ListView 一类没有字体的控件会整块不渲染。
			entry.right(control.font(font));
		}
		root.add(entry);
	}

	/** 弹出一次消息条；消息条自己经 InteractionManager.active 推入浮层表。 */
	private void flyout(String msg) {
		new MessageFlyout()
			.type(0xFF3B82F6)
			.message(msg)
			.autoDismiss(1200)
			.show(msg);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
		Tooltip.cursor(mx, my, width, height);
		uiTickDriver.onRenderFrame(interactionManager);
		root.extractRenderState(e, mx, my, d);
		overlays.renderOverlay(e, mx, my, d);
	}

	// --- 输入转发：MC 事件对象 → 框架事件对象，七个共八条（鼠标按下在 MC 侧叫 mouseClicked） ---
	@Override public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		return interactionManager != null && interactionManager.mousePressed(
			new MousePressedEvent(e.x(), e.y(), e.button(), toMods(e.modifiers())));
	}
	@Override public boolean mouseReleased(MouseButtonEvent e) {
		return interactionManager != null && interactionManager.mouseReleased(
			new MouseReleasedEvent(e.x(), e.y(), e.button(), toMods(e.modifiers())));
	}
	@Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		return interactionManager != null && interactionManager.mouseDragged(
			new MouseDraggedEvent(e.x(), e.y(), e.button(), dx, dy, toMods(e.modifiers())));
	}
	@Override public boolean mouseScrolled(double mx, double my, double h, double v) {
		return interactionManager != null && interactionManager.mouseScrolled(
			new MouseScrolledEvent(mx, my, h, v, NONE));
	}
	@Override public boolean keyPressed(KeyEvent e) {
		return (interactionManager != null && interactionManager.keyPressed(
			new KeyPressedEvent(e.key(), e.scancode(), toMods(e.modifiers())))) || super.keyPressed(e);
	}
	@Override public boolean keyReleased(KeyEvent e) {
		return (interactionManager != null && interactionManager.keyReleased(
			new KeyReleasedEvent(e.key(), e.scancode(), toMods(e.modifiers())))) || super.keyReleased(e);
	}
	@Override public boolean charTyped(CharacterEvent e) {
		return (interactionManager != null && interactionManager.charTyped(
			new CharTypedEvent(e.codepoint()))) || super.charTyped(e);
	}
	@Override public boolean preeditUpdated(PreeditEvent e) {
		return (interactionManager != null && interactionManager.preeditUpdated(
			new PreeditUpdatedEvent(e.fullText(), e.caretPosition(), e.blocks(), e.focusedBlock())))
			|| super.preeditUpdated(e);
	}

	/** GLFW 修饰位 → 框架 {@code Modifiers} 位（两边取值一致，显式写出来不靠巧合）。 */
	private static int toMods(int glfw) {
		int m = NONE;
		if ((glfw & 0x1) != 0) m |= SHIFT;
		if ((glfw & 0x2) != 0) m |= CONTROL;
		if ((glfw & 0x4) != 0) m |= ALT;
		if ((glfw & 0x8) != 0) m |= SUPER;
		return m;
	}

	@Override public boolean isPauseScreen() {
		return false;
	}

	/** 关屏时把全局活动管理器摘掉，别让它指着这个已经没人用的实例。 */
	@Override public void removed() {
		if (interactionManager != null && InteractionManager.active == interactionManager) {
			InteractionManager.active = null;
		}
	}

	/** 最小文字元素：项目没有内置 Label，这是文档里 Text 配方的最小形状。 */
	static final class GalleryLabel extends Element<GalleryLabel> {
		private final Component text;

		GalleryLabel(String text) {
			super(100, 10);
			this.text = Component.literal(text);
		}

		@Override
		protected void measure() { }

		@Override
		public void extractRenderState(GuiGraphicsExtractor e, int mx, int my, float d) {
			if (!visible || font == null) return;
			e.text(font, text, getAbsoluteX(), getAbsoluteY(), 0xFFCCCCCC);
		}
	}
}
