package dev.realseasons;

import net.minecraft.core.world.type.overworld.WorldTypeOverworld;
import net.minecraft.core.world.type.tag.WorldTypeTags;

/** The same properties, biome provider and generator as OVERWORLD_EXTENDED. */
public final class RealSeasonsWorldType extends WorldTypeOverworld {
    public RealSeasonsWorldType() {
        super(defaultProperties(RealSeasons.LANGUAGE_KEY).withTags(WorldTypeTags.OVERWORLD));
    }
}
