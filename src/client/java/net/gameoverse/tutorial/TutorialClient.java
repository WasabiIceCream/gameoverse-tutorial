package net.gameoverse.tutorial;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the hints the server sends and reports when a screen the current step waits for opens. */
public final class TutorialClient implements ClientModInitializer {
    private static String waitingStep;
    private static List<String> waitingScreens = List.of();
    private static boolean debugWasOpen;
    private static boolean debugClosedSent;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(Tutorial.MOD_ID, "hints"), HintHud::render);
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Show.TYPE, (payload, context) -> show(context.client(), payload));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Progress.TYPE, (payload, context) -> {
            Hint hint = HintHud.find(payload.id());
            if (hint != null) {
                hint.setProgress(payload.progress());
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(Payloads.Hide.TYPE, (payload, context) -> {
            Hint hint = HintHud.find(payload.id());
            if (hint != null) {
                hint.hide();
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
            HintHud.clear();
            debugWasOpen = false;
            debugClosedSent = false;
            waitingStep = null;
            waitingScreens = List.of();
        });
        // The F3 tip shows once the player first closes F3 (hints hide while it's open, its text is on the left)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean open = client.player != null && client.getDebugOverlay().showDebugScreen();
            if (debugWasOpen && !open && !debugClosedSent && ClientPlayNetworking.canSend(Payloads.ClientEvent.TYPE)) {
                ClientPlayNetworking.send(new Payloads.ClientEvent("f3_closed"));
                debugClosedSent = true;
            }
            debugWasOpen = open;
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
        Hint old = HintHud.find(payload.id());
        if (old != null) {
            old.hide();
        }
        if (payload.seconds() == 0) {
            // One chain step at a time: a new one replaces any other step still up
            HintHud.all().stream().filter(Hint::isChainHint).forEach(Hint::hide);
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
        HintHud.add(new Hint(client.font, payload.id(), icon, title, text, payload.progress(), payload.seconds() * 1000L));
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F, 1.0F));
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
