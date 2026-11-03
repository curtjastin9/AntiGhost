package com.litantighost.mixin;

import com.litantighost.AntiGhostState;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

	@Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
	private void litantighost$before(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
									 CallbackInfoReturnable<InteractionResult> cir) {
		if (AntiGhostState.shouldBlock(player, hand, hit)) {
			AntiGhostState.discard();
			cir.setReturnValue(InteractionResult.FAIL);
			return;
		}
		AntiGhostState.capture(hit);
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void litantighost$after(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
									CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = cir.getReturnValue();
		if (result != null && result.consumesAction()) {
			AntiGhostState.commit();
		} else {
			AntiGhostState.discard();
		}
	}
}
