package dev.realseasons.mixin.client;

import dev.realseasons.RemoteCalendarWorld;
import net.minecraft.client.world.WorldClientMP;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value = WorldClientMP.class, remap = false)
public abstract class RemoteWorldMixin implements RemoteCalendarWorld {
}
