package dev.villageheart.client.guide;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.guide.GuideTask;
import dev.villageheart.core.heart.HeartTexts;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/** The next three guide tasks, as plain text on the left. H collapses it. */
public final class GuideHud {
	private static final int SHOW = 3;
	private static List<String> tasks = List.of();
	private static boolean open = true;
	private static KeyMapping key;

	private GuideHud() {}

	public static void register() {
		key = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.villageheart.tasks", InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_H, KeyMapping.Category.MISC));
		HudElementRegistry.attachElementAfter(VanillaHudElements.BOSS_BAR,
				Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "tasks"), GuideHud::render);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.consumeClick()) open = !open;
		});
	}

	public static void setTasks(List<String> ids) { tasks = List.copyOf(ids); }

	public static List<String> tasks() { return tasks; }

	public static boolean open() { return open; }

	private static void render(GuiGraphics g, DeltaTracker tick) {
		if (tasks.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.screen != null || mc.player == null) return;
		var font = mc.font;
		int x = 6;
		int y = 6;
		int n = open ? Math.min(SHOW, tasks.size()) : 0;
		int widest = font.width(Component.translatable(HeartTexts.key("guide.tasks")));
		for (int i = 0; i < n; i++) {
			String title = GuideTask.byId(tasks.get(i)).map(GuideTask::title).orElse(tasks.get(i));
			widest = Math.max(widest, font.width(title));
		}
		g.fill(2, 2, x + widest + 6, y + 12 + n * 10 + 2, 0x90000000);
		g.drawString(font, Component.translatable(HeartTexts.key("guide.tasks")), x, y, 0xFFFFFFFF, false);
		for (int i = 0; i < n; i++) {
			String title = GuideTask.byId(tasks.get(i)).map(GuideTask::title).orElse(tasks.get(i));
			g.drawString(font, title, x, y + 12 + i * 10, 0xFFFFFFFF, false);
		}
	}
}
