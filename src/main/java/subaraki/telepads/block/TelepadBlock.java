package subaraki.telepads.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import subaraki.telepads.data.TelepadCatalog;
import subaraki.telepads.data.TelepadLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.network.chat.Component;

public final class TelepadBlock extends BaseEntityBlock {
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty DISABLED = net.minecraft.world.level.block.state.properties.BooleanProperty.create("disabled");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty TRANSMITTER = net.minecraft.world.level.block.state.properties.BooleanProperty.create("transmitter");
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty TOGGLER = net.minecraft.world.level.block.state.properties.BooleanProperty.create("toggler");
    public static final VoxelShape PLATFORM = box(0, 0, 0, 16, 3.2, 16);
    public TelepadBlock(Properties properties) { super(properties); registerDefaultState(stateDefinition.any().setValue(DISABLED, false).setValue(TRANSMITTER, false).setValue(TOGGLER, false)); }
    @Override protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) { builder.add(DISABLED, TRANSMITTER, TOGGLER); }
    @Override protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block, net.minecraft.world.level.redstone.Orientation orientation, boolean moved) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof TelepadBlockEntity pad) pad.refreshDisabled();
    }
    @Override protected java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = new java.util.ArrayList<ItemStack>();
        var stack = new ItemStack(subaraki.telepads.Telepads.TELEPAD.get());
        if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof TelepadBlockEntity pad) {
            stack.set(subaraki.telepads.Telepads.COLORS.get(), pad.colors());
            if (pad.toggler()) drops.add(new ItemStack(subaraki.telepads.Telepads.TOGGLER.get()));
            if (pad.transmitter()) drops.add(new ItemStack(subaraki.telepads.Telepads.TRANSMITTER.get()));
        }
        drops.add(stack); return drops;
    }
    private static void give(Player player, ItemStack stack) { if (!player.getInventory().add(stack)) player.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY); }
    @Override protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player actor, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        boolean upgrade = stack.is(subaraki.telepads.Telepads.TRANSMITTER.get()) || stack.is(subaraki.telepads.Telepads.TOGGLER.get());
        var dye = stack.get(net.minecraft.core.component.DataComponents.DYE);
        boolean rod = stack.is(subaraki.telepads.Telepads.PUBLIC_ROD.get()) || stack.is(subaraki.telepads.Telepads.CYCLE_ROD.get());
        if (!upgrade && dye == null && !stack.is(net.minecraft.world.item.Items.WATER_BUCKET) && !rod) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!(level instanceof ServerLevel server) || !(actor instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TelepadBlockEntity pad) || pad.identity() == null || actor.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) > 64) return InteractionResult.FAIL;
        if (upgrade) {
            if (!pad.install(stack.is(subaraki.telepads.Telepads.TRANSMITTER.get()))) return InteractionResult.FAIL;
            if (!actor.isCreative()) stack.shrink(1);
        } else if (dye != null) {
            var colors = pad.colors().dyed(dye.getId());
            if (colors.equals(pad.colors())) return InteractionResult.FAIL;
            pad.setColors(colors); if (!actor.isCreative()) stack.shrink(1);
        } else if (rod) {
            if (!actor.isCreative() && !player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER)) return InteractionResult.FAIL;
            if (stack.is(subaraki.telepads.Telepads.PUBLIC_ROD.get())) {
                var catalog = TelepadCatalog.get(server.getServer()); var entry = catalog.find(pad.identity());
                if (entry == null) return InteractionResult.FAIL;
                catalog.update(entry.id(), current -> current.withPublic(!entry.publicAccess()));
                subaraki.telepads.server.TravelService.message(player, entry.publicAccess() ? "made_private" : "made_public");
            } else {
                var values = subaraki.telepads.TelepadConfig.DESTINATIONS.get(); int index = values.indexOf(pad.configured()) + 1;
                String value = index >= values.size() ? "" : values.get(index);
                pad.setConfigured(value);
                if (value.isEmpty()) subaraki.telepads.server.TravelService.message(player, "normal_mode");
                else { try { player.sendSystemMessage(Component.literal(subaraki.telepads.server.ConfiguredDestination.parse(value).name()), true); }
                    catch (IllegalArgumentException exception) { subaraki.telepads.Telepads.LOGGER.warn("Invalid configured Telepads destination '{}': {}", value, exception.getMessage()); subaraki.telepads.server.TravelService.message(player, "invalid_configured"); } }
            }
        } else {
            var colors = pad.colors();
            if (colors.equals(subaraki.telepads.data.PadColors.DEFAULT)) return InteractionResult.FAIL;
            pad.setColors(subaraki.telepads.data.PadColors.DEFAULT);
            for (int id : colors.recoveredDyes()) {
                var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.withDefaultNamespace(net.minecraft.world.item.DyeColor.byId(id).getName() + "_dye"));
                give(actor, new ItemStack(item));
            }
            if (!actor.isCreative()) actor.setItemInHand(hand, new ItemStack(net.minecraft.world.item.Items.BUCKET));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return PLATFORM; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TelepadBlockEntity(pos, state); }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!subaraki.telepads.TelepadConfig.PARTICLES.get() || state.getValue(DISABLED) || random.nextInt(3) != 0) return;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL, pos.getX() + random.nextDouble(), pos.getY() + .25, pos.getZ() + random.nextDouble(), 0, .1, 0);
    }

    public static TelepadLocation location(Level level, BlockPos pos) {
        return new TelepadLocation(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player actor, BlockHitResult hit) {
        if (!actor.isShiftKeyDown() || !actor.getMainHandItem().isEmpty()) return InteractionResult.PASS;
        if (!(level instanceof ServerLevel server) || !(actor instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 64) return InteractionResult.FAIL;
        var catalog = TelepadCatalog.get(server.getServer());
        var entry = catalog.find(location(level, pos));
        if (entry == null || entry.missing()) return InteractionResult.FAIL;
        String message;
        if (entry.publicAccess()) message = "message.telepads.already_public";
        else {
            boolean registered = entry.users().contains(player.getUUID());
            catalog.toggleRegistration(entry.id(), player.getUUID());
            message = registered ? "message.telepads.unregistered" : "message.telepads.registered";
        }
        if (player.connection != null) player.sendSystemMessage(Component.translatable(message, entry.name()), true);
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level instanceof ServerLevel server && placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof TelepadBlockEntity pad) {
            var entry = TelepadCatalog.get(server.getServer()).place(location(level, pos), player.getUUID());
            pad.setIdentity(entry.id(), entry.name());
            pad.setColors(stack.getOrDefault(subaraki.telepads.Telepads.COLORS.get(), subaraki.telepads.data.PadColors.DEFAULT));
            subaraki.telepads.server.NamingService.open(player, entry);
        }
    }
}
