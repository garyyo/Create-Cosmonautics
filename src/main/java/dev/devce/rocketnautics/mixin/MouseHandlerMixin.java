package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.util.SimDistUtil;
import dev.simulated_team.simulated.util.click_interactions.InteractCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {

    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;
    @Unique
    private boolean transitioned = true;

    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void rocketnautics$handleMouseInput(CallbackInfo ci) {
        if (dev.devce.rocketnautics.client.ui.imgui.ImGuiManager.hasInstance()) {
            dev.devce.rocketnautics.client.ui.imgui.ImGuiManager imgui = dev.devce.rocketnautics.client.ui.imgui.ImGuiManager.getInstance();
            if (imgui.isOpen()) {
                this.accumulatedDX = 0;
                this.accumulatedDY = 0;
                ci.cancel();
                return;
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player instanceof FreeMotionEntity)) return;

        int inv = 1;
        if ((Boolean)mc.options.invertYMouse().get()) {
            inv = -1;
        }

        double dx = this.accumulatedDX;
        double dy = this.accumulatedDY * inv;

        if (SimDistUtil.getClientPlayer() != null && !SimDistUtil.getClientPlayer().isSpectator()) {
            final InteractCallback.Result status = SimulatedCommonClientEvents.onMouseMove(dx, dy);
            if (status.cancelled()) {
                ci.cancel();
                return;
            }
        }
    }

    @Redirect(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V")
    )
    private void rocketnautics$applyPlayerTurn(LocalPlayer player, double yawInput, double pitchInput) {
        if (!(player instanceof FreeMotionEntity fme)) {
            player.turn(yawInput, pitchInput);
            return;
        }

        Quaternionf q = fme.getOrientation();
        if (!fme.is6DOFEnabled()) {
            player.turn(yawInput, pitchInput);

            double yaw = player.getYRot();
            double pitch = player.getXRot();
            Quaternionf target = new Quaternionf()
                    .rotationYXZ(
                            (float)Math.toRadians(180.0f - yaw),
                            (float)Math.toRadians(-pitch),
                            0.0f
                    ).normalize();

            float dot = Math.clamp(q.normalize().dot(target), -1.0f, 1.0f);
            if (!transitioned && Math.abs(dot) < 0.999f) {
                q.slerp(target, 0.1f);
            } else {
                q.set(target);
                transitioned = true;
            }
            return;
        }

        double dYaw = Math.toRadians((float)yawInput * 0.15f);
        double dPitch = Math.toRadians((float)pitchInput * 0.15f);

        q.rotateYXZ((float)-dYaw, (float)-dPitch, 0.0f).normalize();

        Vector3d forward = new Vector3d(0, 0, -1);
        q.transform(forward);

        double yaw = Math.toDegrees(Math.atan2(-forward.x, forward.z));
        double pitch = -Math.toDegrees(Math.asin(forward.y));

        player.setYRot((float)yaw);
        player.setXRot((float)pitch);

        transitioned = false;
    }
}
