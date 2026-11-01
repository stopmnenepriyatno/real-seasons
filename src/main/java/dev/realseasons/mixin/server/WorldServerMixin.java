package dev.realseasons.mixin.server;

import dev.realseasons.CalendarSeasonManager;
import dev.realseasons.RealSeasons;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.net.packet.PacketCustomPayload;
import net.minecraft.server.entity.player.PlayerServer;
import net.minecraft.server.world.WorldServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldServer.class, remap = false)
public abstract class WorldServerMixin {
    @Unique private int realSeasons$syncTicks;

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void realSeasons$syncClients(CallbackInfo ci) {
        WorldServer world = (WorldServer) (Object) this;
        if (!(world.getSeasonManager() instanceof CalendarSeasonManager calendar)) return;
        if (++realSeasons$syncTicks < 20) return;
        realSeasons$syncTicks = 0;
        // A tiny heartbeat also covers login, respawn and return from another dimension.
        byte[] data = {1, (byte) calendar.calendarSeason().ordinal()};
        for (Player player : world.players) {
            if (player instanceof PlayerServer serverPlayer && serverPlayer.playerNetServerHandler != null) {
                serverPlayer.playerNetServerHandler.sendPacket(new PacketCustomPayload(RealSeasons.SYNC_CHANNEL, data));
            }
        }
    }
}
