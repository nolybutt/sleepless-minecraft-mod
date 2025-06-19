
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.mcreator.sleepless.init;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.common.ForgeSpawnEggItem;

import net.minecraft.world.item.Item;

import net.mcreator.sleepless.SleeplessMod;

public class SleeplessModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, SleeplessMod.MODID);
	public static final RegistryObject<Item> SLEEPLESS_SPAWN_EGG = REGISTRY.register("sleepless_spawn_egg", () -> new ForgeSpawnEggItem(SleeplessModEntities.SLEEPLESS, -16777216, -13057, new Item.Properties()));
	// Start of user code block custom items
	// End of user code block custom items
}
