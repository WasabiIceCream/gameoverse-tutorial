package net.gameoverse.tutorial;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The tutorial's own HUD corner: top left, under Controlify's left button-guide column when that's showing. The top
 * right already holds status effects, the Atlas minimap, the compass and clock read-outs, Controlify's right column
 * and every toast, and a chain hint can stay up for minutes, so it gets a corner of its own.
 */
final class HintHud {
    private static final int MARGIN = 2;
    private static final int GAP = 2;
    private static final List<Hint> HINTS = new ArrayList<>();

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
    }

    static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        HINTS.removeIf(h -> h.finished(now));
        if (HINTS.isEmpty() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }
        int y = MARGIN + ControllerGuide.leftColumnBottom();
        for (Hint hint : new ArrayList<>(HINTS)) {
            float visible = hint.visible(now);
            int x = MARGIN - Math.round((Hint.WIDTH + MARGIN) * (1 - visible));
            hint.draw(graphics, mc.font, x, y);
            y += Math.round((hint.height() + GAP) * visible);
        }
    }
}
