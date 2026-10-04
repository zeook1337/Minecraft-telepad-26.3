package subaraki.telepads.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** One palette and, while either part retains it, the latest eight paid crafting dyes. */
public record PadColors(int frame, int base, Optional<Integer> frameRgb, Optional<Integer> baseRgb, List<Integer> craftDyes) {
    public static final PadColors DEFAULT = new PadColors(-1, -1);
    private record Stored(int frame, int base, Optional<Integer> frameRgb, Optional<Integer> baseRgb, List<Integer> craftDyes) {}
    private static final Codec<Stored> STORED = RecordCodecBuilder.create(instance -> instance.group(
        Codec.intRange(-1, 15).fieldOf("frame").forGetter(Stored::frame),
        Codec.intRange(-1, 15).fieldOf("base").forGetter(Stored::base),
        Codec.intRange(0, 0xffffff).optionalFieldOf("frame_rgb").forGetter(Stored::frameRgb),
        Codec.intRange(0, 0xffffff).optionalFieldOf("base_rgb").forGetter(Stored::baseRgb),
        Codec.intRange(0, 15).listOf(0, 8).optionalFieldOf("craft_dyes", List.of()).forGetter(Stored::craftDyes)
    ).apply(instance, Stored::new));
    public static final Codec<PadColors> CODEC = STORED.flatXmap(value -> {
        try { return DataResult.success(new PadColors(value.frame, value.base, value.frameRgb, value.baseRgb, value.craftDyes)); }
        catch (IllegalArgumentException exception) { return DataResult.error(exception::getMessage); }
    }, value -> DataResult.success(new Stored(value.frame, value.base, value.frameRgb, value.baseRgb, value.craftDyes)));
    /** Protocol 2: two IDs, two optional RGBs, and a bounded receipt. */
    public static final StreamCodec<RegistryFriendlyByteBuf, PadColors> STREAM = StreamCodec.of((buf, colors) -> {
        buf.writeByte(colors.frame); buf.writeByte(colors.base);
        writeRgb(buf, colors.frameRgb); writeRgb(buf, colors.baseRgb);
        buf.writeByte(colors.craftDyes.size());
        colors.craftDyes.forEach(buf::writeByte);
    }, buf -> {
        int frame = buf.readByte(), base = buf.readByte();
        var frameRgb = readRgb(buf); var baseRgb = readRgb(buf);
        int size = buf.readUnsignedByte();
        if (size != 0 && size != 8) throw new IllegalArgumentException("Invalid crafting dye receipt size");
        var dyes = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) dyes.add((int)buf.readByte());
        return new PadColors(frame, base, frameRgb, baseRgb, dyes);
    });
    public PadColors(int frame, int base) { this(frame, base, Optional.empty(), Optional.empty(), List.of()); }
    public PadColors {
        if (frame < -1 || frame > 15 || base < -1 || base > 15) throw new IllegalArgumentException("Invalid dye");
        craftDyes = List.copyOf(craftDyes);
        for (var rgb : List.of(frameRgb, baseRgb)) if (rgb.isPresent() && (rgb.get() < 0 || rgb.get() > 0xffffff)) throw new IllegalArgumentException("Invalid RGB");
        if (frameRgb.isPresent() && frame != -1 || baseRgb.isPresent() && base != -1) throw new IllegalArgumentException("A part cannot have both a dye ID and RGB");
        if (frameRgb.isPresent() && baseRgb.isPresent() && !frameRgb.equals(baseRgb)) throw new IllegalArgumentException("Crafted parts must share one mixture");
        boolean mixed = frameRgb.isPresent() || baseRgb.isPresent();
        if (mixed ? craftDyes.size() != 8 : !craftDyes.isEmpty()) throw new IllegalArgumentException("Mixture requires one eight-dye receipt");
        if (craftDyes.stream().anyMatch(id -> id < 0 || id > 15)) throw new IllegalArgumentException("Invalid crafting dye");
    }
    public static PadColors crafted(int rgb, List<Integer> dyes) { return new PadColors(-1, -1, Optional.of(rgb), Optional.of(rgb), dyes); }
    private static void writeRgb(RegistryFriendlyByteBuf buf, Optional<Integer> rgb) { buf.writeBoolean(rgb.isPresent()); rgb.ifPresent(buf::writeInt); }
    private static Optional<Integer> readRgb(RegistryFriendlyByteBuf buf) { return buf.readBoolean() ? Optional.of(buf.readInt()) : Optional.empty(); }
    public int color(int part) {
        var rgb = part == 0 ? frameRgb : baseRgb;
        if (rgb.isPresent()) return 0xff000000 | rgb.get();
        int dye = part == 0 ? frame : base;
        return dye < 0 ? part == 0 ? 0xffb8c4cb : 0xff18cbd1 : DyeColor.byId(dye).getTextureDiffuseColor();
    }
    public PadColors dyed(int dye) {
        if (frame < 0) return new PadColors(dye, base, Optional.empty(), baseRgb, baseRgb.isPresent() ? craftDyes : List.of());
        return new PadColors(frame, dye, frameRgb, Optional.empty(), frameRgb.isPresent() ? craftDyes : List.of());
    }
    public List<Integer> recoveredDyes() {
        var dyes = new ArrayList<>(craftDyes);
        if (frame >= 0) dyes.add(frame);
        if (base >= 0) dyes.add(base);
        return List.copyOf(dyes);
    }
}
