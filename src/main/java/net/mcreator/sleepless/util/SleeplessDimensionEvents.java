package net.mcreator.sleepless.util;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.sleepless.SleeplessMod;
import net.mcreator.sleepless.init.SleeplessModEntities;

/**
 * Handles placement of the hub structure and safe teleportation when players enter the
 * Sleepless dimension.  The structure template now loads from
 * {@code data/sleepless/structures/sleepless_dimension.nbt} using the exact
 * {@code sleepless:sleepless_dimension} ID.  Detailed debug logging has been added around
 * template loading and placement and the spawn position search was updated so players never
 * spawn in midair or underground.
 */
@Mod.EventBusSubscriber(modid = SleeplessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SleeplessDimensionEvents {
    /** Flag to ensure the hub structure only generates once per server run. */
    private static boolean hubPlaced;
    /** Flag to ensure the Sleepless entity only spawns once when a player enters. */
    private static boolean entitySpawned;
    // Template for the hub built in the Sleepless dimension
    // File path: data/sleepless/structures/sleepless_dimension.nbt
    private static final ResourceLocation HUB_STRUCTURE =
            new ResourceLocation(SleeplessMod.MODID, "sleepless_dimension");
    private static final ResourceKey<Level> DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(SleeplessMod.MODID, "sleepless_dimension"));

    // Coordinates for the hub structure's placement in the Sleepless dimension
    private static final BlockPos HUB_POS;
    // Coordinates where players should spawn inside the dimension
    private static final Vec3 SPAWN_POS;

    static {
        HUB_POS = readBlockPos("data/sleepless/structure_block_location.txt");
        SPAWN_POS = readVec3("data/sleepless/player_spawn_location.txt");
    }
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        if (!level.dimension().equals(DIMENSION_KEY))
            return;
        // Only place the hub the first time the dimension is loaded
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
        // Teleport the player to the configured spawn position once the hub is placed
        player.teleportTo(level, SPAWN_POS.x, spawnPos.getY() + 0.0, SPAWN_POS.z,
                player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Teleported to Sleepless hub"));
        SleeplessMod.LOGGER.info("Teleported {} to {}", player.getScoreboardName(), SPAWN_POS);

        // Spawn the Sleepless entity the first time someone enters the dimension
        if (!entitySpawned) {
            var sleepless = SleeplessModEntities.SLEEPLESS.get().create(level);
            if (sleepless != null) {
                sleepless.moveTo(SPAWN_POS.x, spawnPos.getY(), SPAWN_POS.z, 0, 0);
                level.addFreshEntity(sleepless);
                entitySpawned = true;
                SleeplessMod.LOGGER.info("Spawned Sleepless at {}", SPAWN_POS);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("A Sleepless stalks you..."));
            }
        }
    }

    private static void placeHubIfNeeded(ServerLevel level) {
        if (hubPlaced)
            return;

        BlockState state = level.getBlockState(HUB_POS);
        if (!state.isAir()) {
            hubPlaced = true;
            return;
        }

        // Attempt to load the template for the hub structure. get returns an
        // Optional in 1.20.1, so handle the empty case with a null check.
        StructureTemplateManager manager = level.getStructureManager();
        StructureTemplate template = manager.get(HUB_STRUCTURE).orElse(null);
        if (template == null) {
            SleeplessMod.LOGGER.error("Unable to load structure {}", HUB_STRUCTURE);
            return;
        }

        SleeplessMod.LOGGER.debug("Loaded template {} size {}", HUB_STRUCTURE, template.getSize());
        SleeplessMod.LOGGER.debug("Placing {} at {} in {}", HUB_STRUCTURE, HUB_POS, level.dimension());
        template.placeInWorld(level, HUB_POS, HUB_POS, new StructurePlaceSettings(),
                level.getRandom(), 2);
        hubPlaced = true;
        SleeplessMod.LOGGER.info("Sleepless hub placed at {}", HUB_POS);
    }

    private static BlockPos adjustSpawnPos(ServerLevel level) {
        int x = Mth.floor(SPAWN_POS.x);
        int z = Mth.floor(SPAWN_POS.z);

        // Start near the configured Y but never below the hub placement.
        int y = Math.max(Mth.floor(SPAWN_POS.y), HUB_POS.getY() + 1);
        BlockPos pos = new BlockPos(x, y, z);

        // Move downward until we hit solid ground or the bottom of the world.
        while (y > level.getMinBuildHeight() && level.getBlockState(pos.below()).isAir()) {
            y--;
            pos = pos.below();
        }

        // Move up if the spawn point is obstructed.
        while (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && y < level.getMaxBuildHeight() - 1) {
            y++;
            pos = pos.above();
        }

        return pos;
    }

    /**
     * Reads a BlockPos from a resource file under src/main/resources.
     * The file should contain coordinates separated by spaces or slashes.
     */
    private static BlockPos readBlockPos(String path) {
        try (InputStream in = SleeplessDimensionEvents.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null)
                throw new IOException("Resource not found: " + path);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            text = text.replace("/", " ");
            String[] parts = text.split("\\s+");
            int x = (int) Math.floor(Double.parseDouble(parts[0]));
            int y = (int) Math.floor(Double.parseDouble(parts[1]));
            int z = (int) Math.floor(Double.parseDouble(parts[2]));
            return new BlockPos(x, y, z);
        } catch (Exception e) {
            SleeplessMod.LOGGER.error("Failed reading block position from {}", path, e);
            return BlockPos.ZERO;
        }
    }

    /**
     * Reads a Vec3 from a resource file under src/main/resources.
     * The file should contain coordinates separated by spaces or slashes.
     */
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
}

