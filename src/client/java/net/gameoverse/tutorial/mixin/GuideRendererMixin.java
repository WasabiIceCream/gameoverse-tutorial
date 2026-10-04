package net.gameoverse.tutorial.mixin;

import dev.isxander.controlify.gui.guide.PrecomputedLines;
import net.gameoverse.tutorial.HintHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Controlify's in-game button guide draws its left column from the top-left corner; while tutorial hints show there,
 * the column moves down under them. Only in game (no screen open) and only with the guide at the top.
 * {@code GuideRenderer.extractLines(graphics, lines, font, width, height, bottom, right, ...)}, Controlify 3.5.3;
 * own optional mixin config with require 0, so a changed Controlify just leaves the guide where it was.
 */
@Mixin(targets = "dev.isxander.controlify.gui.guide.GuideRenderer")
abstract class GuideRendererMixin {
    @Unique
    private static boolean gameoverseTutorial$pushed;

    @Inject(method = "extractLines", at = @At("HEAD"))
    private static void gameoverseTutorial$moveUnderHints(GuiGraphicsExtractor graphics, PrecomputedLines lines, Font font,
        int width, int height, boolean bottom, boolean right, boolean flag, CallbackInfo ci) {
        gameoverseTutorial$pushed = false;
        int offset = HintHud.bottom();
        if (bottom || right || offset <= 0 || Minecraft.getInstance().screen != null) {
            return;
        }
        // The guide may be drawn at its own GUI scale; translate in its scaled units
        float scale = graphics.pose().m11();
        graphics.pose().pushMatrix();
        graphics.pose().translate(0, offset / (scale == 0 ? 1 : scale));
        gameoverseTutorial$pushed = true;
    }

    @Inject(method = "extractLines", at = @At("RETURN"))
    private static void gameoverseTutorial$restore(GuiGraphicsExtractor graphics, PrecomputedLines lines, Font font,
        int width, int height, boolean bottom, boolean right, boolean flag, CallbackInfo ci) {
        if (gameoverseTutorial$pushed) {
            graphics.pose().popMatrix();
            gameoverseTutorial$pushed = false;
        }
    }
}
