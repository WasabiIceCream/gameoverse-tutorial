package net.gameoverse.tutorial;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** tutorial/steps.json from the jar: the chain, the tips and the veteran cut-off. */
public record Steps(List<Step> chain, List<Step> tips, double veteranPlayHours) {
    /** {@code until} completes a chain step; {@code when} triggers a tip, shown for {@code seconds}. */
    public record Step(String id, String icon, List<String> keys, Cond until, Cond when, int seconds, List<String> screens) {
    }

    static Steps load() {
        Path path = FabricLoader.getInstance().getModContainer(Tutorial.MOD_ID).orElseThrow()
            .findPath("tutorial/steps.json").orElseThrow();
        JsonObject root;
        try {
            root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("Can't read tutorial/steps.json", e);
        }
        List<Step> chain = new ArrayList<>();
        root.getAsJsonArray("chain").forEach(e -> chain.add(step(e)));
        List<Step> tips = new ArrayList<>();
        root.getAsJsonArray("tips").forEach(e -> tips.add(step(e)));
        double hours = root.has("veteran_play_hours") ? root.get("veteran_play_hours").getAsDouble() : 2;
        return new Steps(List.copyOf(chain), List.copyOf(tips), hours);
    }

    private static Step step(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        List<String> keys = new ArrayList<>();
        if (o.has("keys")) {
            o.getAsJsonArray("keys").forEach(k -> keys.add(k.getAsString()));
        }
        Cond until = o.has("until") ? Cond.parse(o.get("until")) : null;
        Cond when = o.has("when") ? Cond.parse(o.get("when")) : null;
        List<String> screens = new ArrayList<>();
        if (until != null) {
            until.screens(screens);
        }
        return new Step(o.get("id").getAsString(), o.get("icon").getAsString(), List.copyOf(keys), until, when,
            o.has("seconds") ? o.get("seconds").getAsInt() : 0, List.copyOf(screens));
    }
}
