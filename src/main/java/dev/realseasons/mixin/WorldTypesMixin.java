package dev.realseasons.mixin;

import dev.realseasons.RealSeasons;
import dev.realseasons.RealSeasonsWorldType;
import net.minecraft.core.world.type.WorldTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = WorldTypes.class, remap = false)
public abstract class WorldTypesMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void realSeasons$registerType(CallbackInfo ci) {
        WorldTypes.register(RealSeasons.WORLD_TYPE_ID, new RealSeasonsWorldType());
    }
}
