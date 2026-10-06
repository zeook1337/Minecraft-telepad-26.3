package subaraki.telepads.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** Names are presentation; only dimension and coordinates identify a location. */
public record TelepadLocation(String dimension, int x, int y, int z) {
    public static final Codec<TelepadLocation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Identifier.CODEC.xmap(Identifier::toString, Identifier::parse).fieldOf("dimension").forGetter(TelepadLocation::dimension),
        Codec.INT.fieldOf("x").forGetter(TelepadLocation::x),
        Codec.INT.fieldOf("y").forGetter(TelepadLocation::y),
        Codec.INT.fieldOf("z").forGetter(TelepadLocation::z)
    ).apply(instance, TelepadLocation::new));
}
