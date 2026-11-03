package com.litantighost.mixin;

import com.litantighost.AntiGhostState;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server sending a block is the confirmation that it really has that block. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {

	@Inject(method = "handleBlockUpdate", at = @At("HEAD"), require = 0)
	private void litantighost$onBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
		AntiGhostState.confirm(packet.getPos());
	}

	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"), require = 0)
	private void litantighost$onSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		packet.runUpdates((pos, state) -> AntiGhostState.confirm(pos.immutable()));
	}
}
