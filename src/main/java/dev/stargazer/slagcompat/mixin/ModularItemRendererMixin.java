package dev.stargazer.slagcompat.mixin;

import com.google.gson.JsonParser;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.lopyluna.slag.SlagEmbers;
import dev.lopyluna.slag.client.render.CustomRenderedItemModel;
import dev.lopyluna.slag.client.render.PartialItemModelRenderer;
import dev.lopyluna.slag.content.items.dynamic_part.IModularItem;
import dev.lopyluna.slag.content.items.modular.ModularItemRenderer;
import dev.lopyluna.slag.register.AllDataComponents;
import dev.lopyluna.slag.register.AllDynamicTypes;
import dev.stargazer.slagcompat.SlagCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static dev.lopyluna.slag.client.render.CustomRenderedItemModelRenderer.getModel;
import static dev.lopyluna.slag.content.items.modular.ModularItemRenderer.renderBakedPart;
import static dev.lopyluna.slag.content.items.modular.ModularItemRenderer.renderNonBakedPart;

@Mixin(ModularItemRenderer.class)
public class ModularItemRendererMixin {


    @Overwrite @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional e) {
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_blueprint")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_handheld")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_equipable")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_trim")));

        var toolPath = SlagEmbers.loc("slag", "tool").getPath();
        var toolResources = Minecraft.getInstance().getResourceManager().listResources(toolPath, file -> true);

        for (Map.Entry<ResourceLocation, Resource> entry : toolResources.entrySet()) {
            ResourceLocation loc = entry.getKey();
            Resource resource = entry.getValue();
            try (var reader = resource.openAsReader()) {
                var toolObj = JsonParser.parseReader(reader).getAsJsonObject();
                var toolName = toolObj.get("name").getAsString();
                var components = toolObj.getAsJsonArray("components");
                var fallbackArray = new String[components.size()];
                for (int i = 0; i < fallbackArray.length; i++) {
                    fallbackArray[i] = components.get(i).getAsString();
                }
                SlagCompat.ToolFallbacks.put(toolName, fallbackArray);

                e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_fire_proof_" + toolName)));
                e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_" + toolName)));
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }
        }
    }

    public final Minecraft mc = Minecraft.getInstance();

    @Overwrite
    protected void render(ItemStack stack, ItemRenderer itemRenderer, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                        ItemDisplayContext ctx, PoseStack ms, MultiBufferSource buf, int light, int overlay) {
        var level = mc.level;
        var player = mc.player;
        if (level == null || player == null) return;
        var item = stack.getItem();

        if (!(item instanceof IModularItem tool)) {
            renderer.render(model.getOriginalModel(), light);
            return;
        }
        var shaper = itemRenderer.getItemModelShaper();
        var manager = shaper.getModelManager();

        var baked = stack.has(AllDataComponents.MODULAR_TYPE);
        var bakedLoc = stack.get(AllDataComponents.MODULAR_TYPE);
        var modularType = AllDynamicTypes.getModular(bakedLoc).orElse(null);
        var bakedPath = bakedLoc != null ? bakedLoc.getPath() : "";
        var suffix = modularType != null && !modularType.modelType.isEmpty() ? "_" + modularType.modelType : "";

        var baseModel = getModel(stack, level, player, SlagEmbers.loc(baked ? "item/modular_item_baked" + suffix : "item/modular_item_blueprint"), manager);

        var nudge = ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED ? 0.01f : 0.001f;
        int i = 1;

        var dynamicParts = tool.getParts(stack);

        var totalItems = !baked ? dynamicParts == null || dynamicParts.isEmpty() ? 0 : dynamicParts.size() : -1;
        var currentIndex = 0;
        var random = !baked ? new Random(totalItems * 100L + (0 >= totalItems ? 0 : dynamicParts.hashCode())) : null;
        var randomRot = !baked ? random.nextFloat() * 360 : -1;
        var randomSpeedMod = !baked ? (3.25f + (random.nextFloat() * 1.25f)) * (random.nextBoolean() ? 1 : -1) : -1;

        var fireImmune = stack.has(DataComponents.FIRE_RESISTANT);
        var left = player.getMainArm() == HumanoidArm.LEFT && (ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

        ms.pushPose();
        baseModel.applyTransform(ctx, ms, left);
        if (!baked) renderer.render(baseModel, light);
        if (dynamicParts != null && !dynamicParts.isEmpty()) for (var part : dynamicParts.itemCopyRandom(random)) {
            if (part.isEmpty()) continue;
            ms.pushPose();
            ms.scale(1 + (i * nudge), 1 + (i * nudge), 1 + (i * (nudge * 2f)));

            if (baked) renderBakedPart(level, player, part, light, (fireImmune ? "_fire_proof_" : "_") + bakedPath, itemRenderer, renderer, manager);
            else renderNonBakedPart(level, player, part, light, totalItems, currentIndex, randomRot, randomSpeedMod, ms, itemRenderer, renderer, manager);
            ms.popPose();
            i++;
            currentIndex++;
        }
        else {
            for (String partID : SlagCompat.ToolFallbacks.get(bakedPath)) {
                var target = "item/modular/" + bakedPath + "/stone/" + partID;
                var modelLoc = ModelResourceLocation.standalone(SlagEmbers.loc(target));
                renderer.render(manager.getModel(modelLoc), light);
            }
            var handle = ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_" + bakedPath));
            renderer.render(manager.getModel(handle), light);
        }

        if (ctx != ItemDisplayContext.HEAD && stack.has(DataComponents.TRIM) && suffix.equals("_equipable")) {
            ms.pushPose();
            var bakedModel = getModel(stack, level, player, SlagEmbers.loc("item/modular_item_baked_trim"), manager);
            renderer.render(bakedModel, light);
            ms.scale(1 + (i * nudge), 1 + (i * nudge), 1 + (i * (nudge * 2f)));
            renderer.render(bakedModel, light);
            ms.popPose();
        }
        ms.popPose();
    }
}
