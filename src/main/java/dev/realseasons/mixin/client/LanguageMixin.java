package dev.realseasons.mixin.client;

import dev.realseasons.RealSeasons;
import net.minecraft.core.lang.Language;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Properties;

@Mixin(value = Language.class, remap = false)
public abstract class LanguageMixin {
    @Shadow @Final protected Properties entries;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void realSeasons$addNames(CallbackInfo ci) {
        // BTA's language directories are not Fabric resource-pack namespaces.
        entries.putIfAbsent(RealSeasons.LANGUAGE_KEY + ".name", "Real Seasons");
        entries.putIfAbsent(RealSeasons.LANGUAGE_KEY + ".desc",
                "Standard BTA terrain. Native seasons follow the host's local calendar.");
    }
}
