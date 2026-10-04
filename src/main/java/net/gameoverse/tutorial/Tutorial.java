package net.gameoverse.tutorial;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Vanilla-style tutorial hints for new players, from moving around to the first iron pickaxe and the first Satiated
 * Shield meal, plus one-off tips. The server decides what to show; the client draws it (see TutorialClient).
 */
public final class Tutorial implements ModInitializer {
    public static final String MOD_ID = "gameoverse_tutorial";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final AttachmentType<TutorialState> STATE = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath(MOD_ID, "state"), b -> b.persistent(TutorialState.CODEC).copyOnDeath());

    private static Engine engine;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Show.TYPE, Payloads.Show.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Progress.TYPE, Payloads.Progress.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Payloads.Hide.TYPE, Payloads.Hide.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Payloads.ScreenOpened.TYPE, Payloads.ScreenOpened.CODEC);

        Steps steps = Steps.load();
        engine = new Engine(steps);
        LOGGER.info("Loaded {} tutorial steps and {} tips", steps.chain().size(), steps.tips().size());

        ServerTickEvents.END_SERVER_TICK.register(engine::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> engine.join(handler.player, server.getTickCount()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> engine.leave(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ScreenOpened.TYPE,
            (payload, context) -> engine.screenOpened(context.player(), payload.id()));

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
            Commands.literal("tutorial")
                .executes(Tutorial::status)
                .then(Commands.literal("off").executes(c -> set(c, true, false)))
                .then(Commands.literal("on").executes(c -> set(c, false, false)))
                .then(Commands.literal("restart").executes(c -> set(c, false, true)))));
    }

    private static int status(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TutorialState state = engine.state(player);
        long done = engine.steps().chain().stream().filter(s -> state.done.contains(s.id())).count();
        context.getSource().sendSuccess(() -> Component.translatable("gameoverse_tutorial.command.status",
            Component.translatable(state.off ? "gameoverse_tutorial.command.status.off" : "gameoverse_tutorial.command.status.on"),
            done, engine.steps().chain().size()), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandContext<CommandSourceStack> context, boolean off, boolean restart)
        throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        TutorialState state = engine.state(player);
        if (restart) {
            state.done.clear();
            state.baselines.clear();
        }
        state.off = off;
        player.setAttached(STATE, state);
        engine.reset(player);
        String key = restart ? "restart" : off ? "off" : "on";
        context.getSource().sendSuccess(() -> Component.translatable("gameoverse_tutorial.command." + key), false);
        return Command.SINGLE_SUCCESS;
    }
}
