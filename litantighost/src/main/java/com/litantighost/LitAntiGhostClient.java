package com.litantighost;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class LitAntiGhostClient implements ClientModInitializer {

	private static KeyMapping toggleKey;
	private static boolean welcomed = false;

	@Override
	public void onInitializeClient() {
		AntiGhostConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath("litantighost", "main")
		);
		// "=" = anti ghost on / off
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.litantighost.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_EQUALS, category));

		ClientTickEvents.END_CLIENT_TICK.register(LitAntiGhostClient::onTick);
	}

	private static void onTick(Minecraft client) {
		AntiGhostState.tick(client);

		if (!welcomed && client.player != null) {
			welcomed = true;
			client.player.displayClientMessage(Component.literal(
				"[LitAntiGhost] loaded.  = on/off"), false);
		}

		while (toggleKey.consumeClick()) {
			AntiGhostConfig.enabled = !AntiGhostConfig.enabled;
			AntiGhostConfig.save();
			if (!AntiGhostConfig.enabled) {
				AntiGhostState.clear();
			}
			if (client.player != null) {
				String text = "[LitAntiGhost] " + (AntiGhostConfig.enabled ? "ON" : "OFF");
				client.player.displayClientMessage(Component.literal(text), false);
				client.player.displayClientMessage(Component.literal(text), true);
			}
		}
	}
}
