package net.mcreator.sleepless.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.mcreator.sleepless.SleeplessMod;
import net.mcreator.sleepless.util.SleeplessDimensionEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * Command to teleport the executing player to the same location the door
 * portal would send them in the Sleepless dimension. Useful for testing
 * structure generation and spawn logic without using a door.
 */
@Mod.EventBusSubscriber(modid = SleeplessMod.MODID)
public class DoorSpawnCommand {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("doorspawn")
                .requires(cs -> cs.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.server.getLevel(SleeplessDimensionEvents.dimensionKey());
        if (level == null) {
            source.sendFailure(Component.literal("Sleepless dimension missing"));
            return 0;
        }
        SleeplessDimensionEvents.ensureHubPlaced(level);
        Vec3 target = SleeplessDimensionEvents.getSpawnVec();
        BlockPos spawn = SleeplessDimensionEvents.adjustSpawnPos(level);
        player.teleportTo(level, target.x, spawn.getY(), target.z,
                player.getYRot(), player.getXRot());

        source.sendSuccess(() -> Component.literal("Teleported to Sleepless door spawn"), true);
        return 1;
    }
}
