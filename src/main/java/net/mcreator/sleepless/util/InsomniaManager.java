package net.mcreator.sleepless.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tracks how long a player has gone without sleeping.
 */
@Mod.EventBusSubscriber(modid = net.mcreator.sleepless.SleeplessMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InsomniaManager {
    private static final String KEY = "sleepless_insomnia";

    /**
     * Increment insomnia for players every server tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        Player player = event.player;
        if (player.level().isClientSide())
            return;
        CompoundTag tag = player.getPersistentData();
        int level = tag.getInt(KEY);
        tag.putInt(KEY, level + 1);
    }

    /**
     * Reset insomnia when the player begins sleeping in a bed.
     */
    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        resetInsomnia(event.getEntity());
    }

    /**
     * Ensure insomnia resets when the player wakes up from a bed.
     */
    @SubscribeEvent
    public static void onPlayerWake(PlayerWakeUpEvent event) {
        resetInsomnia(event.getEntity());
    }

    private static void resetInsomnia(Player player) {
        if (player != null) {
            player.getPersistentData().putInt(KEY, 0);
        }
    }

    /**
     * Helper to query a player's insomnia level.
     */
    public static int getInsomnia(Player player) {
        return player.getPersistentData().getInt(KEY);
    }
}
