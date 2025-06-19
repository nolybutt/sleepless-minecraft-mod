
package net.mcreator.sleepless.client.renderer;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;

import net.mcreator.sleepless.entity.model.SleeplessModel;
import net.mcreator.sleepless.entity.layer.SleeplessLayer;
import net.mcreator.sleepless.entity.SleeplessEntity;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;

public class SleeplessRenderer extends GeoEntityRenderer<SleeplessEntity> {
	public SleeplessRenderer(EntityRendererProvider.Context renderManager) {
		super(renderManager, new SleeplessModel());
		this.shadowRadius = 0.5f;
		this.addRenderLayer(new SleeplessLayer(this));
	}

	@Override
	public RenderType getRenderType(SleeplessEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
		return RenderType.entityTranslucent(getTextureLocation(animatable));
	}

	@Override
	public void preRender(PoseStack poseStack, SleeplessEntity entity, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green,
			float blue, float alpha) {
		float scale = 1f;
		this.scaleHeight = scale;
		this.scaleWidth = scale;
		super.preRender(poseStack, entity, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
