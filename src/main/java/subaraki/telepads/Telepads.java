package subaraki.telepads;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import subaraki.telepads.block.TelepadBlock;
import subaraki.telepads.block.TelepadBlockEntity;
import java.util.Set;

@Mod(Telepads.MOD_ID)
public final class Telepads {
    public static final String MOD_ID = "telepads";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    private static final DeferredRegister<net.minecraft.core.component.DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    private static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    public static final RegistryObject<net.minecraft.world.item.crafting.RecipeSerializer<subaraki.telepads.recipe.TelepadDyeRecipe>> TELEPAD_DYE = RECIPES.register("telepad_dye", () -> subaraki.telepads.recipe.TelepadDyeRecipe.SERIALIZER);
    public static final RegistryObject<net.minecraft.core.component.DataComponentType<subaraki.telepads.data.PadColors>> COLORS = COMPONENTS.register("colors", () -> net.minecraft.core.component.DataComponentType.<subaraki.telepads.data.PadColors>builder().persistent(subaraki.telepads.data.PadColors.CODEC).networkSynchronized(subaraki.telepads.data.PadColors.STREAM).build());

    public static final RegistryObject<Block> TELEPAD_BLOCK = BLOCKS.register("telepad", () -> new TelepadBlock(
        BlockBehaviour.Properties.of().setId(BLOCKS.key("telepad")).mapColor(MapColor.COLOR_CYAN).strength(5, 3_600_000).noOcclusion()));
    public static final RegistryObject<Item> TELEPAD = ITEMS.register("telepad", () -> new BlockItem(TELEPAD_BLOCK.get(), itemProperties("telepad")));
    public static final RegistryObject<Item> BEAD = ITEMS.register("ender_bead", () -> new subaraki.telepads.item.PortableItem(false, itemProperties("ender_bead")));
    public static final RegistryObject<Item> NECKLACE = ITEMS.register("ender_bead_necklace", () -> new subaraki.telepads.item.PortableItem(true, itemProperties("ender_bead_necklace")));
    public static final RegistryObject<Item> TOGGLER = item("toggler");
    public static final RegistryObject<Item> TRANSMITTER = item("transmitter");
    public static final RegistryObject<Item> CYCLE_ROD = item("creative_rod");
    public static final RegistryObject<Item> PUBLIC_ROD = item("creative_rod_public");
    public static final RegistryObject<BlockEntityType<TelepadBlockEntity>> TELEPAD_ENTITY = BLOCK_ENTITIES.register("telepad", () ->
        new BlockEntityType<>(TelepadBlockEntity::new, Set.of(TELEPAD_BLOCK.get())));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("telepads", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.telepads"))
        .icon(() -> TELEPAD.get().getDefaultInstance())
        .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get()))).build());

    public static Item.Properties itemProperties(String name) { return new Item.Properties().setId(ITEMS.key(name)); }
    private static RegistryObject<Item> item(String name) { return ITEMS.register(name, () -> new Item(itemProperties(name))); }

    public Telepads(FMLJavaModLoadingContext context) {
        var bus = context.getModBusGroup();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
        COMPONENTS.register(bus);
        RECIPES.register(bus);
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, TelepadConfig.SERVER);
        context.registerConfig(net.minecraftforge.fml.config.ModConfig.Type.CLIENT, TelepadConfig.CLIENT);
        subaraki.telepads.network.TelepadNetwork.initialize();
        subaraki.telepads.server.NamingService.initialize();
        subaraki.telepads.server.TravelService.initialize();
        subaraki.telepads.item.PortableItem.registerAnvil();
        LOGGER.info("Telepads 26.3 adaptation registered (one block, seven items)");
    }
}
