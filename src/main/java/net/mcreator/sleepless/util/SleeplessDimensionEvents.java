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
import net.minecraft.world.level.levelgen.Heightmap;
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
 * <p><strong>Summary of fixes</strong>: hub placement now checks the template
 * via {@code StructureTemplateManager#get} using the ID
 * {@code sleepless:sleepless_dimension}. Failures are logged and the attempted
 * placement coordinates printed. The hub chunk is forced while placing the
 * structure. Players now spawn relative to the hub &mdash; 22 blocks south and
 * 7 blocks above the structure block &mdash; ensuring a safe location on every
 * teleport. Debug logs make failures explicit.</p>

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
    // Offset from the structure block to the player spawn point
    private static final Vec3 SPAWN_OFFSET = new Vec3(0.5, 7.0, 22.5);

    static {
        HUB_POS = readBlockPos("data/sleepless/structure_block_location.txt");
    }

    /** Public accessor for the Sleepless dimension key. */
    public static ResourceKey<Level> dimensionKey() {
        return DIMENSION_KEY;
    }

    /**
     * Calculates the absolute spawn vector relative to the hub structure block.
     * Players spawn 22 blocks south and 7 blocks above the hub.
     */
    public static Vec3 getSpawnVec() {
        return new Vec3(HUB_POS.getX() + SPAWN_OFFSET.x, HUB_POS.getY() + SPAWN_OFFSET.y,
                HUB_POS.getZ() + SPAWN_OFFSET.z);
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
        Vec3 targetVec = getSpawnVec();
        BlockPos spawnPos = adjustSpawnPos(level);
        // Teleport the player to the spawn relative to the hub structure
        player.teleportTo(level, targetVec.x, spawnPos.getY(), targetVec.z,

                player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Teleported to Sleepless hub"));
        SleeplessMod.LOGGER.info("Teleported {} to {}", player.getScoreboardName(), targetVec);

        // Spawn the Sleepless entity the first time someone enters the dimension
        if (!entitySpawned) {
            var sleepless = SleeplessModEntities.SLEEPLESS.get().create(level);
            if (sleepless != null) {
                sleepless.moveTo(targetVec.x, spawnPos.getY(), targetVec.z, 0, 0);
                level.addFreshEntity(sleepless);
                entitySpawned = true;
                SleeplessMod.LOGGER.info("Spawned Sleepless at {}", targetVec);
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

        // Load/generate and force the chunk at the hub coordinates before placement
        level.getChunkAt(HUB_POS);
        int chunkX = HUB_POS.getX() >> 4;
        int chunkZ = HUB_POS.getZ() >> 4;
        level.setChunkForced(chunkX, chunkZ, true);

        StructureTemplateManager manager = level.getStructureManager();
        SleeplessMod.LOGGER.debug("Loading template {} for hub", HUB_STRUCTURE);
        var optionalTemplate = manager.get(HUB_STRUCTURE);
        if (optionalTemplate.isEmpty()) {
            SleeplessMod.LOGGER.error("Missing template {} when placing hub", HUB_STRUCTURE);
            level.setChunkForced(chunkX, chunkZ, false);
            return;
        }
        StructureTemplate template = optionalTemplate.get();

        SleeplessMod.LOGGER.debug("Template size {} for {}", template.getSize(), HUB_STRUCTURE);
        SleeplessMod.LOGGER.debug("Attempting placement at {} in {}", HUB_POS, level.dimension());
        template.placeInWorld(level, HUB_POS, HUB_POS, new StructurePlaceSettings(),
                level.getRandom(), 2);
        SleeplessMod.LOGGER.debug("Hub placed at coordinates {}", HUB_POS);

        hubPlaced = true;
        level.setChunkForced(chunkX, chunkZ, false);
        SleeplessMod.LOGGER.info("Sleepless hub placed at {}", HUB_POS);
    }

    /**
     * Calculates a safe spawn position based on the configured spawn vector.
     * Moves upward only when the location is obstructed.
     */
    public static BlockPos adjustSpawnPos(ServerLevel level) {

        Vec3 vec = getSpawnVec();
        int x = Mth.floor(vec.x);
        int z = Mth.floor(vec.z);
        int estimatedY = Mth.floor(vec.y);


        BlockPos start = new BlockPos(x, estimatedY, z);
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, start);

        if (pos.getY() < level.getMinBuildHeight()) {
            pos = new BlockPos(x, level.getMinBuildHeight(), z);

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

