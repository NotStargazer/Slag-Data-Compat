package dev.stargazer.slagcompat;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

import java.util.HashMap;
import java.util.Map;

@Mod(SlagCompat.MODID)
public class SlagCompat {
    public static final String MODID = "slagcompat";
    public SlagCompat(IEventBus modEventBus, ModContainer modContainer) {}
    public static final Map<String, String[]> ToolFallbacks = new HashMap<>();
}
