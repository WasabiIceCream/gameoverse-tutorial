package net.gameoverse.tutorial;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A condition from steps.json, checked on the server against one player. {@link #progress} is the share done for a
 * counting condition (-1 when it doesn't count), for the toast's progress bar.
 */
public interface Cond {
    boolean test(Ctx ctx);

    default double progress(Ctx ctx) {
        return -1;
    }

    /** Conditions measured from the moment a step shows record their starting value here. */
    default void capture(Ctx ctx) {
    }

    /** Screen class names this condition waits for (the client reports when one opens). */
    default void screens(List<String> out) {
    }

    /** What a condition is checked against: the player, their saved state, the step and this session. */
    record Ctx(ServerPlayer player, TutorialState state, String stepId, Engine.Session session, long tick) {
    }

    static Cond parse(JsonElement json) {
        JsonObject o = json.getAsJsonObject();
        if (o.has("any")) {
            List<Cond> parts = list(o.getAsJsonArray("any"));
            return new Cond() {
                public boolean test(Ctx c) { return parts.stream().anyMatch(p -> p.test(c)); }
                public double progress(Ctx c) { return parts.stream().mapToDouble(p -> p.progress(c)).max().orElse(-1); }
                public void capture(Ctx c) { parts.forEach(p -> p.capture(c)); }
                public void screens(List<String> out) { parts.forEach(p -> p.screens(out)); }
            };
        }
        if (o.has("all")) {
            List<Cond> parts = list(o.getAsJsonArray("all"));
            return new Cond() {
                public boolean test(Ctx c) { return parts.stream().allMatch(p -> p.test(c)); }
                public void capture(Ctx c) { parts.forEach(p -> p.capture(c)); }
                public void screens(List<String> out) { parts.forEach(p -> p.screens(out)); }
            };
        }
        if (o.has("item")) {
            return new ItemCond(o.get("item").getAsString(), o.has("count") ? o.get("count").getAsInt() : 1,
                o.has("model") ? Identifier.parse(o.get("model").getAsString()) : null);
        }
        if (o.has("stat")) {
            return new StatCond(Identifier.parse(o.get("stat").getAsString()), o.get("key").getAsString(),
                o.has("min") ? o.get("min").getAsLong() : 1, o.has("since_shown") && o.get("since_shown").getAsBoolean());
        }
        if (o.has("advancement")) {
            Identifier id = Identifier.parse(o.get("advancement").getAsString());
            return c -> {
                AdvancementHolder holder = c.player().level().getServer().getAdvancements().get(id);
                return holder != null && c.player().getAdvancements().getOrStartProgress(holder).isDone();
            };
        }
        if (o.has("effect")) {
            Identifier id = Identifier.parse(o.get("effect").getAsString());
            return c -> {
                Optional<Holder.Reference<MobEffect>> effect = BuiltInRegistries.MOB_EFFECT.get(id);
                return effect.isPresent() && c.player().hasEffect(effect.get());
            };
        }
        if (o.has("permission")) {
            List<String> nodes = new ArrayList<>();
            o.getAsJsonArray("permission").forEach(e -> nodes.add(e.getAsString()));
            return c -> Permissions.any(c.player(), nodes);
        }
        if (o.has("screen")) {
            List<String> names = new ArrayList<>();
            o.getAsJsonArray("screen").forEach(e -> names.add(e.getAsString()));
            return new Cond() {
                public boolean test(Ctx c) { return c.session().screenSeen(c.stepId()); }
                public void screens(List<String> out) { out.addAll(names); }
            };
        }
        if (o.has("client_event")) {
            String name = o.get("client_event").getAsString();
            return c -> c.session().sawEvent(name);
        }
        if (o.has("chain_done")) {
            String id = o.get("chain_done").getAsString();
            return c -> c.state().done.contains(id);
        }
        if (o.has("shown_seconds")) {
            int seconds = o.get("shown_seconds").getAsInt();
            return c -> c.session().shownFor(c.stepId(), c.tick()) >= seconds * 20L;
        }
        if (o.has("play_minutes")) {
            int minutes = o.get("play_minutes").getAsInt();
            return c -> c.player().getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) >= minutes * 1200;
        }
        throw new IllegalArgumentException("Unknown tutorial condition: " + json);
    }

    private static List<Cond> list(JsonArray array) {
        List<Cond> out = new ArrayList<>();
        array.forEach(e -> out.add(parse(e)));
        return out;
    }

    /** Holds at least {@code count} of an item, an item tag (#id), optionally with a given item model. */
    final class ItemCond implements Cond {
        private final TagKey<Item> tag;
        private final Identifier item;
        private final int count;
        private final Identifier model;

        private final boolean campfireResult;
        private static java.util.Set<Item> campfireResults;

        ItemCond(String key, int count, Identifier model) {
            this.campfireResult = key.equals("*campfire_result");
            this.tag = key.startsWith("#") ? TagKey.create(Registries.ITEM, Identifier.parse(key.substring(1))) : null;
            this.item = key.startsWith("#") || campfireResult ? null : Identifier.parse(key);
            this.count = count;
            this.model = model;
        }

        /** Everything a campfire recipe makes (vanilla's cooked tags cover 28 items; our campfire cooks far more). */
        private static java.util.Set<Item> campfireResults(ServerPlayer player) {
            if (campfireResults == null) {
                java.util.Set<Item> out = new java.util.HashSet<>();
                for (var holder : player.level().getServer().getRecipeManager().getRecipes()) {
                    if (!(holder.value() instanceof net.minecraft.world.item.crafting.CampfireCookingRecipe recipe)) {
                        continue;
                    }
                    ItemStack result = recipe.assemble(new net.minecraft.world.item.crafting.SingleRecipeInput(ItemStack.EMPTY));
                    if (!result.isEmpty()) {
                        out.add(result.getItem());
                    }
                }
                campfireResults = java.util.Set.copyOf(out);
                Tutorial.LOGGER.info("{} items count as campfire-cooked for the tutorial", campfireResults.size());
            }
            return campfireResults;
        }

        private boolean matches(ItemStack stack, ServerPlayer player) {
            if (stack.isEmpty()) {
                return false;
            }
            if (campfireResult) {
                return campfireResults(player).contains(stack.getItem());
            }
            if (tag != null ? !stack.is(tag) : !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)) {
                return false;
            }
            return model == null || model.equals(stack.get(DataComponents.ITEM_MODEL));
        }

        private int held(ServerPlayer player) {
            Inventory inventory = player.getInventory();
            int total = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (matches(stack, player)) {
                    total += stack.getCount();
                }
            }
            ItemStack carried = player.containerMenu.getCarried();
            if (matches(carried, player)) {
                total += carried.getCount();
            }
            return total;
        }

        @Override
        public boolean test(Ctx c) {
            return held(c.player()) >= count;
        }

        @Override
        public double progress(Ctx c) {
            return count > 1 ? Math.min(1.0, held(c.player()) / (double) count) : -1;
        }
    }

    /** A statistic (crafted, used, mined, custom...) of one id or a tag, summed; optionally counted from when the step showed. */
    final class StatCond implements Cond {
        private final Identifier type;
        private final String key;
        private final long min;
        private final boolean sinceShown;

        StatCond(Identifier type, String key, long min, boolean sinceShown) {
            this.type = type;
            this.key = key;
            this.min = min;
            this.sinceShown = sinceShown;
        }

        private static List<Item> foods;

        private static List<Item> foods() {
            if (foods == null) {
                List<Item> out = new ArrayList<>();
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item.components().has(DataComponents.FOOD)) {
                        out.add(item);
                    }
                }
                foods = List.copyOf(out);
            }
            return foods;
        }

        private String baselineKey(Ctx c) {
            return c.stepId() + "|" + type + "|" + key;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private long value(ServerPlayer player) {
            StatType statType = BuiltInRegistries.STAT_TYPE.getValue(type);
            if (statType == null) {
                return 0;
            }
            Registry registry = statType.getRegistry();
            long total = 0;
            if (key.equals("*food") && statType.getRegistry() == BuiltInRegistries.ITEM) {
                // Any item that is food: tags like #c:foods miss many mods' foods (500 of 945 here)
                for (Item item : foods()) {
                    total += player.getStats().getValue(statType.get(item));
                }
            } else if (key.startsWith("#")) {
                TagKey tagKey = TagKey.create(registry.key(), Identifier.parse(key.substring(1)));
                for (Object holder : (Iterable<?>) registry.getTagOrEmpty(tagKey)) {
                    total += player.getStats().getValue(statType.get(((Holder) holder).value()));
                }
            } else {
                Identifier id = Identifier.parse(key);
                if (!registry.containsKey(id)) {
                    return 0;
                }
                total = player.getStats().getValue(statType.get(registry.getValue(id)));
            }
            return total;
        }

        private long counted(Ctx c) {
            long now = value(c.player());
            if (!sinceShown) {
                return now;
            }
            Long base = c.state().baselines.get(baselineKey(c));
            return base == null ? 0 : now - base;
        }

        @Override
        public void capture(Ctx c) {
            if (sinceShown) {
                c.state().baselines.putIfAbsent(baselineKey(c), value(c.player()));
            }
        }

        @Override
        public boolean test(Ctx c) {
            return counted(c) >= min;
        }

        @Override
        public double progress(Ctx c) {
            return min > 1 ? Math.min(1.0, counted(c) / (double) min) : -1;
        }
    }

    /** LuckPerms groups through the Fabric Permissions API, when some mod ships it; without it every check passes. */
    final class Permissions {
        private static Method check;
        private static boolean looked;

        static boolean any(ServerPlayer player, List<String> nodes) {
            if (!looked) {
                looked = true;
                try {
                    check = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions")
                        .getMethod("check", Entity.class, String.class);
                } catch (ReflectiveOperationException e) {
                    Tutorial.LOGGER.warn("No Fabric Permissions API; the verify step will be skipped");
                }
            }
            if (check == null) {
                return true;
            }
            try {
                for (String node : nodes) {
                    if ((boolean) check.invoke(null, player, node)) {
                        return true;
                    }
                }
            } catch (ReflectiveOperationException e) {
                Tutorial.LOGGER.warn("Permission check failed", e);
                return true;
            }
            return false;
        }
    }
}
