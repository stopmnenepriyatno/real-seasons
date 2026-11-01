package dev.realseasons.mixin;

import dev.realseasons.RealSeasons;
import net.minecraft.core.data.registry.Registries;
import net.minecraft.core.world.type.WorldType;
import net.minecraft.core.world.type.WorldTypeGroups;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(value = WorldTypeGroups.class, remap = false)
public abstract class WorldTypeGroupsMixin {
    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void realSeasons$addSelection(CallbackInfo ci) {
        WorldType type = Objects.requireNonNull(Registries.WORLD_TYPES.getItem(RealSeasons.WORLD_TYPE_ID));
        WorldTypeGroups.GROUPS.add(new WorldTypeGroups.Group(type));
    }
}
