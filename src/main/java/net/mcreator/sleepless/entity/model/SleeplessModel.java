package net.mcreator.sleepless.entity.model;

import software.bernie.geckolib.model.GeoModel;

import net.minecraft.resources.ResourceLocation;

import net.mcreator.sleepless.entity.SleeplessEntity;

public class SleeplessModel extends GeoModel<SleeplessEntity> {
	@Override
	public ResourceLocation getAnimationResource(SleeplessEntity entity) {
		return new ResourceLocation("sleepless", "animations/sleepless.animation.json");
	}

	@Override
	public ResourceLocation getModelResource(SleeplessEntity entity) {
		return new ResourceLocation("sleepless", "geo/sleepless.geo.json");
	}

	@Override
	public ResourceLocation getTextureResource(SleeplessEntity entity) {
		return new ResourceLocation("sleepless", "textures/entities/" + entity.getTexture() + ".png");
	}

}
