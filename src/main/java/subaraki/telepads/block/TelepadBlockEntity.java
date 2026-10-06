package subaraki.telepads.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import subaraki.telepads.Telepads;
import subaraki.telepads.data.TelepadCatalog;
import java.util.UUID;

public final class TelepadBlockEntity extends BlockEntity {
    private UUID identity;
    private String name = "Telepad";
    private boolean toggler, transmitter;
    private subaraki.telepads.data.PadColors colors = subaraki.telepads.data.PadColors.DEFAULT;
    private String configured = "";
    public TelepadBlockEntity(BlockPos pos, BlockState state) { super(Telepads.TELEPAD_ENTITY.get(), pos, state); }
    public UUID identity() { return identity; }
    public String padName() { return name; }
    public boolean toggler() { return toggler; }
    public boolean transmitter() { return transmitter; }
    public subaraki.telepads.data.PadColors colors() { return colors; }
    public String configured() { return configured; }
    public void setConfigured(String value) { configured = value; sync(); }
    public void setColors(subaraki.telepads.data.PadColors value) { colors = value; sync(); }
    public boolean install(boolean isTransmitter) {
        if (isTransmitter ? transmitter : toggler) return false;
        if (isTransmitter) transmitter = true; else toggler = true;
        refreshDisabled(); sync(); return true;
    }
    public boolean refreshDisabled() {
        boolean disabled = toggler && level != null && level.hasNeighborSignal(worldPosition);
        if (level instanceof ServerLevel server && identity != null) {
            var state = getBlockState();
            if (state.getValue(TelepadBlock.DISABLED) != disabled || state.getValue(TelepadBlock.TRANSMITTER) != transmitter || state.getValue(TelepadBlock.TOGGLER) != toggler)
                level.setBlock(worldPosition, state.setValue(TelepadBlock.DISABLED, disabled).setValue(TelepadBlock.TRANSMITTER, transmitter).setValue(TelepadBlock.TOGGLER, toggler), 3);
            TelepadCatalog.get(server.getServer()).update(identity, entry -> entry.withState(false, disabled, transmitter));
        }
        return disabled;
    }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    public void setIdentity(UUID id, String value) {
        identity = id;
        name = value;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("identity", UUIDUtil.CODEC, identity);
        output.putString("name", name);
        output.putBoolean("toggler", toggler); output.putBoolean("transmitter", transmitter);
        output.store("colors", subaraki.telepads.data.PadColors.CODEC, colors);
        output.putString("configured", configured);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        identity = input.read("identity", UUIDUtil.CODEC).orElse(null);
        name = input.getStringOr("name", "Telepad");
        toggler = input.getBooleanOr("toggler", false); transmitter = input.getBooleanOr("transmitter", false);
        colors = input.read("colors", subaraki.telepads.data.PadColors.CODEC).orElse(subaraki.telepads.data.PadColors.DEFAULT);
        configured = input.getStringOr("configured", "");
    }
    @Override protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) { super.applyImplicitComponents(components); colors = components.getOrDefault(Telepads.COLORS.get(), subaraki.telepads.data.PadColors.DEFAULT); }
    @Override protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) { super.collectImplicitComponents(components); components.set(Telepads.COLORS.get(), colors); }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel server) TelepadCatalog.get(server.getServer()).markMissing(TelepadBlock.location(level, pos));
    }
}
