package com.litantighost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client side bookkeeping.
 *
 * 1. Every right click on a block remembers the block states before the click. If the click changed
 *    the clicked block or the spot next to it (a placement, a glowstone charge, a respawn anchor change),
 *    the change is "pending" until the server sends a block update for that spot.
 * 2. A pending change the server never confirmed within the wait time is rolled back, so it cannot stay ghosted.
 * 3. Placements the server would refuse (something stands in the spot, outside the world) are not sent at all.
 */
public final class AntiGhostState {

	private record Pending(BlockState previous, BlockState predicted, long placedAt) {
	}

	private static final Map<BlockPos, Pending> PENDING = new ConcurrentHashMap<>();

	private static final BlockPos[] capPos = new BlockPos[2];
	private static final BlockState[] capState = new BlockState[2];
	private static boolean captured = false;

	private static ClientLevel levelRef = null;
	/** Becomes true once the block update hook has seen a packet, so rollbacks never run on a dead hook. */
	private static volatile boolean confirmationSeen = false;

	private AntiGhostState() {
	}

	/** Called from the network thread and the main thread when the server sends a block. */
	public static void confirm(BlockPos pos) {
		confirmationSeen = true;
		PENDING.remove(pos);
	}

	/** True if this right click would be refused by the server, so it must not be sent. */
	public static boolean shouldBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		if (!AntiGhostConfig.enabled) {
			return false;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return false;
		}
		var level = player.level();
		BlockPos clicked = hit.getBlockPos();
		BlockState clickedState = level.getBlockState(clicked);
		if (!player.isShiftKeyDown() && isInteractive(clickedState)) {
			return false; // the click opens / uses that block instead of placing
		}
		BlockPos target = clickedState.canBeReplaced() ? clicked : clicked.relative(hit.getDirection());
		if (level.isOutsideBuildHeight(target)) {
			return true;
		}
		if (!level.getBlockState(target).canBeReplaced()) {
			return false; // vanilla refuses this one by itself
		}
		BlockState place = blockItem.getBlock().defaultBlockState();
		return !level.isUnobstructed(place, target, CollisionContext.of(player));
	}

	private static boolean isInteractive(BlockState state) {
		return state.hasBlockEntity()
			|| state.is(BlockTags.DOORS)
			|| state.is(BlockTags.TRAPDOORS)
			|| state.is(BlockTags.BUTTONS)
			|| state.is(BlockTags.FENCE_GATES)
			|| state.is(BlockTags.BEDS)
			|| state.is(Blocks.CRAFTING_TABLE)
			|| state.is(Blocks.ANVIL)
			|| state.is(Blocks.LEVER)
			|| state.is(Blocks.REPEATER)
			|| state.is(Blocks.COMPARATOR)
			|| state.is(Blocks.NOTE_BLOCK);
	}

	/** Remember the states before a right click is sent. */
	public static void capture(BlockHitResult hit) {
		captured = false;
		if (!AntiGhostConfig.enabled) {
			return;
		}
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		BlockPos clicked = hit.getBlockPos();
		BlockPos target = clicked.relative(hit.getDirection());
		capPos[0] = clicked;
		capState[0] = level.getBlockState(clicked);
		capPos[1] = target;
		capState[1] = level.getBlockState(target);
		captured = true;
	}

	/** After the click: whatever changed on the client is now pending until the server confirms it. */
	public static void commit() {
		if (!captured) {
			return;
		}
		captured = false;
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		if (levelRef != level) {
			PENDING.clear();
			levelRef = level;
		}
		long now = level.getGameTime();
		for (int i = 0; i < 2; i++) {
			BlockPos pos = capPos[i];
			if (pos == null) {
				continue;
			}
			BlockState before = capState[i];
			BlockState current = level.getBlockState(pos);
			if (!current.equals(before)) {
				Pending old = PENDING.get(pos);
				BlockState previous = old != null ? old.previous() : before;
				PENDING.put(pos, new Pending(previous, current, now));
			}
		}
	}

	public static void discard() {
		captured = false;
	}

	private static int window(Minecraft mc) {
		int ticks = 6;
		if (mc.player != null && mc.getConnection() != null) {
			var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (info != null) {
				ticks = Math.max(ticks, info.getLatency() / 50 + 6);
			}
		}
		return Math.min(ticks + AntiGhostConfig.extraTicks, 40);
	}

	/** Every client tick: roll back what the server never confirmed. */
	public static void tick(Minecraft mc) {
		if (PENDING.isEmpty()) {
			return;
		}
		ClientLevel level = mc.level;
		if (level == null || level != levelRef) {
			PENDING.clear();
			return;
		}
		long now = level.getGameTime();
		int window = window(mc);
		Iterator<Map.Entry<BlockPos, Pending>> it = PENDING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<BlockPos, Pending> entry = it.next();
			Pending pending = entry.getValue();
			if (now - pending.placedAt() < window) {
				continue;
			}
			it.remove();
			if (!AntiGhostConfig.enabled || !confirmationSeen) {
				continue;
			}
			BlockPos pos = entry.getKey();
			// Still showing our guess and the server never sent that block: it is a ghost.
			if (level.getBlockState(pos).equals(pending.predicted())) {
				level.setBlock(pos, pending.previous(), 3);
			}
		}
	}

	public static void clear() {
		PENDING.clear();
		captured = false;
	}
}
