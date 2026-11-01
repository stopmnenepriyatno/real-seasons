package dev.realseasons.mixin;

import dev.realseasons.CalendarSeasonManager;
import dev.realseasons.RealSeasonsWorldType;
import net.minecraft.core.world.World;
import net.minecraft.core.world.config.season.SeasonConfig;
import net.minecraft.core.world.season.SeasonManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SeasonManager.class, remap = false)
public abstract class SeasonManagerMixin {
    @Inject(method = "fromConfig(Lnet/minecraft/core/world/World;Lnet/minecraft/core/world/config/season/SeasonConfig;)Lnet/minecraft/core/world/season/SeasonManager;",
            at = @At("HEAD"), cancellable = true)
    private static void realSeasons$createManager(World world, SeasonConfig config,
                                                 CallbackInfoReturnable<SeasonManager> cir) {
        if (world.getWorldType() instanceof RealSeasonsWorldType) {
            cir.setReturnValue(new CalendarSeasonManager(world));
        }
    }
}
