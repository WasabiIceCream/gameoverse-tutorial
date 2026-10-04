package net.gameoverse.tutorial;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the hints the server sends and reports when a screen the current step waits for opens. */
public final class TutorialClient implements ClientModInitializer {
    private static final Map<String, HintToast> SHOWN = new HashMap<>();
    private static String waitingStep;
    private static List<String> waitingScreens = List.of();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Show.TYPE, (payload, context) -> show(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Progress.TYPE, (payload, context) -> {
            HintToast toast = SHOWN.get(payload.id());
            if (toast != null) {
                toast.setProgress(payload.progress());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Hide.TYPE, (payload, context) -> {
            HintToast toast = SHOWN.remove(payload.id());
            if (toast != null) {
                toast.hide();
            }
            if (payload.id().equals(waitingStep)) {
                waitingStep = null;
                waitingScreens = List.of();
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (ClientPlayNetworking.canSend(Payloads.ScreenOpened.TYPE)) {
                // Our chain teaches the same first steps, so vanilla's own tutorial toasts would only double up
                client.getTutorial().setStep(TutorialSteps.NONE);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SHOWN.values().forEach(HintToast::hide);
            SHOWN.clear();
            waitingStep = null;
            waitingScreens = List.of();
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (waitingStep == null || waitingScreens.isEmpty()) {
                return;
            }
            for (Class<?> c = screen.getClass(); c != null; c = c.getSuperclass()) {
                if (waitingScreens.contains(c.getName())) {
                    ClientPlayNetworking.send(new Payloads.ScreenOpened(waitingStep));
                    return;
                }
            }
        });
    }

    private static void show(Minecraft client, Payloads.Show payload) {
        HintToast old = SHOWN.remove(payload.id());
        if (old != null) {
            old.hide();
        }
        if (payload.seconds() == 0) {
            // One chain step at a time: a new one replaces any other step still up
            SHOWN.values().removeIf(t -> {
                if (t.isChainHint()) {
                    t.hide();
                    return true;
                }
                return false;
            });
            waitingStep = payload.id();
            waitingScreens = payload.screens();
        }
        String base = Tutorial.MOD_ID + "." + payload.id();
        Object[] keys = payload.keys().stream().map(Component::keybind).toArray();
        Component title = Component.translatable(base + ".title");
        Component text = Component.translatable(textKey(base), keys);
        Identifier iconId = Identifier.tryParse(payload.icon());
        ItemStack icon = iconId != null && BuiltInRegistries.ITEM.containsKey(iconId)
            ? new ItemStack(BuiltInRegistries.ITEM.getValue(iconId)) : new ItemStack(Items.BOOK);
        HintToast toast = new HintToast(client.font, payload.id(), icon, title, text, payload.progress(),
            payload.seconds() * 1000L);
        SHOWN.put(payload.id(), toast);
        client.getToastManager().addToast(toast);
    }

    /** The controller wording when the player is on a controller and the step has one. */
    private static String textKey(String base) {
        String controller = base + ".text.controller";
        if (I18n.exists(controller) && ControllerCheck.usingController()) {
            return controller;
        }
        return base + ".text";
    }

    /** Controlify's input mode, read reflectively so the mod works without Controlify. */
    static final class ControllerCheck {
        private static boolean looked;
        private static java.lang.reflect.Method get;
        private static java.lang.reflect.Method mode;
        private static java.lang.reflect.Method isController;

        static boolean usingController() {
            if (!FabricLoader.getInstance().isModLoaded("controlify")) {
                return false;
            }
            try {
                if (!looked) {
                    looked = true;
                    Class<?> api = Class.forName("dev.isxander.controlify.api.ControlifyApi");
                    get = api.getMethod("get");
                    mode = api.getMethod("currentInputMode");
                    isController = Class.forName("dev.isxander.controlify.InputMode").getMethod("isController");
                }
                if (get == null) {
                    return false;
                }
                return (boolean) isController.invoke(mode.invoke(get.invoke(null)));
            } catch (ReflectiveOperationException | LinkageError e) {
                get = null;
                return false;
            }
        }
    }
}
