package dev.stargazer.slagcompat.mixin;

import dev.lopyluna.slag.SlagEmbers;
import dev.lopyluna.slag.content.items.modular.ModularItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ModularItemRenderer.class)
public class ModularItemRendererMixin {

    @Overwrite @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional e) {
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_blueprint")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_handheld")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_equipable")));
        e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/modular_item_baked_trim")));

        var a = SlagEmbers.loc("slag", "tool").getPath();
        var b = Minecraft.getInstance().getResourceManager().listResources(a, file -> true);

        b.forEach((loc, r) -> {
            var mixture = loc.getPath().split("/")[1];
            e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_fire_proof_" + mixture)));
            e.register(ModelResourceLocation.standalone(SlagEmbers.loc("item/handle_" + mixture)));
        });
    }
}
