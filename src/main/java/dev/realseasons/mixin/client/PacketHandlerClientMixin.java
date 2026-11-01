package dev.realseasons.mixin.client;

import dev.realseasons.CalendarSeasonManager;
import dev.realseasons.RealSeasons;
import dev.realseasons.calendar.CalendarSeason;
import net.minecraft.client.net.handler.PacketHandlerClient;
import net.minecraft.client.world.WorldClientMP;
import net.minecraft.core.net.packet.PacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PacketHandlerClient.class, remap = false)
public abstract class PacketHandlerClientMixin {
    @Shadow private WorldClientMP worldClientMP;

    @Inject(method = "handleCustomPayload(Lnet/minecraft/core/net/packet/PacketCustomPayload;)V",
            at = @At("HEAD"), cancellable = true)
    private void realSeasons$receiveSeason(PacketCustomPayload packet, CallbackInfo ci) {
        if (!RealSeasons.SYNC_CHANNEL.equals(packet.channel)) return;
        ci.cancel();
        if (packet.data == null || packet.data.length != 2 || packet.data[0] != 1) return;
        int index = Byte.toUnsignedInt(packet.data[1]);
        if (index >= CalendarSeason.values().length || worldClientMP == null) return;
        if (worldClientMP.getSeasonManager() instanceof CalendarSeasonManager calendar) {
            calendar.acceptServerSeason(CalendarSeason.values()[index]);
        }
    }
}
