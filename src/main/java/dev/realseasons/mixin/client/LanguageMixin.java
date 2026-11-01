package dev.realseasons.mixin.client;

import dev.realseasons.RealSeasons;
import net.minecraft.core.lang.Language;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Properties;

@Mixin(value = Language.Default.class, remap = false)
public abstract class LanguageMixin {
    @Shadow public abstract Properties getEntries();

    @Inject(method = "<init>()V", at = @At("RETURN"))
    private void realSeasons$addNames(CallbackInfo ci) {
        // Default clears the superclass entries before loading native namespaces.
        // Add fallbacks only after that initialization; other languages use Default too.
        Properties entries = getEntries();
        entries.putIfAbsent(RealSeasons.LANGUAGE_KEY + ".name", "Real Seasons");
        entries.putIfAbsent(RealSeasons.LANGUAGE_KEY + ".desc",
                "Standard BTA terrain. Native seasons follow the host's local calendar.");
    }
}
