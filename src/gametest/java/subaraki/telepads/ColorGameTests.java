package subaraki.telepads;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.PadColors;
import subaraki.telepads.recipe.TelepadDyeRecipe;
import java.util.*;

/** Real recipe-manager, menu, placement, interaction and drop checks; acceptance builds only. */
public final class ColorGameTests {
    public static Item dye(DyeColor color) { return BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(color.getName() + "_dye")); }
    public static List<ItemStack> ring(ItemStack center, boolean mixed) {
        var items = new ArrayList<ItemStack>();
        for (int slot = 0; slot < 9; slot++) items.add(slot == 4 ? center : new ItemStack(dye(mixed && slot > 4 ? DyeColor.BLUE : DyeColor.RED)));
        return items;
    }
    public static ItemStack mixedPad() { return new TelepadDyeRecipe().assemble(CraftingInput.of(3, 3, ring(new ItemStack(Telepads.TELEPAD.get()), true))); }
    private static void check(GameTestHelper test, boolean valid, String message) { test.assertTrue(valid, message); }
    public static void recipes(GameTestHelper test) {
        var recipe = new TelepadDyeRecipe();
        var loaded = test.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("telepads", "telepad_dye"))).orElseThrow();
        check(test, loaded.value() instanceof TelepadDyeRecipe, "Custom recipe loads through recipe manager");
        var codec = Telepads.TELEPAD_DYE.get().codec().codec();
        check(test, codec.parse(JsonOps.INSTANCE, codec.encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()).getOrThrow() instanceof TelepadDyeRecipe, "Serializer JSON round-trip");
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), test.getLevel().registryAccess());
        try { Telepads.TELEPAD_DYE.get().streamCodec().encode(buf, recipe); check(test, Telepads.TELEPAD_DYE.get().streamCodec().decode(buf) instanceof TelepadDyeRecipe && !buf.isReadable(), "Recipe stream round-trip"); }
        finally { buf.release(); }
        for (var color : DyeColor.values()) {
            var inputItems = ring(new ItemStack(Telepads.TELEPAD.get(), 3), false);
            for (int i = 0; i < 9; i++) if (i != 4) inputItems.set(i, new ItemStack(dye(color), i + 1));
            var input = CraftingInput.of(3, 3, inputItems); var output = recipe.assemble(input);
            check(test, recipe.matches(input, test.getLevel()) && output.getCount() == 1, "All sixteen uniform rings accepted: " + color);
            var palette = output.get(Telepads.COLORS.get());
            check(test, palette.color(0) == color.getTextureDiffuseColor() && palette.color(1) == palette.color(0) && palette.craftDyes().equals(Collections.nCopies(8, color.getId())), "Uniform color and exact eight-dye receipt: " + color);
        }
        var center = new ItemStack(Telepads.TELEPAD.get(), 3);
        center.set(DataComponents.CUSTOM_NAME, Component.literal("Named palette")); center.set(DataComponents.MAX_STACK_SIZE, 16);
        center.set(Telepads.COLORS.get(), new PadColors(5, 9));
        var items = ring(center, true); var before = items.stream().map(ItemStack::copy).toList();
        var input = CraftingInput.of(3, 3, items); var output = recipe.assemble(input);
        check(test, output.get(Telepads.COLORS.get()).color(0) == 0xffad5398, "Fixed vanilla red/blue reference is #AD5398");
        check(test, output.getHoverName().getString().equals("Named palette") && output.get(DataComponents.MAX_STACK_SIZE) == 16, "Unrelated components survive");
        for (int i = 0; i < 9; i++) check(test, ItemStack.matches(items.get(i), before.get(i)), "Assembly does not mutate slot " + i);
        for (int i = 0; i < 9; i++) if (i != 4) items.get(i).setCount(i + 1);
        check(test, output.get(Telepads.COLORS.get()).equals(recipe.assemble(input).get(Telepads.COLORS.get())), "Stack counts do not weight blend");
        Collections.swap(items, 0, 8); Collections.swap(items, 1, 7); Collections.swap(items, 2, 6); Collections.swap(items, 3, 5);
        check(test, output.get(Telepads.COLORS.get()).color(0) == recipe.assemble(input).get(Telepads.COLORS.get()).color(0), "Dye order does not affect blend");
        var recraft = ring(output, false);
        check(test, recipe.assemble(CraftingInput.of(3, 3, recraft)).get(Telepads.COLORS.get()).equals(PadColors.crafted(DyeColor.RED.getTextureDiffuseColor() & 0xffffff, Collections.nCopies(8, 14))), "Recraft ignores previous colors and replaces receipt");
        for (int slot = 0; slot < 9; slot++) {
            for (var invalid : List.of(ItemStack.EMPTY, new ItemStack(Items.STONE), new ItemStack(Telepads.TELEPAD.get()))) {
                var bad = ring(new ItemStack(Telepads.TELEPAD.get()), true); bad.set(slot, invalid);
                if (slot == 4 && invalid.is(Telepads.TELEPAD.get())) continue;
                var badInput = CraftingInput.of(3, 3, bad);
                check(test, !recipe.matches(badInput, test.getLevel()) && recipe.assemble(badInput).isEmpty(), "Reject missing/invalid/extra pad slot " + slot);
            }
            if (slot != 4) { var bad = ring(new ItemStack(Telepads.TELEPAD.get()), true); Collections.swap(bad, slot, 4); check(test, !recipe.matches(CraftingInput.of(3, 3, bad), test.getLevel()), "Reject off-center pad " + slot); }
        }
        check(test, !recipe.matches(CraftingInput.of(2, 2, List.of(center, new ItemStack(dye(DyeColor.RED)), new ItemStack(dye(DyeColor.BLUE)), new ItemStack(dye(DyeColor.RED)))), test.getLevel()), "Reject inventory grid");
        test.succeed();
    }
    private static CraftingMenu menu(GameTestHelper test, ServerPlayer player) {
        var pos = test.absolutePos(new BlockPos(1, 1, 1)); test.getLevel().setBlockAndUpdate(pos, Blocks.CRAFTING_TABLE.defaultBlockState());
        var menu = new CraftingMenu(23, player.getInventory(), ContainerLevelAccess.create(test.getLevel(), pos)); player.containerMenu = menu; return menu;
    }
    private static void fill(CraftingMenu menu, List<ItemStack> items) { for (int i = 0; i < 9; i++) menu.getSlot(i + 1).set(items.get(i).copy()); }
    private static int inventoryCount(ServerPlayer player, Item item) { int n = 0; for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(item)) n += player.getInventory().getItem(i).getCount(); return n; }
    public static void menus(GameTestHelper test) {
        var player = TelepadGameTests.connected(test);
        try {
            player.getInventory().clearContent(); var menu = menu(test, player);
            var center = new ItemStack(Telepads.TELEPAD.get(), 4); center.set(DataComponents.CUSTOM_NAME, Component.literal("Menu palette")); center.set(DataComponents.MAX_STACK_SIZE, 16);
            var items = ring(center, true); for (int i = 0; i < 9; i++) if (i != 4) items.get(i).setCount(i + 2);
            fill(menu, items);
            for (int repeat = 0; repeat < 3; repeat++) menu.getSlot(1).setChanged();
            check(test, menu.getSlot(0).getItem().getCount() == 1 && menu.getSlot(0).getItem().get(Telepads.COLORS.get()).color(0) == 0xffad5398, "Actual preview contains named mixed pad");
            for (int i = 0; i < 9; i++) check(test, ItemStack.matches(menu.getSlot(i + 1).getItem(), items.get(i)), "Repeated previews preserve slot " + i);
            menu.clicked(0, 0, ContainerInput.PICKUP, player);
            check(test, menu.getCarried().getCount() == 1 && menu.getCarried().getHoverName().getString().equals("Menu palette") && menu.getCarried().get(DataComponents.MAX_STACK_SIZE) == 16, "Normal result taking preserves components");
            for (int i = 0; i < 9; i++) check(test, menu.getSlot(i + 1).getItem().getCount() == items.get(i).getCount() - 1, "One consumed per slot " + i);
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            check(test, inventoryCount(player, Telepads.TELEPAD.get()) == 1 && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(5).getItem().getCount() == 2, "Shift crafting stops at exhausted smallest dye stack");
            player.getInventory().clearContent(); items = ring(center, true); for (var item : items) item.setCount(3); fill(menu, items);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            check(test, inventoryCount(player, Telepads.TELEPAD.get()) == 3 && menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty()), "One shift click crafts three results with exact accounting");
            for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(Telepads.TELEPAD.get())) check(test, player.getInventory().getItem(i).getHoverName().getString().equals("Menu palette") && player.getInventory().getItem(i).get(DataComponents.MAX_STACK_SIZE) == 16, "Shift results retain components");
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            var target = mixedPad(); target.setCount(63); player.getInventory().setItem(35, target);
            items = ring(new ItemStack(Telepads.TELEPAD.get(), 3), true); for (var item : items) item.setCount(3); fill(menu, items);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            check(test, inventoryCount(player, Telepads.TELEPAD.get()) == 64 && menu.getSlot(5).getItem().getCount() == 2 && !menu.getSlot(0).getItem().isEmpty(), "Shift crafting stops when output inventory fills");
            for (var invalid : List.of(ItemStack.EMPTY, new ItemStack(Items.STONE), new ItemStack(Telepads.TELEPAD.get()))) {
                items = ring(new ItemStack(Telepads.TELEPAD.get()), true); items.set(0, invalid); fill(menu, items);
                menu.clicked(0, 0, ContainerInput.PICKUP, player);
                check(test, menu.getSlot(0).getItem().isEmpty() && menu.getCarried().isEmpty() && menu.getSlot(5).getItem().getCount() == 1, "Invalid menu cannot consume ingredients");
            }
            var small = player.inventoryMenu; small.getSlot(1).set(new ItemStack(Telepads.TELEPAD.get())); for (int i = 2; i <= 4; i++) small.getSlot(i).set(new ItemStack(dye(DyeColor.RED)));
            check(test, small.getSlot(0).getItem().isEmpty(), "Actual 2x2 inventory menu has no dye output");
            player.getInventory().clearContent(); fill(menu, ring(new ItemStack(Telepads.TELEPAD.get()), true)); menu.removed(player);
            check(test, inventoryCount(player, Telepads.TELEPAD.get()) == 1 && inventoryCount(player, dye(DyeColor.RED)) == 4 && inventoryCount(player, dye(DyeColor.BLUE)) == 4, "Closing preview returns all nine unchanged inputs");
        } finally { test.getLevel().getServer().getPlayerList().remove(player); }
        test.succeed();
    }
    private static TelepadBlockEntity place(GameTestHelper test, ServerPlayer player, BlockPos pos, ItemStack stack) {
        var absolute = test.absolutePos(pos); test.getLevel().setBlockAndUpdate(absolute.below(), Blocks.STONE.defaultBlockState());
        player.setPos(absolute.getX() + 2.5, absolute.getY(), absolute.getZ() + .5); player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute.below()), Direction.UP, absolute.below(), false);
        check(test, ((BlockItem)Telepads.TELEPAD.get()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))).consumesAction(), "Real block item placement succeeds");
        return (TelepadBlockEntity)test.getLevel().getBlockEntity(absolute);
    }
    private static void use(GameTestHelper test, ServerPlayer player, BlockPos pos, Item item) {
        var absolute = test.absolutePos(pos); player.setPos(absolute.getX() + 2.5, absolute.getY(), absolute.getZ() + .5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item)); test.useBlock(pos, player);
    }
    public static void lifecycle(GameTestHelper test) {
        var player = TelepadGameTests.connected(test); var pos = new BlockPos(1, 1, 1); var absolute = test.absolutePos(pos); var level = test.getLevel();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            var mixed = mixedPad(); var palette = mixed.get(Telepads.COLORS.get()); var pad = place(test, player, pos, mixed);
            check(test, pad.colors().equals(palette), "Placement carries full palette");
            pad.install(false); pad.install(true);
            var saved = pad.saveWithFullMetadata(level.registryAccess()); var restored = new TelepadBlockEntity(absolute, pad.getBlockState());
            restored.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
            check(test, restored.colors().equals(palette), "Save/load retains mixture and receipt");
            var update = new TelepadBlockEntity(absolute, pad.getBlockState()); update.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), pad.getUpdatePacket().getTag()));
            check(test, update.colors().equals(palette), "Update packet contains full palette");
            var drops = Block.getDrops(pad.getBlockState(), level, absolute, pad);
            check(test, drops.stream().filter(s -> s.is(Telepads.TOGGLER.get())).mapToInt(ItemStack::getCount).sum() == 1 && drops.stream().filter(s -> s.is(Telepads.TRANSMITTER.get())).mapToInt(ItemStack::getCount).sum() == 1, "Mixed pad returns each upgrade exactly once");
            check(test, pad.collectComponents().get(Telepads.COLORS.get()).equals(palette), "Implicit component collection retains receipt");
            level.destroyBlock(absolute, false); var dropped = drops.stream().filter(s -> s.is(Telepads.TELEPAD.get())).findFirst().orElseThrow(); pad = place(test, player, pos, dropped);
            check(test, pad.colors().equals(palette) && !pad.toggler() && !pad.transmitter(), "Replacement retains colors without duplicating upgrades");
            use(test, player, pos, dye(DyeColor.LIME)); var partial = pad.colors();
            check(test, partial.equals(palette.dyed(5)) && player.getMainHandItem().isEmpty(), "Survival first direct dye preserves base mixture and consumes one");
            var partialDrop = Block.getDrops(pad.getBlockState(), level, absolute, pad).getFirst(); level.destroyBlock(absolute, false); pad = place(test, player, pos, partialDrop);
            check(test, pad.colors().equals(partial), "Partial override survives drop/replacement");
            var partialLoaded = new TelepadBlockEntity(absolute, pad.getBlockState()); partialLoaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), pad.saveWithFullMetadata(level.registryAccess())));
            check(test, partialLoaded.colors().equals(partial), "Partial override survives save/load");
            for (var value : List.of(palette, partial, partial.dyed(4), PadColors.crafted(0xb02e26, Collections.nCopies(8, 14)), new TelepadDyeRecipe().assemble(CraftingInput.of(3, 3, ring(mixed, false))).get(Telepads.COLORS.get()))) {
                pad.setColors(value); pad.install(false); pad.install(true); player.getInventory().clearContent();
                use(test, player, pos, Items.WATER_BUCKET);
                check(test, pad.colors().equals(PadColors.DEFAULT) && player.getMainHandItem().is(Items.BUCKET) && pad.toggler() && pad.transmitter(), "Wash resets palette/bucket and preserves upgrades");
                for (var color : DyeColor.values()) check(test, inventoryCount(player, dye(color)) == Collections.frequency(value.recoveredDyes(), color.getId()), "Wash exact refund for " + color);
                int before = value.recoveredDyes().size(); use(test, player, pos, Items.WATER_BUCKET);
                int after = DyeColor.VALUES.stream().mapToInt(c -> inventoryCount(player, dye(c))).sum();
                check(test, before == after && player.getMainHandItem().is(Items.WATER_BUCKET), "Repeated wash cannot create dyes or exchange bucket");
            }
            pad.setColors(palette); use(test, player, pos, dye(DyeColor.LIME)); use(test, player, pos, dye(DyeColor.YELLOW)); use(test, player, pos, dye(DyeColor.BLUE));
            check(test, pad.colors().equals(new PadColors(5, 11)), "Actual repeated direct recoloring replaces base and discards receipt");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE); pad.setColors(palette); use(test, player, pos, dye(DyeColor.LIME));
            check(test, player.getMainHandItem().getCount() == 1 && pad.colors().equals(partial), "Creative direct dye does not consume");
            player.getInventory().clearContent(); use(test, player, pos, Items.WATER_BUCKET);
            check(test, player.getMainHandItem().is(Items.WATER_BUCKET) && inventoryCount(player, dye(DyeColor.RED)) == 4 && inventoryCount(player, dye(DyeColor.BLUE)) == 4 && inventoryCount(player, dye(DyeColor.LIME)) == 1, "Creative wash preserves bucket and existing refund rules");
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); pad.setColors(palette);
            for (int i = 0; i < 36; i++) player.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
            use(test, player, pos, Items.WATER_BUCKET);
            var ground = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4));
            check(test, ground.stream().filter(e -> e.getItem().is(dye(DyeColor.RED))).mapToInt(e -> e.getItem().getCount()).sum() == 4 && ground.stream().filter(e -> e.getItem().is(dye(DyeColor.BLUE))).mapToInt(e -> e.getItem().getCount()).sum() == 4, "Full inventory drops exact eight-dye refund");
        } finally { level.getServer().getPlayerList().remove(player); }
        test.succeed();
    }
}
