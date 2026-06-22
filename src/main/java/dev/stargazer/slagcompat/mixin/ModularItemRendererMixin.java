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
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static dev.lopyluna.slag.client.render.CustomRenderedItemModelRenderer.getModel;
import static dev.lopyluna.slag.content.items.modular.ModularItemRenderer.renderBakedPart;
import static dev.lopyluna.slag.content.items.modular.ModularItemRenderer.renderNonBakedPart;

@Mixin(ModularItemRenderer.class)
public class ModularItemRendererMixin {


    /**
     * @author Stargazer
     * @reason Functionally does the same thing as previous code, however now supports data
     */
    @Overwrite @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional e) {
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_blueprint")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_handheld")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_equipable")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_trim")));

        var toolPath = SlagEmbers.loc("slag", "tool").getPath();
        var toolResources = Minecraft.getInstance().getResourceManager().listResources(toolPath, file -> true);

        for (Resource resource : toolResources.values()) {
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

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V", ordinal = 2))
    protected void render(ItemStack stack, ItemRenderer itemRenderer, CustomRenderedItemModel model, PartialItemModelRenderer renderer, ItemDisplayContext ctx, PoseStack ms, MultiBufferSource buf, int light, int overlay, CallbackInfo ci) {
        var toolItem = (IModularItem)stack.getItem();
        var modelManager = itemRenderer.getItemModelShaper().getModelManager();
        var toolLoc = stack.get(AllDataComponents.MODULAR_TYPE);
        var toolBaked = toolLoc != null ? toolLoc.getPath() : "";
        var dynamicPartsFallback = toolItem.getParts(stack);
        var statement = dynamicPartsFallback != null && !dynamicPartsFallback.isEmpty();
        if (!statement && !toolBaked.isEmpty()) {
            for (String partID : SlagCompat.ToolFallbacks.get(toolBaked)) {
                var target = "item/modular/" + toolBaked + "/stone/" + partID;
                var modelLoc = ModelResourceLocation.standalone(SlagEmbers.loc(target));
                renderer.render(modelManager.getModel(modelLoc), light);
            }
            var handle = ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_" + toolBaked));
            renderer.render(modelManager.getModel(handle), light);
        }
    }
}
