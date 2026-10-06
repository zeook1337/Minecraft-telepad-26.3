package subaraki.telepads;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import subaraki.telepads.server.SafeArrival;
import subaraki.telepads.server.TravelService;
import subaraki.telepads.network.TelepadNetwork;
import subaraki.telepads.data.TelepadCatalog;
import subaraki.telepads.block.TelepadBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import java.util.List;

public final class ArrivalGameTests {
    // Retain real hazard states while assembling adjacent fixtures, including a
    // body-only fire contact without triggering placement/survival updates.
    private static final int FIXTURE_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS
        | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE | net.minecraft.world.level.block.Block.UPDATE_SKIP_ON_PLACE;
    private static void arena(ServerLevel level, BlockPos anchor, BlockState floor, BlockState hazard) {
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            for (int y = -5; y <= 6; y++) level.setBlock(anchor.offset(x,y,z), Blocks.AIR.defaultBlockState(), FIXTURE_FLAGS);
            level.setBlock(anchor.offset(x,-1,z), floor, FIXTURE_FLAGS);
            level.setBlock(anchor.offset(x,0,z), hazard, FIXTURE_FLAGS);
        }
    }
    public static void hazards(GameTestHelper test) {
        var player = TelepadGameTests.connected(test); var level = test.getLevel();
        var anchor = test.absolutePos(new BlockPos(64,40,64));
        try {
            var hazards = List.of(Blocks.FIRE.defaultBlockState(), Blocks.SOUL_FIRE.defaultBlockState(),
                Blocks.LAVA.defaultBlockState(), Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.CACTUS.defaultBlockState(),
                Blocks.CAMPFIRE.defaultBlockState(), Blocks.SOUL_CAMPFIRE.defaultBlockState(),
                Blocks.SWEET_BERRY_BUSH.defaultBlockState(), Blocks.WITHER_ROSE.defaultBlockState(), Blocks.POWDER_SNOW.defaultBlockState());
            var unsafe = new java.util.ArrayList<String>();
            for (var hazard : hazards) {
                arena(level, anchor, Blocks.STONE.defaultBlockState(), hazard);
                test.assertTrue(level.getBlockState(anchor).equals(hazard), "Hazard fixture must retain its real block state");
                if (SafeArrival.find(level,player,anchor).isPresent()) unsafe.add(hazard.toString());
            }
            // Protective equipment, immunity and crouching do not change the arrival contract.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 600));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_BOOTS));
            player.setShiftKeyDown(true);
            for (var hazard : hazards) {
                arena(level, anchor, Blocks.STONE.defaultBlockState(), hazard);
                if (SafeArrival.find(level,player,anchor).isPresent()) unsafe.add("protected " + hazard);
            }
            arena(level,anchor,Blocks.MAGMA_BLOCK.defaultBlockState(),Blocks.AIR.defaultBlockState());
            if (SafeArrival.find(level,player,anchor).isPresent()) unsafe.add("magma support");
            arena(level,anchor,Blocks.STONE.defaultBlockState(),Blocks.AIR.defaultBlockState());
            for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) level.setBlock(anchor.offset(x,1,z),Blocks.FIRE.defaultBlockState(),FIXTURE_FLAGS);
            if (SafeArrival.find(level,player,anchor).isPresent()) unsafe.add("upper body fire");
            test.assertTrue(unsafe.isEmpty(), "Unsafe arrivals accepted: " + unsafe);
        } finally { level.getServer().getPlayerList().remove(player); }
        test.succeed();
    }
    private record Resources(net.minecraft.world.phys.Vec3 position, int levels, float progress, int total, List<ItemStack> inventory) {
        static Resources capture(ServerPlayer player) {
            var inventory = new java.util.ArrayList<ItemStack>();
            for (int i=0;i<player.getInventory().getContainerSize();i++) inventory.add(player.getInventory().getItem(i).copy());
            return new Resources(player.position(),player.experienceLevel,player.experienceProgress,player.totalExperience,inventory);
        }
        void unchanged(GameTestHelper test, ServerPlayer player, String flow) {
            test.assertTrue(position.equals(player.position()) && levels==player.experienceLevel && progress==player.experienceProgress && total==player.totalExperience, flow+" preserves position and XP");
            for (int i=0;i<inventory.size();i++) test.assertTrue(ItemStack.matches(inventory.get(i),player.getInventory().getItem(i)),flow+" preserves inventory slot "+i);
        }
    }
    public static void travelPaths(GameTestHelper test) {
        var player = TelepadGameTests.connected(test); var level = test.getLevel(); var server = level.getServer();
        var catalog = TelepadCatalog.get(server);
        var originPos = test.absolutePos(new BlockPos(0,40,160)); var targetPos = originPos.offset(24,0,0);
        var origin = TelepadGameTests.place(level,originPos,player.getUUID());
        int levels=TelepadConfig.XP_LEVELS.get(),points=TelepadConfig.XP_POINTS.get(),wait=TelepadConfig.WAIT_SECONDS.get();
        var configured=TelepadConfig.DESTINATIONS.get(); boolean bead=TelepadConfig.BEAD.get(),necklace=TelepadConfig.NECKLACE.get();
        try {
            player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); player.giveExperienceLevels(20);
            TelepadConfig.XP_LEVELS.set(2); TelepadConfig.XP_POINTS.set(999999);
            TelepadConfig.BEAD.set(true); TelepadConfig.NECKLACE.set(true);
            arena(level,targetPos,Blocks.MAGMA_BLOCK.defaultBlockState(),Blocks.AIR.defaultBlockState());
            var target=TelepadGameTests.place(level,targetPos,player.getUUID());
            level.setBlock(targetPos.above(),Blocks.WITHER_ROSE.defaultBlockState(),FIXTURE_FLAGS);
            var session=TelepadGameTests.open(player,origin,List.of(target.id())); var before=Resources.capture(player);
            test.assertTrue(!TravelService.handle(player,new TelepadNetwork.TravelRequest(session.token(),2,target.id(),0,0)),"Hazardous active destination rejects");
            before.unchanged(test,player,"active rejection");
            var fallback=targetPos.offset(3,0,3); level.setBlock(fallback.below(),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS);
            session=TelepadGameTests.open(player,origin,List.of(target.id())); int xp=player.experienceLevel;
            test.assertTrue(TravelService.handle(player,new TelepadNetwork.TravelRequest(session.token(),2,target.id(),0,0)),"Active destination uses safe fallback");
            test.assertTrue(player.getX()==fallback.getX()+.5 && player.getZ()==fallback.getZ()+.5 && player.experienceLevel==xp-2,"Active fallback arrives safely and charges once");
            test.assertTrue(!TravelService.handle(player,new TelepadNetwork.TravelRequest(session.token(),2,target.id(),0,0)) && player.experienceLevel==xp-2,"Replay cannot add a charge");

            level.removeBlock(targetPos,false); level.setBlock(targetPos.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),FIXTURE_FLAGS);
            level.setBlock(fallback.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),FIXTURE_FLAGS);
            session=TelepadGameTests.open(player,origin,List.of(target.id())); before=Resources.capture(player);
            test.assertTrue(!TravelService.handle(player,new TelepadNetwork.TravelRequest(session.token(),3,target.id(),0,0)),"Confirmed missing destination rejects hazardous arrival");
            before.unchanged(test,player,"missing rejection");
            level.setBlock(fallback.below(),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS);
            session=TelepadGameTests.open(player,origin,List.of(target.id())); xp=player.experienceLevel;
            test.assertTrue(TravelService.handle(player,new TelepadNetwork.TravelRequest(session.token(),3,target.id(),0,0)) && player.experienceLevel==xp-2,"Missing fallback charges only once");
            test.assertTrue(player.getX()==fallback.getX()+.5 && player.getZ()==fallback.getZ()+.5,"Missing fallback avoids magma");

            String fixed=targetPos.getX()+"/"+targetPos.getY()+"/"+targetPos.getZ()+"/minecraft:overworld/Hazard";
            var pad=(TelepadBlockEntity)level.getBlockEntity(originPos);
            pad.setConfigured(fixed); TelepadConfig.DESTINATIONS.set(List.of(fixed)); TelepadConfig.WAIT_SECONDS.set(0);
            level.setBlock(fallback.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),FIXTURE_FLAGS);
            TelepadGameTests.open(player,origin,List.of()); TravelService.sessions(server).clear(player.getUUID()); before=Resources.capture(player);
            TravelService.tick(player); before.unchanged(test,player,"configured rejection");
            level.setBlock(fallback.below(),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS);
            TelepadGameTests.open(player,origin,List.of()); TravelService.sessions(server).clear(player.getUUID()); xp=player.experienceLevel;
            TravelService.tick(player);
            test.assertTrue(player.getX()==fallback.getX()+.5 && player.getZ()==fallback.getZ()+.5 && player.experienceLevel==xp,"Configured fallback uses zero cost");
            pad.setConfigured("");

            // Only the target remains an authorized portable candidate.
            catalog.update(origin.id(),value->value.register(player.getUUID(),false));
            target=TelepadGameTests.place(level,targetPos,player.getUUID());
            level.setBlock(targetPos.above(),Blocks.WITHER_ROSE.defaultBlockState(),FIXTURE_FLAGS);
            for (var portable:List.of(Telepads.BEAD.get(),Telepads.NECKLACE.get())) {
                level.setBlock(fallback.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),FIXTURE_FLAGS);
                player.setPos(originPos.getX()+.5,originPos.getY()+.2,originPos.getZ()+.5);
                player.getInventory().clearContent(); player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(portable,2));
                before=Resources.capture(player);
                long ground=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(4)).stream().filter(v->v.getItem().is(Items.STRING)).count();
                test.assertTrue(!portable.use(level,player,InteractionHand.MAIN_HAND).consumesAction(),"Unsafe portable travel rejects");
                before.unchanged(test,player,"portable rejection");
                test.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,player.getBoundingBox().inflate(4)).stream().filter(v->v.getItem().is(Items.STRING)).count()==ground,"Failed necklace cannot return string");
                level.setBlock(fallback.below(),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS); xp=player.experienceLevel;
                test.assertTrue(portable.use(level,player,InteractionHand.MAIN_HAND).consumesAction(),"Portable uses safe fallback");
                test.assertTrue(player.getX()==fallback.getX()+.5 && player.getZ()==fallback.getZ()+.5 && player.getMainHandItem().getCount()==1 && player.experienceLevel==xp,"Portable fallback consumes exactly one and preserves XP");
                int string=0; for (int i=0;i<player.getInventory().getContainerSize();i++) if (player.getInventory().getItem(i).is(Items.STRING)) string+=player.getInventory().getItem(i).getCount();
                test.assertTrue(portable==Telepads.NECKLACE.get() ? string>=1 && string<=2 : string==0,"Only successful necklace returns its bounded recovery");
            }
        } finally {
            TelepadConfig.XP_LEVELS.set(levels); TelepadConfig.XP_POINTS.set(points); TelepadConfig.WAIT_SECONDS.set(wait);
            TelepadConfig.DESTINATIONS.set(configured); TelepadConfig.BEAD.set(bead); TelepadConfig.NECKLACE.set(necklace);
            server.getPlayerList().remove(player);
        }
        test.succeed();
    }
    public static void geometry(GameTestHelper test) {
        var player = TelepadGameTests.connected(test); var level = test.getLevel();
        var anchor = test.absolutePos(new BlockPos(96,40,96));
        try {
            for (var safe : List.of(Blocks.AIR.defaultBlockState(),Blocks.STONE.defaultBlockState(),Blocks.OAK_SLAB.defaultBlockState(),
                    Telepads.TELEPAD_BLOCK.get().defaultBlockState(),Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false),
                    Blocks.SOUL_CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT,false))) {
                arena(level,anchor,Blocks.STONE.defaultBlockState(),safe);
                var arrival = SafeArrival.find(level,player,anchor);
                test.assertTrue(arrival.isPresent(), "Safe ordinary terrain must allow " + safe);
                double height = safe.getCollisionShape(level,anchor).isEmpty() ? 0 : safe.getCollisionShape(level,anchor).max(net.minecraft.core.Direction.Axis.Y);
                test.assertTrue(Math.abs(arrival.orElseThrow().y - anchor.getY() - height) < .000001, "Retain fractional standing height");
            }
            arena(level,anchor,Blocks.MAGMA_BLOCK.defaultBlockState(),Blocks.AIR.defaultBlockState());
            level.setBlock(anchor,Telepads.TELEPAD_BLOCK.get().defaultBlockState(),FIXTURE_FLAGS);
            var arrival = SafeArrival.find(level,player,anchor).orElseThrow();
            test.assertTrue(Math.abs(arrival.y-anchor.getY()-.2)<.000001, "Telepad surface isolates player from magma below");
            level.setBlock(anchor,Blocks.CAMPFIRE.defaultBlockState(),FIXTURE_FLAGS);
            test.assertTrue(SafeArrival.find(level,player,anchor).isEmpty(), "Fractional lit campfire contact must reject");
            // A unique safe alternative exercises order and the full horizontal bound.
            var fallback = anchor.offset(3,0,3);
            level.setBlock(anchor,Blocks.AIR.defaultBlockState(),FIXTURE_FLAGS);
            level.setBlock(fallback.below(),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS);
            arrival = SafeArrival.find(level,player,anchor).orElseThrow();
            test.assertTrue(arrival.x==fallback.getX()+.5 && arrival.z==fallback.getZ()+.5, "Search must choose safe fallback at radius three");
            level.setBlock(fallback.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),FIXTURE_FLAGS);
            test.assertTrue(SafeArrival.find(level,player,anchor).isEmpty(), "Exhausted hazardous search must reject");
            level.setBlock(anchor.offset(4,-1,0),Blocks.STONE.defaultBlockState(),FIXTURE_FLAGS);
            test.assertTrue(SafeArrival.find(level,player,anchor).isEmpty(), "Do not extend search or create platforms");
        } finally { level.getServer().getPlayerList().remove(player); }
        test.succeed();
    }
}
