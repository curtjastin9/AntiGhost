package com.litantighost;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side (also runs inside singleplayer).
 * After a player places a block, uses glowstone or clicks with a totem, the real block state of the
 * clicked block and of the spot next to it is sent back to that player at the end of the tick.
 * The client then always ends up with exactly what the server has, so nothing can stay ghosted.
 */
public class LitAntiGhost implements ModInitializer {

	private record Resync(ServerPlayer player, Level level, BlockPos clicked, BlockPos target) {
	}

	private static final List<Resync> PENDING = new ArrayList<>();

	@Override
	public void onInitialize() {
		AntiGhostConfig.load();

		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (AntiGhostConfig.enabled && player instanceof ServerPlayer serverPlayer) {
				ItemStack stack = player.getItemInHand(hand);
				if (stack.getItem() instanceof BlockItem
					|| stack.is(Items.GLOWSTONE)
					|| stack.is(Items.TOTEM_OF_UNDYING)) {
					BlockPos clicked = hit.getBlockPos();
					PENDING.add(new Resync(serverPlayer, world, clicked, clicked.relative(hit.getDirection())));
				}
			}
			return InteractionResult.PASS;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (PENDING.isEmpty()) {
				return;
			}
			List<Resync> work = new ArrayList<>(PENDING);
			PENDING.clear();
			for (Resync r : work) {
				try {
					r.player().connection.send(new ClientboundBlockUpdatePacket(r.level(), r.clicked()));
					if (!r.target().equals(r.clicked())) {
						r.player().connection.send(new ClientboundBlockUpdatePacket(r.level(), r.target()));
					}
				} catch (RuntimeException ignored) {
					// player left during the tick
				}
			}
		});
	}
}
