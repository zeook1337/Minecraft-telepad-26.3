package subaraki.telepads.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import subaraki.telepads.Telepads;
import subaraki.telepads.data.PadColors;

public record PadTint(int part) implements ItemTintSource {
    public static final MapCodec<PadTint> CODEC = com.mojang.serialization.Codec.intRange(0, 1).fieldOf("part").xmap(PadTint::new, PadTint::part);
    @Override public int calculate(ItemStack stack, ClientLevel level, LivingEntity owner) { return stack.getOrDefault(Telepads.COLORS.get(), PadColors.DEFAULT).color(part); }
    @Override public MapCodec<? extends ItemTintSource> type() { return CODEC; }
}
