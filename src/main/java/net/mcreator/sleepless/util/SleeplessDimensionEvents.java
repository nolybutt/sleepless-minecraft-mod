package net.mcreator.sleepless.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.sleepless.SleeplessMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Handles placing the Sleepless hub structure and teleporting players to the spawn location
 * when the Sleepless dimension loads.
 */
@Mod.EventBusSubscriber(modid = SleeplessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SleeplessDimensionEvents {
    private static final ResourceLocation HUB_STRUCTURE = new ResourceLocation(SleeplessMod.MODID, "sleepless_hub");
    private static final ResourceKey<Level> DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(SleeplessMod.MODID, "sleepless_dimension"));

    private static final BlockPos HUB_POS;
    private static final Vec3 SPAWN_POS;

    static {
        HUB_POS = readBlockPos("sleepless_dimension_spawn_data/structure_block_location.txt");
        SPAWN_POS = readVec3("sleepless_dimension_spawn_data/player_spawn_location.txt");
    }

    private static BlockPos readBlockPos(String path) {
        try (InputStream in = SleeplessDimensionEvents.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null)
                throw new IOException("Resource not found: " + path);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            text = text.replace("/", " ");
            String[] parts = text.split("\\s+");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new BlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        } catch (Exception e) {
            SleeplessMod.LOGGER.error("Failed reading block position from {}", path, e);
            return BlockPos.ZERO;
        }
    }

    private static Vec3 readVec3(String path) {
        try (InputStream in = SleeplessDimensionEvents.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null)
                throw new IOException("Resource not found: " + path);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            text = text.replace("/", " ");
            String[] parts = text.split("\\s+");
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            return new Vec3(x, y, z);
        } catch (Exception e) {
            SleeplessMod.LOGGER.error("Failed reading vector from {}", path, e);
            return Vec3.ZERO;
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        if (!level.dimension().equals(DIMENSION_KEY))
            return;
        placeHubIfNeeded(level);
    }

    @SubscribeEvent
    public static void onPlayerChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        Player entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player))
            return;
        if (!event.getTo().equals(DIMENSION_KEY))
            return;
        ServerLevel level = player.server.getLevel(DIMENSION_KEY);
        if (level == null)
            return;
        placeHubIfNeeded(level);
        BlockPos spawnPos = adjustSpawnPos(level);
        player.teleportTo(level, SPAWN_POS.x, spawnPos.getY() + 0.0, SPAWN_POS.z, player.getYRot(), player.getXRot());
    }

    private static void placeHubIfNeeded(ServerLevel level) {
        BlockState state = level.getBlockState(HUB_POS);
        if (!state.isAir())
            return;
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.getOrCreate(HUB_STRUCTURE);
        if (template == null) {
            SleeplessMod.LOGGER.error("Unable to load structure {}", HUB_STRUCTURE);
            return;
        }
        template.placeInWorld(level, HUB_POS, HUB_POS, new StructurePlaceSettings(), level.getRandom(), 2);
    }

    private static BlockPos adjustSpawnPos(ServerLevel level) {
        int x = Mth.floor(SPAWN_POS.x);
        int z = Mth.floor(SPAWN_POS.z);
        int y = Math.max(Mth.floor(SPAWN_POS.y), HUB_POS.getY() + 1);
        BlockPos pos = new BlockPos(x, y, z);
        while (!level.getBlockState(pos).isAir() && y < level.getMaxBuildHeight()) {
            y++;
            pos = new BlockPos(x, y, z);
        }
        return pos;
    }
}

