package net.gameoverse.tutorial;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Walks each online player through the chain and fires tips, twice a second. */
public final class Engine {
    /** Ticks between two tips, so they don't pile up. */
    private static final long TIP_GAP = 20 * 20;

    private final Steps steps;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    Engine(Steps steps) {
        this.steps = steps;
    }

    /** What this login has shown; rebuilt on every join, since the client starts with no hints. */
    public static final class Session {
        String active;
        String shown;
        float lastProgress = -1;
        long lastTip;
        final Map<String, Long> shownAt = new HashMap<>();
        final Set<String> screensSeen = new HashSet<>();
        final Set<String> events = new HashSet<>();

        Session(long tick) {
            lastTip = tick - TIP_GAP;
        }

        public boolean screenSeen(String stepId) {
            return screensSeen.contains(stepId);
        }

        public boolean sawEvent(String name) {
            return events.contains(name);
        }

        public long shownFor(String stepId, long tick) {
            Long at = shownAt.get(stepId);
            return at == null ? 0 : tick - at;
        }
    }

    void join(ServerPlayer player, long tick) {
        sessions.put(player.getUUID(), new Session(tick));
    }

    void leave(ServerPlayer player) {
        sessions.remove(player.getUUID());
    }

    void screenOpened(ServerPlayer player, String stepId) {
        Session session = sessions.get(player.getUUID());
        if (session != null && stepId.equals(session.shown)) {
            session.screensSeen.add(stepId);
        }
    }

    void clientEvent(ServerPlayer player, String name) {
        Session session = sessions.get(player.getUUID());
        if (session != null && name.length() <= 64) {
            session.events.add(name);
        }
    }

    TutorialState state(ServerPlayer player) {
        TutorialState state = player.getAttached(Tutorial.STATE);
        if (state == null) {
            state = new TutorialState();
            long played = player.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
            if (played > steps.veteranPlayHours() * 72000) {
                state.off = true;
            }
            player.setAttached(Tutorial.STATE, state);
        }
        return state;
    }

    Steps steps() {
        return steps;
    }

    /** Take down whatever hint is up and forget this session's progress display (used by the commands). */
    void reset(ServerPlayer player) {
        Session session = sessions.get(player.getUUID());
        if (session != null && session.shown != null) {
            ServerPlayNetworking.send(player, new Payloads.Hide(session.shown));
        }
        sessions.put(player.getUUID(), new Session(player.level().getServer().getTickCount()));
    }

    void tick(MinecraftServer server) {
        long tick = server.getTickCount();
        if (tick % 10 != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(player, Payloads.Show.TYPE)) {
                continue;
            }
            Session session = sessions.computeIfAbsent(player.getUUID(), u -> new Session(tick));
            TutorialState state = state(player);
            if (state.off) {
                if (session.shown != null) {
                    ServerPlayNetworking.send(player, new Payloads.Hide(session.shown));
                    session.shown = null;
                    session.active = null;
                }
                continue;
            }
            boolean changed = chain(player, state, session, tick);
            changed |= tips(player, state, session, tick);
            if (changed) {
                player.setAttached(Tutorial.STATE, state);
            }
        }
    }

    private boolean chain(ServerPlayer player, TutorialState state, Session session, long tick) {
        boolean changed = false;
        for (Steps.Step step : steps.chain()) {
            if (state.done.contains(step.id())) {
                continue;
            }
            Cond.Ctx ctx = new Cond.Ctx(player, state, step.id(), session, tick);
            if (!step.id().equals(session.active)) {
                session.active = step.id();
                step.until().capture(ctx);
                changed = true;
            }
            if (step.until().test(ctx)) {
                state.done.add(step.id());
                state.baselines.keySet().removeIf(k -> k.startsWith(step.id() + "|"));
                if (step.id().equals(session.shown)) {
                    ServerPlayNetworking.send(player, new Payloads.Hide(step.id()));
                    session.shown = null;
                }
                session.active = null;
                changed = true;
                continue;
            }
            float progress = (float) step.until().progress(ctx);
            if (!step.id().equals(session.shown)) {
                ServerPlayNetworking.send(player, new Payloads.Show(step.id(), step.icon(), step.keys(), 0, progress, step.screens()));
                Tutorial.LOGGER.info("Tutorial step {} for {}", step.id(), player.getScoreboardName());
                session.shown = step.id();
                session.shownAt.put(step.id(), tick);
                session.lastProgress = progress;
            } else if (progress >= 0 && Math.abs(progress - session.lastProgress) > 0.001f) {
                ServerPlayNetworking.send(player, new Payloads.Progress(step.id(), progress));
                session.lastProgress = progress;
            }
            break;
        }
        return changed;
    }

    private boolean tips(ServerPlayer player, TutorialState state, Session session, long tick) {
        if (tick - session.lastTip < TIP_GAP) {
            return false;
        }
        for (Steps.Step tip : steps.tips()) {
            if (state.done.contains(tip.id())) {
                continue;
            }
            if (tip.when().test(new Cond.Ctx(player, state, tip.id(), session, tick))) {
                state.done.add(tip.id());
                ServerPlayNetworking.send(player, new Payloads.Show(tip.id(), tip.icon(), tip.keys(), tip.seconds(), -1, java.util.List.of()));
                Tutorial.LOGGER.info("Tutorial tip {} for {}", tip.id(), player.getScoreboardName());
                session.lastTip = tick;
                return true;
            }
        }
        return false;
    }
}
