package net.gameoverse.tutorial;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Vanilla's tutorial toast (same background, layout and colors), with an item as its icon. */
public final class HintToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/tutorial");
    private static final int TEXT_LEFT = 30;
    private static final int TEXT_WIDTH = 126;
    private static final int LINE = 11;

    private final String id;
    private final ItemStack icon;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final boolean progressable;
    private final long durationMs;
    private Visibility visibility = Visibility.SHOW;
    private float progress;
    private float smoothed;
    private long lastSmoothing;

    HintToast(Font font, String id, ItemStack icon, Component title, Component text, float progress, long durationMs) {
        this.id = id;
        this.icon = icon;
        this.lines.addAll(font.split(title.copy().withColor(0xFF500050), TEXT_WIDTH));
        this.lines.addAll(font.split(text, TEXT_WIDTH));
        this.progressable = progress >= 0;
        this.progress = Math.max(0, progress);
        this.smoothed = this.progress;
        this.durationMs = durationMs;
    }

    String id() {
        return id;
    }

    /** Chain hints stay up until the server takes them down; tips time out. */
    boolean isChainHint() {
        return durationMs == 0;
    }

    void hide() {
        visibility = Visibility.HIDE;
    }

    void setProgress(float progress) {
        this.progress = progress;
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long visibleTime) {
        if (durationMs > 0 && visibleTime >= durationMs * manager.getNotificationDisplayTimeMultiplier()) {
            visibility = Visibility.HIDE;
        }
        if (progressable) {
            smoothed = Mth.clampedLerp((visibleTime - lastSmoothing) / 100.0F, smoothed, progress);
            lastSmoothing = visibleTime;
        }
    }

    /** Below Controlify's button guide when it's showing in this corner. */
    @Override
    public float yPos(int firstSlotIndex) {
        return Toast.super.yPos(firstSlotIndex) + ControllerGuide.rightColumnBottom();
    }

    @Override
    public int height() {
        return 7 + Math.max(lines.size(), 2) * LINE + 3 + (progressable ? 4 : 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleTime) {
        int height = height();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height);
        int contentHeight = Math.max(lines.size(), 2) * LINE;
        graphics.item(icon, 8, 7 + contentHeight / 2 - 8);
        int y = 7 + (contentHeight - lines.size() * LINE) / 2;
        for (int i = 0; i < lines.size(); i++) {
            graphics.text(font, lines.get(i), TEXT_LEFT, y + i * LINE, 0xFF000000, false);
        }
        if (progressable) {
            int barY = height - 4 - 1;
            graphics.fill(3, barY, 157, barY + 1, 0xFFFFFFFF);
            graphics.fill(3, barY, 3 + (int) (154 * smoothed), barY + 1, 0xFF500050);
        }
    }
}
