package net.gameoverse.tutorial;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The tutorial's own HUD corner: the top left. The top right already holds status effects, the Atlas minimap, the
 * compass and clock read-outs, Controlify's right column and every toast, and a chain hint can stay up for minutes,
 * so it gets a corner of its own; Controlify's left column moves down under it (see GuideRendererMixin).
 */
public final class HintHud {
    private static final int MARGIN = 2;
    private static final int GAP = 2;
    private static final List<Hint> HINTS = new ArrayList<>();
    private static int bottom;

    private HintHud() {
    }

    static void add(Hint hint) {
        // The chain hint goes first, tips under it
        if (hint.isChainHint()) {
            HINTS.add(0, hint);
        } else {
            HINTS.add(hint);
        }
    }

    static Hint find(String id) {
        for (Hint hint : HINTS) {
            if (hint.id.equals(id)) {
                return hint;
            }
        }
        return null;
    }

    static List<Hint> all() {
        return HINTS;
    }

    static void clear() {
        HINTS.clear();
        bottom = 0;
    }

    /** How far down the hints reached when last drawn, in GUI pixels (0 when none show). */
    public static int bottom() {
        return bottom;
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        if (mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            HINTS.forEach(h -> h.pause(now));
            bottom = 0;
            return;
        }
        HINTS.forEach(h -> h.resume(now));
        HINTS.removeIf(h -> h.finished(now));
        if (HINTS.isEmpty()) {
            bottom = 0;
            return;
        }
        int y = MARGIN;
        for (Hint hint : new ArrayList<>(HINTS)) {
            float visible = hint.visible(now);
            int x = MARGIN - Math.round((Hint.WIDTH + MARGIN) * (1 - visible));
            hint.draw(graphics, mc.font, x, y);
            y += Math.round((hint.height() + GAP) * visible);
        }
        bottom = y;
    }
}
