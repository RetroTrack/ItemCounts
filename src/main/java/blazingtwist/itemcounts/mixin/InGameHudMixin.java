package blazingtwist.itemcounts.mixin;

import blazingtwist.itemcounts.ItemCounts;
import blazingtwist.itemcounts.config.ItemCountsConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private ItemRenderer itemRenderer;

	@Shadow
	private int scaledWidth;

	@Shadow
	private int scaledHeight;

	private int getTotalItemCount(PlayerEntity player, ItemStack stack) {
		return Stream.concat(player.getInventory().main.stream(), player.getInventory().offHand.stream())
				.filter(other -> other.isItemEqual(stack))
				.mapToInt(ItemStack::getCount)
				.sum();
	}

	private void renderItemOverlay(ItemCountsConfig.ItemRenderConfig config, boolean onHotbar,
								   PlayerEntity player, ItemStack stack, int x, int y) {
		if (!config.enabled) {
			return;
		}
		if (!onHotbar) {
			x = scaledWidth / 2;
			y = scaledHeight / 2;
		}
		x += config.offset.x;
		y += config.offset.y;

		boolean didRenderText = renderItemText(config, onHotbar, player, stack, x, y);
		if (config.iconOption.shouldShowIcon(didRenderText)) {
			renderItemAt(stack, x, y, config.offset.textScale, onHotbar);
		}
	}

	private boolean renderItemText(ItemCountsConfig.ItemRenderConfig config, boolean onHotbar,
								   PlayerEntity player, ItemStack stack, int x, int y) {
		String text;
		int color = -1;
		if (stack.isDamageable() && config.durabilityFilter.showDurabilityInsteadOfItemCount(stack)) {
			if (!config.durabilityOption.shouldShowDurability(stack)) {
				return false;
			}
			text = "" + (stack.getMaxDamage() - stack.getDamage());
			color = stack.getItemBarColor();
		} else {
			int totalCount = getTotalItemCount(player, stack);
			if (!config.countOption.shouldShowCount(stack, totalCount)) {
				return false;
			}
			text = "" + totalCount;
		}

		renderTextAt(text, color, x, y, config.offset.textScale, onHotbar);
		return true;
	}

	private void renderTextAt(String text, int color, int x, int y, float scaleFactor, boolean isOnHotbar) {
		TextRenderer renderer = client.textRenderer;
		MatrixStack matrixStack = new MatrixStack();
		matrixStack.translate(0, ItemCounts.FONT_Y_OFFSET * scaleFactor, 250);
		if (isOnHotbar) {
			matrixStack.translate(ItemCounts.HOTBAR_X_OFFSET * scaleFactor, 0, 0);
		}
		matrixStack.scale(scaleFactor, scaleFactor, 1);
		VertexConsumerProvider.Immediate immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
		renderer.draw(
				text,
				(x / scaleFactor) - (renderer.getWidth(text) / 2f),
				(y / scaleFactor) - (ItemCounts.FONT_HEIGHT / 2),
				color >= 0 ? color : 16777215,
				true,
				matrixStack.peek().getPositionMatrix(),
				immediate,
				false,
				0,
				15728880);
		immediate.draw();
	}

	private void renderItemAt(ItemStack item, int x, int y, float scaleFactor, boolean isOnHotbar) {
		//textureManager.getTexture(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE).setFilter(false, false);
		RenderSystem.setShaderTexture(0, PlayerScreenHandler.BLOCK_ATLAS_TEXTURE);
		RenderSystem.enableBlend();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		MatrixStack matrixStack = RenderSystem.getModelViewStack();
		matrixStack.push();
		matrixStack.translate(x, y, 100.0F);
		if (isOnHotbar) {
			matrixStack.translate(ItemCounts.HOTBAR_X_OFFSET * scaleFactor, 0, 0);
		}
		matrixStack.scale(1.0F, -1.0F, 1.0F);
		matrixStack.scale(16.0F, 16.0F, 16.0F);
		RenderSystem.applyModelViewMatrix();
		MatrixStack matrixStack2 = new MatrixStack();
		matrixStack2.scale(scaleFactor, scaleFactor, scaleFactor);
		VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
		BakedModel model = itemRenderer.getModel(item, null, null, 0);
		boolean bl = !model.isSideLit();
		if (bl) {
			DiffuseLighting.disableGuiDepthLighting();
		}

		itemRenderer.renderItem(item, ModelTransformation.Mode.GUI, false, matrixStack2, immediate, 15728880, OverlayTexture.DEFAULT_UV, model);
		immediate.draw();
		RenderSystem.enableDepthTest();
		if (bl) {
			DiffuseLighting.enableGuiDepthLighting();
		}

		matrixStack.pop();
		RenderSystem.applyModelViewMatrix();
	}

	@Inject(method = "renderHotbarItem(IIFLnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/item/ItemStack;I)V", at = @At("HEAD"))
	public void onRenderHotbarItem(int x, int y, float tickDelta, PlayerEntity player, ItemStack stack, int seed, CallbackInfo info) {
		if (stack.isEmpty()) {
			return;
		}

		ItemCountsConfig config = ItemCounts.getConfig();

		if (player.getMainHandStack() == stack) {
			renderItemOverlay(config.mainHand_relativeToCrosshairConfig, false, player, stack, x, y);
			renderItemOverlay(config.mainHand_relativeToHotbarConfig, true, player, stack, x, y);
		}

		if (player.getOffHandStack() == stack) {
			renderItemOverlay(config.offHand_relativeToCrosshairConfig, false, player, stack, x, y);
			renderItemOverlay(config.offHand_relativeToHotbarConfig, true, player, stack, x, y);
		}

		renderItemOverlay(config.hotbar_relativeToHotbarConfig, true, player, stack, x, y);
	}
}
