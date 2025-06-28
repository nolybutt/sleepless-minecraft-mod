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
 * Handles placement of the Sleepless hub and safe teleportation when players
 * enter the dimension.
 *
 * <p><strong>Summary of fixes</strong>: the hub structure is loaded via
 * {@code sleepless:sleepless_dimension} and an optional template check ensures
 * missing resources are logged. Additional debug logs print the placement
 * coordinates and whether the template loaded. The spawn position now searches
 * downward for solid ground and only shifts upward when obstructed, preventing
 * players from falling or suffocating.</p>
 */
@Mod.EventBusSubscriber(modid = SleeplessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SleeplessDimensionEvents {
    /** Flag to ensure the hub structure only generates once per server run. */
    private static boolean hubPlaced;
    /** Flag to ensure the Sleepless entity only spawns once when a player enters. */
    private static boolean entitySpawned;
    // Template for the hub built in the Sleepless dimension
    // File path: data/sleepless/structures/sleepless_dimension.nbt
    // NBT name for the hub structure used by both /place command and code
    private static final ResourceLocation HUB_STRUCTURE =
            new ResourceLocation("sleepless", "sleepless_dimension");
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

    /** Public accessor for the Sleepless dimension key. */
    public static ResourceKey<Level> dimensionKey() {
        return DIMENSION_KEY;
    }

    /** Returns the configured spawn vector loaded from the resource file. */
    public static Vec3 getSpawnVec() {
        return SPAWN_POS;
    }
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level))
            return;
        if (!level.dimension().equals(DIMENSION_KEY))
            return;
        // Only place the hub the first time the dimension is loaded
        ensureHubPlaced(level);
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
        ensureHubPlaced(level);
        BlockPos spawnPos = adjustSpawnPos(level);
        // Teleport the player to the exact spawn location, adjusting Y only if obstructed
        player.teleportTo(level, SPAWN_POS.x, spawnPos.getY(), SPAWN_POS.z,
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

    /**
     * Ensures the hub structure exists in the target level. This method may be
     * called multiple times but the hub will only be placed once.
     */
    public static void ensureHubPlaced(ServerLevel level) {
        if (hubPlaced)
            return;

        // Load/generate the chunk at the hub coordinates before placement
        level.getChunkAt(HUB_POS);

        StructureTemplateManager manager = level.getStructureManager();
        SleeplessMod.LOGGER.debug("Loading template {} for hub", HUB_STRUCTURE);
        var optionalTemplate = manager.get(HUB_STRUCTURE);
        if (optionalTemplate.isEmpty()) {
            SleeplessMod.LOGGER.error("Hub template {} missing", HUB_STRUCTURE);
            return;
        }
        StructureTemplate template = optionalTemplate.get();

        SleeplessMod.LOGGER.debug("Template size {}. Placing hub at {} in {}", template.getSize(), HUB_POS,
                level.dimension());
        template.placeInWorld(level, HUB_POS, HUB_POS, new StructurePlaceSettings(),
                level.getRandom(), 2);
        SleeplessMod.LOGGER.debug("Hub placed at coordinates {}", HUB_POS);
        hubPlaced = true;
        SleeplessMod.LOGGER.info("Sleepless hub placed at {}", HUB_POS);
    }

    /**
     * Calculates a safe spawn position based on the configured spawn vector.
     * Moves upward only when the location is obstructed.
     */
    public static BlockPos adjustSpawnPos(ServerLevel level) {
        int x = Mth.floor(SPAWN_POS.x);
        int z = Mth.floor(SPAWN_POS.z);
        int y = Mth.floor(SPAWN_POS.y);
        BlockPos pos = new BlockPos(x, y, z);

        // Walk downward until we find solid ground beneath the spawn point
        while (y > level.getMinBuildHeight() &&
                level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
            y--;
            pos = pos.below();
        }

        // If the spawn block itself is obstructed, shift upward into air
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

