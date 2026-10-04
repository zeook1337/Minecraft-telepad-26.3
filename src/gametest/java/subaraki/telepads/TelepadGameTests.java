package subaraki.telepads;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraftforge.gametest.GameTest;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import subaraki.telepads.block.TelepadBlock;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.TelepadCatalog;
import java.util.UUID;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import subaraki.telepads.network.TelepadNetwork;
import subaraki.telepads.server.NamingService;

@Mod.EventBusSubscriber(modid = "telepads", bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TelepadGameTests {
    static net.minecraft.server.level.ServerPlayer connected(GameTestHelper test) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TelepadTest"), false);
        var player = new net.minecraft.server.level.ServerPlayer(test.getLevel().getServer(), test.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        test.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper -> {
            helper.register(Identifier.fromNamespaceAndPath("telepads", "naming"), TelepadGameTests::namingRequiresPhysicalPlacement);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "platform"), TelepadGameTests::platformPersistenceAndReplacement);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "sharing"), TelepadGameTests::sharingAndPhysicalDiscovery);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "travel"), TelepadGameTests::validatedTravelAndCosts);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "extensions"), TelepadGameTests::upgradesDyesPortableAndRecipes);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "edge_cases"), TelepadGameTests::configurationDragonAndAnvilMenu);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "dye_recipe"), ColorGameTests::recipes);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "dye_menu"), ColorGameTests::menus);
            helper.register(Identifier.fromNamespaceAndPath("telepads", "dye_lifecycle"), ColorGameTests::lifecycle);
        });
    }
    @GameTest public static void configurationDragonAndAnvilMenu(GameTestHelper test) {
        var player = connected(test); var server = test.getLevel().getServer();
        int wait = TelepadConfig.WAIT_SECONDS.get(); boolean dragonSetting = TelepadConfig.DRAGON_BLOCK.get(), anvil = TelepadConfig.ANVIL.get(), bead = TelepadConfig.BEAD.get();
        var configured = TelepadConfig.DESTINATIONS.get();
        try {
            player.setGameMode(GameType.SURVIVAL); player.giveExperienceLevels(10);
            var menu = new net.minecraft.world.inventory.AnvilMenu(17, player.getInventory());
            menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 2));
            menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 2)); menu.createResult();
            var output = menu.getSlot(2).getItem();
            test.assertTrue(output.is(Telepads.BEAD.get()) && output.getCount() == 8 && menu.getSlot(2).mayPickup(player), "Actual anvil menu must offer eight beads");
            menu.getSlot(2).onTake(player, output.copy());
            test.assertTrue(menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty() && player.experienceLevel == 6, "Taking result consumes both inputs and four levels");
            for (int total = 2; total <= 8; total++) {
                menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)); menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, total - 1)); menu.createResult();
                test.assertTrue(menu.getSlot(2).getItem().getCount() == total * 2, "Anvil accepts each total from two to eight");
            }
            TelepadConfig.ANVIL.set(false); menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)); menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)); menu.createResult();
            test.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Disabling conversion must remove actual menu result");
            TelepadConfig.ANVIL.set(true); menu.getSlot(0).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 8)); menu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)); menu.createResult();
            test.assertTrue(menu.getSlot(2).getItem().isEmpty(), "Nine pearls must reject conversion");
            TelepadConfig.BEAD.set(false); player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.BEAD.get(), 2)); var before = player.position();
            test.assertTrue(!Telepads.BEAD.get().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction() && player.getMainHandItem().getCount() == 2 && player.position().equals(before), "Disabled bead preserves item and position");
            TelepadConfig.BEAD.set(true); test.assertTrue(!Telepads.BEAD.get().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction() && player.getMainHandItem().getCount() == 2, "No authorized candidates must preserve portable item");
            var sourcePos = test.absolutePos(new BlockPos(1, 1, 1)); var source = place(player.level(), sourcePos, player.getUUID());
            String fixed = (sourcePos.getX() + 20) + "/" + sourcePos.getY() + "/" + sourcePos.getZ() + "/minecraft:overworld/Fixed";
            var arrival = sourcePos.offset(20, 0, 0); player.level().setBlockAndUpdate(arrival.below(), Blocks.STONE.defaultBlockState());
            TelepadConfig.DESTINATIONS.set(java.util.List.of(fixed)); TelepadConfig.WAIT_SECONDS.set(0);
            var pad = (TelepadBlockEntity)player.level().getBlockEntity(sourcePos); pad.setConfigured(fixed);
            open(player, source, java.util.List.of()); subaraki.telepads.server.TravelService.sessions(server).clear(player.getUUID());
            subaraki.telepads.server.TravelService.tick(player);
            test.assertTrue(player.distanceToSqr(arrival.getX() + .5, arrival.getY(), arrival.getZ() + .5) < .001 && player.experienceLevel == 6, "Configured travel needs no receiver or XP");
            TelepadConfig.DESTINATIONS.set(java.util.List.of()); open(player, source, java.util.List.of()); subaraki.telepads.server.TravelService.sessions(server).clear(player.getUUID()); before = player.position();
            subaraki.telepads.server.TravelService.tick(player); test.assertTrue(player.position().equals(before), "Removed configured selection must fail after reload");
            test.assertTrue(pad.configured().isEmpty(), "Removed configured selection must restore normal mode");
            String interval = "-12#-10/100/-12#-10/minecraft:the_nether/Interval";
            var intervalLevel = server.getLevel(net.minecraft.world.level.Level.NETHER);
            for (int x = -15; x <= -7; x++) for (int z = -15; z <= -7; z++) { intervalLevel.setBlockAndUpdate(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState()); for (int y = 100; y <= 103; y++) intervalLevel.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState()); }
            TelepadConfig.DESTINATIONS.set(java.util.List.of(interval)); pad.setConfigured(interval); subaraki.telepads.server.TravelService.sessions(server).clear(player.getUUID()); subaraki.telepads.server.TravelService.tick(player);
            test.assertTrue(player.level() == intervalLevel && player.getX() >= -11.5 && player.getX() <= -9.5 && player.getZ() >= -11.5 && player.getZ() <= -9.5 && player.experienceLevel == 6, "Configured interval travels to real Nether without receiver/transmitter/XP");
            test.assertTrue(subaraki.telepads.server.ConfiguredDestination.parse("0/-64/0/minecraft:overworld/Min").resolve(server, player.getRandom()).pos().getY() == -64, "Minimum world Y must be accepted");
            try { subaraki.telepads.server.ConfiguredDestination.parse("0/64/0/telepads:unknown/Unknown").resolve(server, player.getRandom()); test.fail("Unknown configured dimension must reject"); } catch (IllegalArgumentException expected) {}
            var end = server.getLevel(net.minecraft.world.level.Level.END);
            var endPlayer = new net.minecraft.server.level.ServerPlayer(server, end, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "EndTest"), net.minecraft.server.level.ClientInformation.createDefault());
            var dragon = new net.minecraft.world.entity.boss.enderdragon.EnderDragon(net.minecraft.world.entity.EntityTypes.ENDER_DRAGON, end) { @Override public boolean isAlwaysTicking() { return true; } }; dragon.setPos(0, 100, 0); end.addFreshEntity(dragon);
            try { TelepadConfig.DRAGON_BLOCK.set(true); test.assertTrue(subaraki.telepads.server.TravelService.dragonBlocked(endPlayer), "Live dragon must block configured and normal platform activation"); TelepadConfig.DRAGON_BLOCK.set(false); test.assertTrue(!subaraki.telepads.server.TravelService.dragonBlocked(endPlayer), "Disabled rule must allow activation"); }
            finally { dragon.discard(); }
        } finally { TelepadConfig.WAIT_SECONDS.set(wait); TelepadConfig.DRAGON_BLOCK.set(dragonSetting); TelepadConfig.ANVIL.set(anvil); TelepadConfig.BEAD.set(bead); TelepadConfig.DESTINATIONS.set(configured); server.getPlayerList().remove(player); }
        test.succeed();
    }
    private static subaraki.telepads.data.TelepadEntry place(net.minecraft.server.level.ServerLevel level, BlockPos pos, UUID owner) {
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos, Telepads.TELEPAD_BLOCK.get().defaultBlockState());
        var entry = TelepadCatalog.get(level.getServer()).place(TelepadBlock.location(level, pos), owner);
        ((TelepadBlockEntity)level.getBlockEntity(pos)).setIdentity(entry.id(), entry.name());
        return entry;
    }
    private static subaraki.telepads.server.ActivationSessions.Session open(net.minecraft.server.level.ServerPlayer player, subaraki.telepads.data.TelepadEntry origin, java.util.List<UUID> targets) {
        player.setPos(origin.location().x() + .5, origin.location().y() + .2, origin.location().z() + .5);
        return subaraki.telepads.server.TravelService.sessions(player.level().getServer()).open(player.getUUID(), origin.id(), origin.location(), player.level().getServer().overworld().getGameTime(), targets);
    }
    @GameTest public static void validatedTravelAndCosts(GameTestHelper test) {
        var player = connected(test);
        var level = test.getLevel(); var server = level.getServer();
        var originPos = test.absolutePos(new BlockPos(1, 1, 1));
        var targetPos = originPos.offset(32, 0, 0);
        var origin = place(level, originPos, player.getUUID()); var target = place(level, targetPos, player.getUUID());
        var catalog = TelepadCatalog.get(server);
        int oldLevels = TelepadConfig.XP_LEVELS.get(), oldPoints = TelepadConfig.XP_POINTS.get();
        try {
            var lifecycle = open(player, origin, java.util.List.of(target.id()));
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent.BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(player));
            test.assertTrue(subaraki.telepads.server.TravelService.sessions(server).find(player.getUUID(), lifecycle.token(), server.overworld().getGameTime()) == null, "Logout event must clear transient activation");
            lifecycle = open(player, origin, java.util.List.of(target.id()));
            net.minecraftforge.event.entity.living.LivingDeathEvent.BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic()));
            test.assertTrue(subaraki.telepads.server.TravelService.sessions(server).find(player.getUUID(), lifecycle.token(), server.overworld().getGameTime()) == null, "Death event must clear transient activation");
            player.setGameMode(GameType.SURVIVAL); player.giveExperiencePoints(100);
            TelepadConfig.XP_LEVELS.set(2); TelepadConfig.XP_POINTS.set(100000);
            var session = open(player, origin, java.util.List.of(target.id()));
            int xp = player.experienceLevel; var before = player.position();
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(UUID.randomUUID(), 2, target.id(), 0, 0)), "Invented activation must fail");
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, UUID.randomUUID(), 0, 0)), "Invented destination must fail");
            test.assertTrue(player.position().equals(before) && player.experienceLevel == xp, "Invalid packets cannot move or charge");
            catalog.update(target.id(), entry -> entry.register(player.getUUID(), false));
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, target.id(), 0, 0)), "Revoked access must fail");
            catalog.update(target.id(), entry -> entry.register(player.getUUID(), true));
            var canceled = net.minecraftforge.event.entity.EntityTravelToDimensionEvent.BUS.addListener((java.util.function.Predicate<net.minecraftforge.event.entity.EntityTravelToDimensionEvent>)event -> event.getEntity() == player);
            try {
                test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, target.id(), 0, 0)), "Forge cancellation must reject travel");
                test.assertTrue(player.position().equals(before) && player.experienceLevel == xp, "Canceled travel must preserve position and XP");
            } finally { net.minecraftforge.event.entity.EntityTravelToDimensionEvent.BUS.removeListener(canceled); }
            session = open(player, origin, java.util.List.of(target.id()));
            test.assertTrue(subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, target.id(), 0, 0)), "Valid travel must arrive");
            test.assertTrue(player.distanceToSqr(targetPos.getX() + .5, targetPos.getY() + .2, targetPos.getZ() + .5) < .001, "Arrival must be on platform");
            test.assertTrue(player.experienceLevel == xp - 2, "Levels must take priority over points and charge exactly once");
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, target.id(), 0, 0)), "Replay must fail");
            TelepadConfig.XP_LEVELS.set(0); TelepadConfig.XP_POINTS.set(5);
            long balance = subaraki.telepads.server.ExperienceCost.balance(player.experienceLevel, player.experienceProgress);
            test.assertTrue(subaraki.telepads.server.TravelService.travel(player, origin, true, false), "Point-cost return trip must succeed");
            test.assertTrue(subaraki.telepads.server.ExperienceCost.balance(player.experienceLevel, player.experienceProgress) == balance - 5, "Point cost must be exact");
            TelepadConfig.XP_POINTS.set(100000); before = player.position(); balance = subaraki.telepads.server.ExperienceCost.balance(player.experienceLevel, player.experienceProgress);
            test.assertTrue(!subaraki.telepads.server.TravelService.travel(player, target, true, false), "Insufficient balance must fail");
            test.assertTrue(before.equals(player.position()) && balance == subaraki.telepads.server.ExperienceCost.balance(player.experienceLevel, player.experienceProgress), "No partial XP charge");
            TelepadConfig.XP_POINTS.set(0);
            level.removeBlock(targetPos, false); session = open(player, origin, java.util.List.of(target.id()));
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, target.id(), 0, 0)), "Missing destination needs confirmation");
            test.assertTrue(subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 3, target.id(), 0, 0)), "Confirmed missing location with support must work");
            var unavailable = catalog.place(new subaraki.telepads.data.TelepadLocation("telepads:unknown", 0, 64, 0), player.getUUID()); catalog.markMissing(unavailable.location());
            test.assertTrue(!subaraki.telepads.server.TravelService.travel(player, catalog.find(unavailable.id()), true, true), "Unknown dimension must fail safely");
            var voidPos = originPos.offset(256, 0, 256);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -5; dy <= 6; dy++) level.setBlockAndUpdate(voidPos.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
            test.assertTrue(subaraki.telepads.server.SafeArrival.find(level, player, voidPos).isEmpty(), "Unsupported arrival must fail in a bounded search");
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) for (int dy = -5; dy <= 7; dy++) level.setBlockAndUpdate(voidPos.offset(dx, dy, dz), Blocks.STONE.defaultBlockState());
            test.assertTrue(subaraki.telepads.server.SafeArrival.find(level, player, voidPos).isEmpty(), "Blocked arrival must fail in a bounded search");
            test.assertTrue(subaraki.telepads.server.SafeArrival.find(level, player, new BlockPos(30000001, 64, 0)).isEmpty(), "Outside border must fail");
            var nether = server.getLevel(net.minecraft.world.level.Level.NETHER); var netherPos = new BlockPos(0, 100, 0);
            for (int y = 0; y < 4; y++) nether.setBlockAndUpdate(netherPos.above(y), Blocks.AIR.defaultBlockState());
            var cross = place(nether, netherPos, player.getUUID()); session = open(player, origin, java.util.List.of(cross.id()));
            test.assertTrue(!subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, cross.id(), 0, 0)), "Origin without transmitter must reject cross-dimension travel");
            ((TelepadBlockEntity)level.getBlockEntity(originPos)).install(true); session = open(player, origin, java.util.List.of(cross.id()));
            test.assertTrue(subaraki.telepads.server.TravelService.handle(player, new TelepadNetwork.TravelRequest(session.token(), 2, cross.id(), 0, 0)), "Only origin needs a transmitter");
            test.assertTrue(player.level() == nether, "Player must arrive in Nether");
        } finally { TelepadConfig.XP_LEVELS.set(oldLevels); TelepadConfig.XP_POINTS.set(oldPoints); server.getPlayerList().remove(player); }
        test.succeed();
    }
    @GameTest public static void upgradesDyesPortableAndRecipes(GameTestHelper test) {
        var player = connected(test); var level = test.getLevel(); var pos = new BlockPos(1, 1, 1); var absolute = test.absolutePos(pos);
        var entry = place(level, absolute, player.getUUID()); var pad = (TelepadBlockEntity)level.getBlockEntity(absolute);
        int oldPortableXp = TelepadConfig.XP_POINTS.get();
        try {
            TelepadConfig.XP_POINTS.set(999999);
            player.setGameMode(GameType.SURVIVAL); player.setPos(absolute.getX() + .5, absolute.getY() + .2, absolute.getZ() + .5);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.TRANSMITTER.get(), 2)); test.useBlock(pos, player); test.useBlock(pos, player);
            test.assertTrue(pad.transmitter() && player.getMainHandItem().getCount() == 1, "Transmitter install must consume once");
            level.setBlockAndUpdate(absolute.east(), Blocks.REDSTONE_BLOCK.defaultBlockState()); test.assertTrue(!pad.refreshDisabled(), "Unmodified pad must ignore redstone");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.TOGGLER.get(), 2)); test.useBlock(pos, player); test.useBlock(pos, player);
            test.assertTrue(pad.toggler() && pad.refreshDisabled() && player.getMainHandItem().getCount() == 1, "Toggler consumes once and disables powered pad");
            test.assertTrue(!subaraki.telepads.server.TravelService.travel(player, entry, false, false), "Powered destination must reject travel");
            level.removeBlock(absolute.east(), false); test.assertTrue(!pad.refreshDisabled(), "Removing signal must reactivate");
            var redDye = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_dye"));
            var blueDye = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("blue_dye"));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(redDye)); test.useBlock(pos, player);
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(blueDye)); test.useBlock(pos, player);
            test.assertTrue(pad.colors().frame() == net.minecraft.world.item.DyeColor.RED.getId() && pad.colors().base() == net.minecraft.world.item.DyeColor.BLUE.getId(), "Dyes target frame then base");
            var drops = net.minecraft.world.level.block.Block.getDrops(level.getBlockState(absolute), level, absolute, pad);
            test.assertTrue(drops.size() == 3 && drops.stream().filter(stack -> stack.is(Telepads.TELEPAD.get())).count() == 1, "Drops must contain one block and one of each upgrade");
            var blockDrop = drops.stream().filter(stack -> stack.is(Telepads.TELEPAD.get())).findFirst().orElseThrow();
            test.assertTrue(blockDrop.get(Telepads.COLORS.get()).equals(pad.colors()), "Block drop preserves colors through component");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET)); test.useBlock(pos, player);
            test.assertTrue(pad.colors().equals(subaraki.telepads.data.PadColors.DEFAULT) && player.getMainHandItem().is(net.minecraft.world.item.Items.BUCKET), "Washing resets colors and bucket");
            int returnedRed = 0, returnedBlue = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) { var stack = player.getInventory().getItem(slot); if (stack.is(redDye)) returnedRed += stack.getCount(); if (stack.is(blueDye)) returnedBlue += stack.getCount(); }
            test.assertTrue(returnedRed == 1 && returnedBlue == 1, "Washing must return exactly one of each applied dye");
            var replacement = new TelepadBlockEntity(absolute, pad.getBlockState()); replacement.applyComponentsFromItemStack(blockDrop);
            test.assertTrue(replacement.colors().equals(blockDrop.get(Telepads.COLORS.get())) && !replacement.toggler() && !replacement.transmitter(), "Replaced block preserves colors without returned upgrades");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.PUBLIC_ROD.get())); test.useBlock(pos, player);
            test.assertTrue(!TelepadCatalog.get(level.getServer()).find(entry.id()).publicAccess(), "Survival non-operator cannot publish");
            player.setGameMode(GameType.CREATIVE); test.useBlock(pos, player); test.assertTrue(TelepadCatalog.get(level.getServer()).find(entry.id()).publicAccess(), "Creative tool can publish");
            test.useBlock(pos, player); test.assertTrue(!TelepadCatalog.get(level.getServer()).find(entry.id()).publicAccess(), "Public/private cycle preserves users");
            var event = new net.minecraftforge.event.AnvilUpdateEvent(new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 2), new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 2), "", 0, player);
            test.assertTrue(!subaraki.telepads.item.PortableItem.updateAnvil(event) && event.getOutput().is(Telepads.BEAD.get()) && event.getOutput().getCount() == 8 && event.getCost() == 4 && event.getMaterialCost() == 2, "Anvil must double pearls and consume right stack for exact level cost");
            for (String name : java.util.List.of("telepad", "transmitter", "toggler", "necklace")) test.assertTrue(level.getServer().getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath("telepads", name))).isPresent(), "Recipe must load: " + name);
            player.setGameMode(GameType.SURVIVAL); player.setPos(absolute.getX() + 10, absolute.getY() + .2, absolute.getZ() + .5);
            var farther = place(level, absolute.offset(32, 0, 0), player.getUUID());
            var disabledNearest = place(level, absolute.offset(10, 0, 0), player.getUUID()); var disabledPad = (TelepadBlockEntity)level.getBlockEntity(absolute.offset(10, 0, 0)); disabledPad.install(false); level.setBlockAndUpdate(absolute.offset(10, 0, 1), Blocks.REDSTONE_BLOCK.defaultBlockState());
            place(level, absolute.offset(11, 0, 0), UUID.randomUUID());
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.NECKLACE.get(), 2));
            var xp = player.experienceLevel;
            test.assertTrue(Telepads.NECKLACE.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction(), "Necklace must travel without origin");
            test.assertTrue(player.distanceToSqr(absolute.getX() + .5, absolute.getY() + .2, absolute.getZ() + .5) < .001 && player.getMainHandItem().getCount() == 1 && player.experienceLevel == xp, "Nearest portable destination consumes one without XP");
            int returnedString = 0; for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) if (player.getInventory().getItem(slot).is(net.minecraft.world.item.Items.STRING)) returnedString += player.getInventory().getItem(slot).getCount();
            test.assertTrue(returnedString >= 1 && returnedString <= 2, "Necklace returns one or two string");
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(net.minecraft.world.item.Items.STONE, 64));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.NECKLACE.get(), 2));
            Telepads.NECKLACE.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
            int onGround = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(3)).stream().filter(entity -> entity.getItem().is(net.minecraft.world.item.Items.STRING)).mapToInt(entity -> entity.getItem().getCount()).sum();
            test.assertTrue(onGround >= 1 && onGround <= 2 && player.getMainHandItem().getCount() == 1, "Full inventory must leave returned string on ground without losing it");
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Telepads.BEAD.get(), 2));
            test.assertTrue(Telepads.BEAD.get().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND).consumesAction() && player.getMainHandItem().getCount() == 1, "Bead uses a valid same-dimension candidate and consumes once");
            test.assertTrue(player.distanceToSqr(absolute.getX() + .5, absolute.getY() + .2, absolute.getZ() + .5) < .001 || player.distanceToSqr(farther.location().x() + .5, farther.location().y() + .2, farther.location().z() + .5) < .001, "Random bead can only arrive at valid accessible candidates");
        } finally { TelepadConfig.XP_POINTS.set(oldPortableXp); level.getServer().getPlayerList().remove(player); }
        test.succeed();
    }
    @GameTest public static void sharingAndPhysicalDiscovery(GameTestHelper test) {
        var level = test.getLevel();
        var server = level.getServer();
        var pos = new BlockPos(1, 1, 1);
        var absolute = test.absolutePos(pos);
        var alice = new net.minecraft.server.level.ServerPlayer(server, level,
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "TelepadAlice"), net.minecraft.server.level.ClientInformation.createDefault());
        var bob = connected(test);
        test.setBlock(pos, Telepads.TELEPAD_BLOCK.get());
        var catalog = TelepadCatalog.get(server);
        var entry = catalog.place(TelepadBlock.location(level, absolute), alice.getUUID());
        var pad = test.getBlockEntity(pos, TelepadBlockEntity.class);
        pad.setIdentity(entry.id(), entry.name());
        test.assertTrue(!catalog.visibleTo(bob.getUUID()).contains(entry), "An unknown private pad must be hidden");
        bob.setPos(absolute.getX() + 0.5, absolute.getY() + 0.2, absolute.getZ() + 0.5);
        bob.setShiftKeyDown(true);
        test.useBlock(pos, bob);
        test.assertTrue(catalog.find(entry.id()).canUse(bob.getUUID()), "Local interaction must register the actor");
        test.useBlock(pos, bob);
        test.assertTrue(!catalog.find(entry.id()).canUse(bob.getUUID()), "Second interaction must remove only the actor");
        test.assertTrue(catalog.find(entry.id()).canUse(alice.getUUID()), "Alice registration must be unchanged");
        try {
            subaraki.telepads.server.FriendsService.handle(alice, new TelepadNetwork.FriendRequest(1, UUID.randomUUID(), bob.getGameProfile().name()));
            test.assertTrue(catalog.preferences(alice.getUUID()).friends().getFirst().id().equals(bob.getUUID()), "Friend identity must come from the online server player");
            subaraki.telepads.server.FriendsService.handle(alice, new TelepadNetwork.FriendRequest(1, bob.getUUID(), bob.getGameProfile().name()));
            test.assertTrue(catalog.preferences(alice.getUUID()).friends().size() == 1, "Duplicate friend must be rejected");
            alice.setPos(absolute.getX() + 0.5, absolute.getY() + 0.2, absolute.getZ() + 0.5);
            var naming = NamingService.open(alice, entry);
            test.assertTrue(NamingService.confirm(alice, new TelepadNetwork.NameRequest(naming.token(), "Shared", true)), "Sharing through the naming form must succeed");
            subaraki.telepads.server.FriendsService.handle(alice, new TelepadNetwork.FriendRequest(2, bob.getUUID(), ""));
            test.assertTrue(catalog.find(entry.id()).canUse(bob.getUUID()), "Removing a friend must preserve prior destination access");
            test.assertTrue(catalog.preferences(bob.getUUID()).friends().isEmpty(), "Alice's list changes must not alter Bob's list");
            var clonedAlice = new net.minecraft.server.level.ServerPlayer(server, server.getLevel(net.minecraft.world.level.Level.NETHER),
                alice.getGameProfile(), net.minecraft.server.level.ClientInformation.createDefault());
            test.assertTrue(catalog.find(entry.id()).canUse(clonedAlice.getUUID()), "A replacement player entity in another dimension must retain access");
        } finally { server.getPlayerList().remove(bob); }
        test.succeed();
    }
    @GameTest public static void namingRequiresPhysicalPlacement(GameTestHelper test) {
        var pos = new BlockPos(1, 1, 1);
        test.setBlock(pos, Telepads.TELEPAD_BLOCK.get());
        var level = test.getLevel();
        var absolute = test.absolutePos(pos);
        var player = (net.minecraft.server.level.ServerPlayer)test.makeMockServerPlayer(GameType.CREATIVE);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 0.2, absolute.getZ() + 0.5);
        var block = (TelepadBlock)Telepads.TELEPAD_BLOCK.get();
        block.setPlacedBy(level, absolute, level.getBlockState(absolute), player, new ItemStack(Telepads.TELEPAD.get()));
        var catalog = TelepadCatalog.get(level.getServer());
        var entry = catalog.find(TelepadBlock.location(level, absolute));
        test.assertTrue(entry != null && entry.name().equals("Telepad") && entry.canUse(player.getUUID()), "Placement must register a default name immediately");
        var canceled = NamingService.open(player, entry);
        NamingService.sessions(level.getServer()).cancel(player.getUUID(), canceled.token());
        test.assertTrue(catalog.find(entry.id()).name().equals("Telepad"), "Closing without confirmation must preserve the default");
        test.assertTrue(!NamingService.confirm(player, new TelepadNetwork.NameRequest(canceled.token(), "Forged", false)), "Canceled contexts cannot rename");
        var valid = NamingService.open(player, entry);
        test.assertTrue(NamingService.confirm(player, new TelepadNetwork.NameRequest(valid.token(), "NamedPad", false)), "Valid physical placement must rename");
        test.assertTrue(catalog.find(entry.id()).name().equals("NamedPad"), "Server must persist confirmed name");
        test.assertTrue(!NamingService.confirm(player, new TelepadNetwork.NameRequest(valid.token(), "Replay", false)), "Name context must be consumed exactly once");
        test.succeed();
    }

    @GameTest public static void platformPersistenceAndReplacement(GameTestHelper test) {
        var pos = new BlockPos(1, 1, 1);
        test.setBlock(pos, Telepads.TELEPAD_BLOCK.get());
        var level = test.getLevel();
        var absolute = test.absolutePos(pos);
        var catalog = TelepadCatalog.get(level.getServer());
        var location = TelepadBlock.location(level, absolute);
        var entry = catalog.place(location, UUID.randomUUID());
        var pad = test.getBlockEntity(pos, TelepadBlockEntity.class);
        pad.setIdentity(entry.id(), "SavedPad");
        var saved = pad.saveWithFullMetadata(level.registryAccess());
        var loaded = new TelepadBlockEntity(absolute, pad.getBlockState());
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved));
        test.assertTrue(entry.id().equals(loaded.identity()), "Saved identity must survive block-entity reload");
        test.assertTrue("SavedPad".equals(loaded.padName()), "Saved name must survive block-entity reload");
        var shape = pad.getBlockState().getCollisionShape(level, absolute);
        test.assertTrue(Math.abs(shape.bounds().maxY - 0.2) < 0.001, "Platform collision must be 0.2 blocks high");
        test.setBlock(pos, Blocks.AIR);
        test.assertTrue(catalog.find(entry.id()).missing(), "Removing the physical block must mark the destination missing");
        test.setBlock(pos, Telepads.TELEPAD_BLOCK.get());
        var replacement = catalog.place(location, UUID.randomUUID());
        test.assertTrue(!replacement.id().equals(entry.id()), "Replacement must have a new identity");
        test.assertTrue(catalog.find(entry.id()) == null, "Old activation identity must be invalidated");
        test.succeed();
    }
}
