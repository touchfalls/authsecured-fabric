package net.authsecured.fabric.mixin;

import net.authsecured.fabric.auth.AuthManager;
import net.authsecured.fabric.i18n.MessageService;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin intercepting player movement, chat messages, and command execution for unauthenticated players.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {

    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void onPlayerMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (!AuthManager.getInstance().isAuthenticated(player)) {
            ci.cancel();
            player.requestTeleport(player.getX(), player.getY(), player.getZ());
        }
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"), cancellable = true)
    private void onChatMessage(ChatMessageC2SPacket packet, CallbackInfo ci) {
        if (!AuthManager.getInstance().isAuthenticated(player)) {
            ci.cancel();
            player.sendMessage(MessageService.getInstance().get("authsecured.login.prompt"), false);
        }
    }

    @Inject(method = "onCommandExecution", at = @At("HEAD"), cancellable = true)
    private void onCommandExecution(CommandExecutionC2SPacket packet, CallbackInfo ci) {
        if (!AuthManager.getInstance().isAuthenticated(player)) {
            String command = packet.command().trim().toLowerCase();
            if (!command.startsWith("login") && !command.startsWith("register") && !command.startsWith("changepassword")) {
                ci.cancel();
                player.sendMessage(MessageService.getInstance().get("authsecured.command.restriction"), false);
            }
        }
    }
}
