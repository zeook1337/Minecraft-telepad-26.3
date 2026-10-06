package subaraki.telepads.recipe;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import subaraki.telepads.Telepads;
import subaraki.telepads.data.PadColors;
import java.util.ArrayList;

public final class TelepadDyeRecipe extends CustomRecipe {
    public static final RecipeSerializer<TelepadDyeRecipe> SERIALIZER = new RecipeSerializer<>(
        MapCodec.unit(TelepadDyeRecipe::new), StreamCodec.of((buf, recipe) -> {}, buf -> new TelepadDyeRecipe()));

    @Override public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3 || !input.getItem(4).is(Telepads.TELEPAD.get())) return false;
        for (int slot = 0; slot < 9; slot++) {
            if (slot == 4) continue;
            var stack = input.getItem(slot);
            if (stack.isEmpty() || stack.is(Telepads.TELEPAD.get()) || !stack.has(DataComponents.DYE)) return false;
        }
        return true;
    }
    @Override public ItemStack assemble(CraftingInput input) {
        if (!matches(input, null)) return ItemStack.EMPTY;
        var dyes = new ArrayList<DyeColor>(8);
        for (int slot = 0; slot < 9; slot++) if (slot != 4) dyes.add(input.getItem(slot).get(DataComponents.DYE));
        var color = DyedItemColor.applyDyes((DyedItemColor)null, dyes);
        var result = input.getItem(4).copyWithCount(1);
        result.set(Telepads.COLORS.get(), PadColors.crafted(color.rgb(), dyes.stream().map(DyeColor::getId).toList()));
        return result;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingInput input) { return NonNullList.withSize(input.size(), ItemStack.EMPTY); }
    @Override public RecipeSerializer<TelepadDyeRecipe> getSerializer() { return Telepads.TELEPAD_DYE.get(); }
}
