package net.mcreator.sleepless.util;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.sleepless.SleeplessMod;
import net.mcreator.sleepless.entity.SleeplessEntity;

import java.util.List;

/**
 * Handles door interactions that may teleport the player to the nightmare dimension.
 */
@Mod.EventBusSubscriber(modid = SleeplessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DoorPortalHandler {
    private static final String KEY_X = "sleepless_portal_x";
    private static final String KEY_Y = "sleepless_portal_y";
    private static final String KEY_Z = "sleepless_portal_z";

    @SubscribeEvent
    public static void onDoorOpened(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide())
            return;
        Player player = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (!(state.getBlock() instanceof DoorBlock))
            return;
        // Only trigger when opening a closed door
        if (state.getValue(DoorBlock.OPEN))
            return;
        List<SleeplessEntity> nearby = event.getLevel().getEntitiesOfClass(SleeplessEntity.class, player.getBoundingBox().inflate(20));
        if (nearby.isEmpty())
            return;
        CompoundTag tag = player.getPersistentData();
        tag.putInt(KEY_X, pos.getX());
        tag.putInt(KEY_Y, pos.getY());
        tag.putInt(KEY_Z, pos.getZ());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Player player = event.player;
        if (player.level().isClientSide())
            return;
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(KEY_X))
            return;
        BlockPos doorPos = new BlockPos(tag.getInt(KEY_X), tag.getInt(KEY_Y), tag.getInt(KEY_Z));
        AABB box = new AABB(doorPos).inflate(0.25);
        if (box.intersects(player.getBoundingBox())) {
            tag.remove(KEY_X);
            tag.remove(KEY_Y);
            tag.remove(KEY_Z);
            if (player.getRandom().nextFloat() < 0.05f) {
                if (player instanceof ServerPlayer sp) {
                    ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(SleeplessMod.MODID, "nightmare"));
                    ServerLevel target = sp.server.getLevel(key);
                    if (target != null)
                        sp.changeDimension(target);
                }
            }
        }
    }
}
