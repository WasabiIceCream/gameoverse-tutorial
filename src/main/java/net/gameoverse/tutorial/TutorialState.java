package net.gameoverse.tutorial;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A player's tutorial progress, saved on the player and kept on death. */
public final class TutorialState {
    public static final Codec<TutorialState> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.listOf().fieldOf("done").forGetter(s -> List.copyOf(s.done)),
        Codec.BOOL.fieldOf("off").forGetter(s -> s.off),
        Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("baselines").forGetter(s -> Map.copyOf(s.baselines))
    ).apply(i, (done, off, baselines) -> {
        TutorialState state = new TutorialState();
        state.done.addAll(done);
        state.off = off;
        state.baselines.putAll(baselines);
        return state;
    }));

    /** Chain steps and tips already completed or shown. */
    public final Set<String> done = new HashSet<>();
    public boolean off;
    /** Starting values of "since_shown" statistics, by step. */
    public final Map<String, Long> baselines = new HashMap<>();
}
