package net.gameoverse.tutorial;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * How far Controlify's in-game button guide reaches down the top-left corner, in GUI pixels, so our hints can sit
 * below it instead of on top of it. Read reflectively from Controlify 3.5 internals (Controlify.inGameButtonGuide(),
 * InGameButtonGuide.controller/guideInstance, GuideInstanceImpl.leftGuides().height(), the profile's guide
 * settings); anything missing or renamed gives 0 and hints stay in the corner. Recheck on Controlify updates.
 */
final class ControllerGuide {
    private static final int MARGIN = 4;
    private static final int TOP = 5;
    private static final int SPACING = 2;
    private static boolean looked;
    private static boolean broken;
    private static Method instance;
    private static Method inGameButtonGuide;
    private static Field controller;
    private static Field guideInstance;
    private static Method settings;
    private static Field generic;
    private static Field guide;
    private static Field show;
    private static Field bottom;
    private static Field scale;
    private static Method leftGuides;
    private static Method height;
    private static Method lines;

    private ControllerGuide() {
    }

    static int leftColumnBottom() {
        if (broken || !FabricLoader.getInstance().isModLoaded("controlify")) {
            return 0;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return 0;
        }
        try {
            if (!looked) {
                looked = true;
                Class<?> controlify = Class.forName("dev.isxander.controlify.Controlify");
                instance = controlify.getMethod("instance");
                inGameButtonGuide = controlify.getMethod("inGameButtonGuide");
                Class<?> igbg = Class.forName("dev.isxander.controlify.gui.guide.InGameButtonGuide");
                controller = igbg.getDeclaredField("controller");
                controller.setAccessible(true);
                guideInstance = igbg.getDeclaredField("guideInstance");
                guideInstance.setAccessible(true);
                settings = Class.forName("dev.isxander.controlify.controller.ControllerEntity").getMethod("settings");
                generic = Class.forName("dev.isxander.controlify.config.settings.profile.ProfileSettings").getField("generic");
                guide = Class.forName("dev.isxander.controlify.config.settings.profile.GenericControllerSettings").getField("guide");
                Class<?> guideSettings = Class.forName("dev.isxander.controlify.config.settings.profile.GenericControllerSettings$GuideSettings");
                show = guideSettings.getField("showIngameGuide");
                bottom = guideSettings.getField("ingameGuideBottom");
                scale = guideSettings.getField("ingameGuiScale");
                leftGuides = Class.forName("dev.isxander.controlify.gui.guide.GuideInstanceImpl").getMethod("leftGuides");
                Class<?> precomputed = Class.forName("dev.isxander.controlify.gui.guide.PrecomputedLines");
                height = precomputed.getMethod("height");
                lines = precomputed.getMethod("lines");
            }
            Optional<?> igbg = (Optional<?>) inGameButtonGuide.invoke(instance.invoke(null));
            if (igbg.isEmpty()) {
                return 0;
            }
            Object guideSettings = guide.get(generic.get(settings.invoke(controller.get(igbg.get()))));
            if (!show.getBoolean(guideSettings) || bottom.getBoolean(guideSettings)) {
                return 0;
            }
            Object column = leftGuides.invoke(guideInstance.get(igbg.get()));
            int count = ((java.util.List<?>) lines.invoke(column)).size();
            if (count == 0) {
                return 0;
            }
            // GuideRenderer.extractLines: 5 px from the top, the lines' heights, 2 px between lines
            int guideHeight = TOP + (int) height.invoke(column) + (count - 1) * SPACING;
            int guideScale = scale.getInt(guideSettings);
            double factor = guideScale <= 0 ? 1.0 : guideScale / mc.getWindow().getGuiScale();
            return (int) Math.ceil(guideHeight * factor) + MARGIN;
        } catch (ReflectiveOperationException | ClassCastException | LinkageError e) {
            broken = true;
            Tutorial.LOGGER.warn("Can't read Controlify's button guide; tutorial hints may overlap it", e);
            return 0;
        }
    }
}
