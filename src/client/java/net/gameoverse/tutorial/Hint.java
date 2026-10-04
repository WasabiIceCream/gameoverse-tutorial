package net.gameoverse.tutorial;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One hint in vanilla's tutorial-toast look (same background, layout and colors) with an item as its icon. Drawn by
 * {@link HintHud} in the top-left corner instead of the toast corner, sliding in and out from the left.
 */
final class Hint {
    static final int WIDTH = 160;
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/tutorial");
    private static final int TEXT_LEFT = 30;
    private static final int TEXT_WIDTH = 126;
    private static final int LINE = 11;
    private static final long SLIDE_MS = 300;

    final String id;
    private final ItemStack icon;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final boolean progressable;
    private final long durationMs;
    private long shownAt = Util.getMillis();
    private long pausedAt = -1;
    private long hiddenAt = -1;
    private float progress;
    private float smoothed;

    Hint(Font font, String id, ItemStack icon, Component title, Component text, float progress, long durationMs) {
        this.id = id;
        this.icon = icon;
        this.lines.addAll(font.split(title.copy().withColor(0xFF500050), TEXT_WIDTH));
        this.lines.addAll(font.split(text, TEXT_WIDTH));
        this.progressable = progress >= 0;
        this.progress = Math.max(0, progress);
        this.smoothed = this.progress;
        this.durationMs = durationMs;
    }

    /** Chain hints stay up until the server takes them down; tips time out. */
    boolean isChainHint() {
        return durationMs == 0;
    }

    void hide() {
        if (hiddenAt < 0) {
            hiddenAt = Util.getMillis();
        }
    }

    void setProgress(float progress) {
        this.progress = progress;
    }

    /** While hints are hidden (F1, F3) a tip's time doesn't run, so it always gets its full time on screen. */
    void pause(long now) {
        if (pausedAt < 0) {
            pausedAt = now;
        }
    }

    void resume(long now) {
        if (pausedAt >= 0) {
            shownAt += now - pausedAt;
            if (hiddenAt >= 0) {
                hiddenAt += now - pausedAt;
            }
            pausedAt = -1;
        }
    }

    /** Fully slid out and can be dropped. */
    boolean finished(long now) {
        if (pausedAt >= 0) {
            return false;
        }
        if (hiddenAt < 0 && durationMs > 0 && now - shownAt >= durationMs) {
            hiddenAt = now;
        }
        return hiddenAt >= 0 && now - hiddenAt >= SLIDE_MS;
    }

    /** How far in it has slid, 0 to 1. */
    float visible(long now) {
        float in = Mth.clamp((now - shownAt) / (float) SLIDE_MS, 0, 1);
        float out = hiddenAt < 0 ? 1 : 1 - Mth.clamp((now - hiddenAt) / (float) SLIDE_MS, 0, 1);
        float t = Math.min(in, out);
        return 1 - (1 - t) * (1 - t);
    }

    int height() {
        return 7 + Math.max(lines.size(), 2) * LINE + 3 + (progressable ? 4 : 0);
    }

    void draw(GuiGraphicsExtractor graphics, Font font, int x, int y) {
        if (progressable) {
            smoothed += (progress - smoothed) * 0.15F;
        }
        int height = height();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, WIDTH, height);
        int contentHeight = Math.max(lines.size(), 2) * LINE;
        graphics.item(icon, x + 8, y + 7 + contentHeight / 2 - 8);
        int textY = y + 7 + (contentHeight - lines.size() * LINE) / 2;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), x + TEXT_LEFT, textY + i * LINE, 0xFF000000, false);
        }
        if (progressable) {
            int barY = y + height - 4 - 1;
            graphics.fill(x + 3, barY, x + 157, barY + 1, 0xFFFFFFFF);
            graphics.fill(x + 3, barY, x + 3 + (int) (154 * Mth.clamp(smoothed, 0, 1)), barY + 1, 0xFF500050);
        }
    }
}
