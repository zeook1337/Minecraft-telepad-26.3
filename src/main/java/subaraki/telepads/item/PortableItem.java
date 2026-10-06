package subaraki.telepads.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AnvilUpdateEvent;
import subaraki.telepads.Telepads;
import subaraki.telepads.TelepadConfig;
import subaraki.telepads.block.TelepadBlockEntity;
import subaraki.telepads.data.TelepadCatalog;
import subaraki.telepads.data.TelepadEntry;
import subaraki.telepads.server.SafeArrival;
import subaraki.telepads.server.TravelService;
import java.util.Comparator;

public final class PortableItem extends Item {
    private final boolean necklace;
    public PortableItem(boolean necklace, Properties properties) { super(properties); this.necklace = necklace; }
    @Override public InteractionResult use(Level level, Player actor, InteractionHand hand) {
        if (!(actor instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!(necklace ? TelepadConfig.NECKLACE : TelepadConfig.BEAD).get()) { TravelService.message(player, "portable_disabled"); return InteractionResult.FAIL; }
        var candidates = TelepadCatalog.get(player.level().getServer()).visibleTo(player.getUUID()).stream()
            .filter(entry -> entry.location().dimension().equals(player.level().dimension().identifier().toString()) && !entry.missing())
            .filter(entry -> valid(player, entry)).sorted(Comparator.comparingDouble(entry -> player.distanceToSqr(entry.location().x() + .5, entry.location().y() + .2, entry.location().z() + .5))).toList();
        if (candidates.isEmpty()) { TravelService.message(player, "no_portable_destination"); return InteractionResult.FAIL; }
        var destination = candidates.get(necklace ? 0 : player.getRandom().nextInt(candidates.size()));
        var stack = actor.getItemInHand(hand);
        if (!TravelService.travel(player, destination, false, false)) return InteractionResult.FAIL;
        if (!actor.isCreative()) {
            stack.shrink(1);
            if (necklace) { var string = new ItemStack(Items.STRING, player.getRandom().nextInt(2) + 1); if (!actor.getInventory().add(string)) actor.drop(string, false, net.minecraft.util.Prediction.SERVER_ONLY); }
        }
        return InteractionResult.SUCCESS_SERVER;
    }
    public static boolean valid(ServerPlayer player, TelepadEntry entry) {
        var level = player.level(); var pos = TravelService.pos(entry.location());
        if (!level.isInsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        return level.getBlockEntity(pos) instanceof TelepadBlockEntity pad && entry.id().equals(pad.identity()) && !pad.refreshDisabled() && SafeArrival.find(level, player, pos).isPresent();
    }
    public static void registerAnvil() { AnvilUpdateEvent.BUS.addListener((java.util.function.Predicate<AnvilUpdateEvent>)PortableItem::updateAnvil); }
    public static boolean updateAnvil(AnvilUpdateEvent event) {
        if (!event.getLeft().is(Items.ENDER_PEARL) || !event.getRight().is(Items.ENDER_PEARL)) return false;
        int total = event.getLeft().getCount() + event.getRight().getCount();
        // Let vanilla clear the result when conversion is disallowed. Canceling this
        // event returns early in 26.3 and can leave a previous custom result in place.
        if (!TelepadConfig.ANVIL.get() || total < 2 || total > 8) { event.setOutput(ItemStack.EMPTY); return false; }
        event.setOutput(new ItemStack(Telepads.BEAD.get(), total * 2));
        event.setCost(total);
        event.setMaterialCost(event.getRight().getCount());
        return false;
    }
}
