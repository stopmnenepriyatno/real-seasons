package dev.realseasons.mixin;

import dev.realseasons.CalendarSeasonManager;
import net.minecraft.core.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = World.class, remap = false)
public abstract class WorldMixin {
    @Inject(method = "tick()V", at = @At("HEAD"))
    private void realSeasons$checkCalendar(CallbackInfo ci) {
        World world = (World) (Object) this;
        if (world.getSeasonManager() instanceof CalendarSeasonManager calendar) {
            calendar.tickCalendar();
        }
    }
}
