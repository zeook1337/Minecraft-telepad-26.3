package telepads.fixture;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import subaraki.telepads.Telepads;
import subaraki.telepads.TelepadConfig;
import subaraki.telepads.block.*;
import subaraki.telepads.data.*;
import subaraki.telepads.server.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;

/** Local acceptance mod only. Uses real connected players and normal client requests. */
@Mod.EventBusSubscriber(modid = "telepads_fixture")
public final class ReleaseScaleServer {
    static final String[] NAMES = {"ScaleAlice", "ScaleBob", "ScaleCarol", "ScaleDave"};
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final UUID FIXTURE_OWNER = UUID.fromString("9e4a234f-620a-440a-b1fa-c2a76f3524c8");
    private static final Map<String, Long> counts = new TreeMap<>();
    private static final Map<String, Integer> logins = new TreeMap<>();
    private static final Map<String, Long> gcIds = new HashMap<>();
    private static final List<Map<String, Object>> ticks = new ArrayList<>(), heaps = new ArrayList<>();
    private static final Actor[] actors = {new Actor(), new Actor(), new Actor(), new Actor()};
    private static int seeded, tickCounter, measuredStartTick;
    private static long allConnectedAt, started, measuredStart, lastSample;
    private static boolean finished, snapshotSaved;
    private static JsonElement expected;
    private static final class Actor {
        int phase, rounds, xp, items, age; boolean pending;
        UUID token; net.minecraft.world.phys.Vec3 position;
        String dimension;
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException("Release scale: " + message);
    }
    private static void count(String name) { counts.merge(name, 1L, Long::sum); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (Boolean.getBoolean("telepads.releaseScale")) logins.merge(event.getEntity().getName().getString(), 1, Integer::sum);
    }
    private static Path directory() { return Path.of(System.getProperty("telepads.scaleEvidence")); }
    private static JsonElement catalog(net.minecraft.server.MinecraftServer server) {
        return ReleaseBaselineFixture.normalize(TelepadCatalog.CODEC.encodeStart(JsonOps.INSTANCE, TelepadCatalog.get(server)).getOrThrow());
    }
    private static TelepadEntry named(net.minecraft.server.MinecraftServer server, String name) {
        return TelepadCatalog.get(server).entries().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
    }
    private static void create(ServerLevel level, BlockPos pos, String name, boolean hazard) {
        level.getChunkAt(pos);
        for (int x=-3; x<=3; x++) for (int z=-3; z<=3; z++) {
            level.setBlockAndUpdate(pos.offset(x,-1,z), (hazard ? Blocks.MAGMA_BLOCK : Blocks.STONE).defaultBlockState());
            for (int y=0; y<=4; y++) level.setBlockAndUpdate(pos.offset(x,y,z), Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(pos, Telepads.TELEPAD_BLOCK.get().defaultBlockState());
        var catalog=TelepadCatalog.get(level.getServer());
        var entry=catalog.place(TelepadBlock.location(level,pos), FIXTURE_OWNER);
        catalog.update(entry.id(), v -> v.rename(name).withPublic(!name.startsWith("Private")));
        var pad=(TelepadBlockEntity)level.getBlockEntity(pos);
        pad.setIdentity(entry.id(), name); pad.install(true);
        if (hazard) level.removeBlock(pos, false);
    }
    private static void seed(net.minecraft.server.MinecraftServer server) throws Exception {
        if (seeded==0) {
            require(TelepadCatalog.get(server).entries().isEmpty(), "scale world must be new");
            TelepadConfig.WAIT_SECONDS.set(60); TelepadConfig.XP_LEVELS.set(2); TelepadConfig.XP_POINTS.set(99999);
            TelepadConfig.DRAGON_BLOCK.set(false); // Fixture isolates transport from the dragon rule, covered by GameTests.
            TelepadConfig.DESTINATIONS.set(List.of());
        }
        int total=Integer.getInteger("telepads.scaleDestinations",1000);
        for (int n=0; n<8 && seeded<total; n++,seeded++) {
            int i=seeded; var dimension=i%3==0 ? Level.OVERWORLD : i%3==1 ? Level.NETHER : Level.END;
            int x=64+(i/3%20)*16, z=64+(i/60)*16;
            if (i==996) { x=8192; z=8192; }
            create(server.getLevel(dimension),new BlockPos(x,100,z),String.format(Locale.ROOT,"S%04d",i),false);
        }
        if (seeded<total || snapshotSaved) return;
        for (int i=0; i<4; i++) {
            create(server.overworld(),new BlockPos(i*16,100,-64),"Origin"+i,false);
            create(server.overworld(),new BlockPos(i*16,100,-96),"Hazard"+i,true);
            create(server.overworld(),new BlockPos(i*16,100,-128),"Private"+i,false);
        }
        expected=catalog(server); snapshotSaved=true;
        Files.writeString(directory().resolve("expected-catalog.json"),JSON.toJson(expected));
        Telepads.LOGGER.info("RELEASE_SCALE_SEEDED {} destinations across three dimensions", total);
    }
    private static void start(ServerPlayer player, int i, Actor actor) {
        var server=player.level().getServer(); var origin=named(server,"Origin"+i);
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(server.overworld(),origin.location().x()+.5,100.2,origin.location().z()+.5,Set.of(),0,0,true);
        player.giveExperienceLevels(-player.experienceLevel); player.giveExperienceLevels(30);
        player.getInventory().clearContent(); TravelService.sessions(server).clear(player.getUUID());
        TravelService.sessions(server).ready(player.getUUID(),origin.id(),server.overworld().getGameTime(),0);
        actor.xp=player.experienceLevel; actor.items=0; actor.position=player.position();
        actor.dimension=player.level().dimension().identifier().toString(); actor.age=0;
        if (actor.phase==4 || actor.phase==5) {
            player.getInventory().setItem(0,new ItemStack(actor.phase==4 ? Telepads.NECKLACE.get() : Telepads.BEAD.get(),2));
            actor.items=2;
        } else if (actor.phase!=7) {
            var visible=TelepadCatalog.get(server).visibleTo(player.getUUID()).stream().filter(e -> !e.id().equals(origin.id())).toList();
            require(visible.stream().noneMatch(e -> e.name().startsWith("Private")),"unauthorized destination exposure");
            var session=TravelService.sessions(server).open(player.getUUID(),origin.id(),origin.location(),server.overworld().getGameTime(),visible.stream().map(TelepadEntry::id).toList());
            actor.token=session.token(); TravelService.sendView(player,session,0,0);
        }
        actor.pending=true;
    }
    private static boolean poll(ServerPlayer player, int i, Actor actor) {
        var server=player.level().getServer(); actor.age++;
        if (actor.phase==7) {
            if (logins.getOrDefault(NAMES[i],0)<=actor.rounds+1) return false;
            count("reconnect"); return true;
        }
        if (actor.age<40) return false;
        boolean complete;
        if (actor.phase==4 || actor.phase==5) {
            complete=player.getInventory().getItem(0).getCount()==1;
            if (!complete) return false;
            require(player.experienceLevel==actor.xp,"portable XP changed");
            var visible=TelepadCatalog.get(server).visibleTo(player.getUUID());
            require(visible.stream().anyMatch(e -> !e.missing() && near(player,e)),"portable unsafe/unauthorized arrival");
            int string=0, other=0;
            for (int slot=1; slot<player.getInventory().getContainerSize(); slot++) {
                var stack=player.getInventory().getItem(slot);
                if (stack.is(net.minecraft.world.item.Items.STRING)) string+=stack.getCount(); else other+=stack.getCount();
            }
            require(other==0 && (actor.phase==4 ? string>=1 && string<=2 : string==0),"portable duplication/recovery changed");
            count(actor.phase==4 ? "necklace" : "bead");
        } else {
            complete=TravelService.sessions(server).find(player.getUUID(),actor.token,server.overworld().getGameTime())==null;
            if (!complete) return false;
            if (actor.phase==2 || actor.phase==3) {
                require(player.position().distanceToSqr(actor.position)<.01 && player.experienceLevel==actor.xp && player.getInventory().getItem(0).isEmpty(),"rejection moved/charged/consumed");
                count(actor.phase==2 ? "hazardRejection" : "unauthorizedRejection");
            } else {
                String target=actor.phase==0 ? "S0997" : actor.phase==1 ? "S0998" : "S0996";
                require(near(player,named(server,target)) && player.experienceLevel==actor.xp-2,"unsafe arrival/duplicate or missing XP charge");
                require(player.getInventory().getItem(0).isEmpty(),"travel duplicated inventory");
                count(actor.phase==6 ? "coldArrival" : "travel");
            }
            count("paging");
        }
        require(expected.equals(catalog(server)),"catalog/data changed during travel");
        count("invariantChecks"); return true;
    }
    private static boolean near(ServerPlayer player, TelepadEntry entry) {
        return player.level().dimension().identifier().toString().equals(entry.location().dimension())
            && player.distanceToSqr(entry.location().x()+.5,entry.location().y()+.2,entry.location().z()+.5)<4;
    }
    private static void sample(net.minecraft.server.MinecraftServer server, long now) {
        double seconds=(now-measuredStart)/1e9;
        ticks.add(Map.of("seconds",seconds,"ticks",tickCounter-measuredStartTick,"clients",server.getPlayerList().getPlayerCount(),
            "scheduledReconnect",Arrays.stream(actors).anyMatch(a -> a.pending && a.phase==7),"operations",new TreeMap<>(counts)));
        Set<String> heapPools=new HashSet<>();
        for (var pool:ManagementFactory.getMemoryPoolMXBeans()) if (pool.getType()==MemoryType.HEAP) heapPools.add(pool.getName());
        for (var raw:ManagementFactory.getGarbageCollectorMXBeans()) if (raw instanceof com.sun.management.GarbageCollectorMXBean bean) {
            var gc=bean.getLastGcInfo();
            if (gc==null || gcIds.getOrDefault(bean.getName(),-1L)==gc.getId()) continue;
            gcIds.put(bean.getName(),gc.getId());
            // Use the JVM's post-event pool values, never Runtime.freeMemory or forced GC.
            long bytes=gc.getMemoryUsageAfterGc().entrySet().stream().filter(e -> heapPools.contains(e.getKey())).mapToLong(e -> e.getValue().getUsed()).sum();
            long gcEndNanos=started+((gc.getEndTime()-jvmStartMillis)*1_000_000L);
            double gcSeconds=(gcEndNanos-measuredStart)/1e9;
            if (gcSeconds>=0) heaps.add(Map.of("seconds",gcSeconds,"bytes",bytes,"collector",bean.getName(),"id",gc.getId()));
        }
        lastSample=now;
    }
    private static long jvmStartMillis;
    private static void report(String status, String error) throws Exception {
        var value=new LinkedHashMap<String,Object>();
        value.put("status",status); value.put("error",error); value.put("destinations",seeded);
        value.put("seconds",measuredStart==0 ? 0 : (System.nanoTime()-measuredStart)/1e9);
        value.put("warmupSeconds",Integer.getInteger("telepads.scaleWarmup",600));
        value.put("operations",counts); value.put("logins",logins); value.put("tickSamples",ticks); value.put("postGcSamples",heaps);
        value.put("connectedClients",Arrays.stream(NAMES).filter(n -> logins.getOrDefault(n,0)>0).count());
        value.put("clientNames",NAMES); value.put("heapMaximum",Runtime.getRuntime().maxMemory());
        Files.writeString(directory().resolve("scale-workload.json"),JSON.toJson(value));
    }
    private static void restart(net.minecraft.server.MinecraftServer server) throws Exception {
        if (server.getTickCount()<40) return;
        var original=JsonParser.parseString(Files.readString(directory().resolve("expected-catalog.json")));
        require(ReleaseBaselineFixture.normalize(original).equals(catalog(server)),"catalog lost after restart");
        for (var entry:TelepadCatalog.get(server).entries()) {
            var level=TravelService.dimension(server,entry.location().dimension()); var pos=TravelService.pos(entry.location()); level.getChunkAt(pos);
            if (entry.missing()) require(!(level.getBlockEntity(pos) instanceof TelepadBlockEntity),"missing block reappeared");
            else require(level.getBlockEntity(pos) instanceof TelepadBlockEntity pad && entry.id().equals(pad.identity()) && entry.name().equals(pad.padName()) && pad.transmitter(),"physical state lost after restart");
        }
        Files.writeString(directory().resolve("scale-restart.json"),"{\"status\":\"PASS\"}");
        Telepads.LOGGER.info("RELEASE_SCALE_RESTART_PASS: identities, names, locations, permissions, missing state and upgrades"); finished=true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent.Post event) throws Exception {
        if (!Boolean.getBoolean("telepads.releaseScale") || finished) return;
        if (started==0) { started=System.nanoTime(); jvmStartMillis=ManagementFactory.getRuntimeMXBean().getUptime(); }
        tickCounter++;
        try {
            var server=event.server();
            if (Boolean.getBoolean("telepads.scaleRestart")) { restart(server); return; }
            seed(server); if (!snapshotSaved) return;
            var players=Arrays.stream(NAMES).map(server.getPlayerList()::getPlayerByName).toArray(ServerPlayer[]::new);
            long now=System.nanoTime();
            if (Arrays.stream(players).allMatch(Objects::nonNull)) {
                if (allConnectedAt==0) allConnectedAt=now;
                if (measuredStart==0 && (now-allConnectedAt)/1e9>=Integer.getInteger("telepads.scaleWarmup",600)) {
                    measuredStart=now; measuredStartTick=tickCounter; counts.clear(); heaps.clear(); ticks.clear();
                    sample(server,now);
                }
            }
            if (!Arrays.stream(players).allMatch(Objects::nonNull) && measuredStart==0) allConnectedAt=0;
            if (measuredStart!=0 && (now-lastSample)>5_000_000_000L) { sample(server,now); report("RUNNING",""); }
            for (int i=0; i<4; i++) {
                var player=players[i]; var actor=actors[i]; if (player==null) continue;
                var standing=TravelService.standingPad(player);
                if (standing!=null) TravelService.sessions(server).ready(player.getUUID(),standing.identity(),server.overworld().getGameTime(),0);
                if (actor.pending) {
                    if (poll(player,i,actor)) {
                        actor.pending=false; actor.phase=(actor.phase+1)%8;
                        if (actor.phase==0) actor.rounds++;
                        if (actor.phase==6) {
                            var origin=named(server,"Origin"+i);
                            player.teleportTo(server.overworld(),origin.location().x()+.5,100.2,origin.location().z()+.5,Set.of(),0,0,true);
                        }
                    } else require(actor.age<6000,"operation stalled for "+NAMES[i]+" phase "+actor.phase);
                } else if (tickCounter%100==0 && (measuredStart==0 || (now-measuredStart)/1e9<Integer.getInteger("telepads.scaleSeconds",14400))) {
                    if (actor.phase==7 && Arrays.stream(actors).anyMatch(a -> a.pending && a.phase==7)) continue;
                    if (actor.phase==6 && server.overworld().hasChunkAt(TravelService.pos(named(server,"S0996").location()))) continue;
                    start(player,i,actor);
                }
            }
            if (measuredStart!=0 && (now-measuredStart)/1e9>=Integer.getInteger("telepads.scaleSeconds",14400)
                && Arrays.stream(actors).noneMatch(a -> a.pending)) {
                sample(server,now); require(expected.equals(catalog(server)),"final catalog changed");
                report("COMPLETED",""); finished=true;
                Telepads.LOGGER.info("RELEASE_SCALE_INTERVAL_COMPLETE: qualifying limits require evaluator and restart");
            }
        } catch (Exception exception) {
            report("FAILED",exception.getMessage()); finished=true; throw exception;
        }
    }
}
