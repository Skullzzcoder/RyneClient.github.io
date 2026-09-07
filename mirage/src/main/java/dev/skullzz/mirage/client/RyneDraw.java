package dev.skullzz.mirage.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.font.TextRenderer;

/**
 * Everything the screens paint with, built out of one call.
 *
 * <p>{@link #box} is the only call in this mod that has not already been watched compile:
 * nothing else fills a rectangle. It lives here, alone, so that if {@code fill} has been
 * renamed in some version of Minecraft it is one compile error in one method rather than
 * one in every screen -- and everything below is built from it, so gradients, shadows and
 * corners cost nothing extra in risk.
 */
public final class RyneDraw {

    private RyneDraw() {
    }

    /** A filled rectangle, in 0xAARRGGBB. */
    public static void box(DrawContext context, int x, int y, int width, int height,
                           int colour) {
        context.fill(x, y, x + width, y + height, colour);
    }

    /**
     * A vertical fade, drawn as a stack of thin bands.
     *
     * <p>Minecraft can do this in one call, but the name for it is not one this mod has
     * seen compile and a gradient is not worth a build failure. Sixteen bands is past
     * where the eye can see the steps at these heights, and sixteen rectangles is nothing.
     */
    public static void gradient(DrawContext context, int x, int y, int width, int height,
                                int top, int bottom) {
        if (height <= 0 || width <= 0) return;

        int bands = Math.min(16, height);
        for (int i = 0; i < bands; i++) {
            int bandY = y + i * height / bands;
            int nextY = y + (i + 1) * height / bands;
            box(context, x, bandY, width, Math.max(1, nextY - bandY),
                    RyneGui.blend(top, bottom, bands == 1 ? 0f : i / (float) (bands - 1)));
        }
    }

    /**
     * A rectangle with its corners taken off.
     *
     * <p>Three stacked rectangles, insetting the top and bottom rows by a pixel each. Not
     * a real curve -- there is no way to draw one out of axis-aligned rectangles -- but at
     * two pixels the eye reads it as a rounded corner rather than as a chamfer, and it is
     * the difference between a panel that looks drawn and one that looks placed.
     */
    public static void rounded(DrawContext context, int x, int y, int width, int height,
                               int colour) {
        if (width <= 4 || height <= 4) {
            box(context, x, y, width, height, colour);
            return;
        }
        box(context, x + 2, y, width - 4, 1, colour);
        box(context, x + 1, y + 1, width - 2, 1, colour);
        box(context, x, y + 2, width, height - 4, colour);
        box(context, x + 1, y + height - 2, width - 2, 1, colour);
        box(context, x + 2, y + height - 1, width - 4, 1, colour);
    }

    /**
     * A soft edge under something, so it reads as sitting above the game rather than
     * printed on it.
     *
     * <p>Each ring is fainter than the last, which is what a shadow is; a single dark
     * rectangle behind a panel just looks like a second panel.
     */
    public static void shadow(DrawContext context, int x, int y, int width, int height,
                              int depth) {
        for (int i = depth; i >= 1; i--) {
            int alpha = Math.max(4, 40 / i);
            box(context, x - i, y - i, width + i * 2, height + i * 2, (alpha << 24));
        }
    }

    /** A one-pixel outline, for the panel that is being dragged. */
    public static void outline(DrawContext context, int x, int y, int width, int height,
                               int colour) {
        box(context, x, y, width, 1, colour);
        box(context, x, y + height - 1, width, 1, colour);
        box(context, x, y, 1, height, colour);
        box(context, x + width - 1, y, 1, height, colour);
    }

    public static void text(DrawContext context, TextRenderer renderer, String message,
                            int x, int y, int colour) {
        context.drawTextWithShadow(renderer, Text.literal(message), x, y, colour);
    }

    /**
     * A heading: capitals, spaced out, drawn one character at a time.
     *
     * <p>The one thing that most changes how a menu reads. A title set like this stops
     * looking like a sentence that happens to be at the top and starts looking like a
     * label -- and it costs nothing but a loop, because it is the same proven call as
     * every other piece of text, run once per character at an offset {@link RyneType}
     * worked out.
     */
    public static void heading(DrawContext context, TextRenderer renderer, String message,
                               int x, int y, int colour) {
        tracked(context, renderer, RyneType.caps(message), x, y, colour, RyneType.TRACKING);
    }

    /** The same, at a spacing of your choosing. */
    public static void tracked(DrawContext context, TextRenderer renderer, String message,
                               int x, int y, int colour, int tracking) {
        if (message == null || message.isEmpty()) return;
        if (tracking <= 0) {
            text(context, renderer, message, x, y, colour);
            return;
        }

        int[] offsets = RyneType.offsets(message, tracking);
        for (int i = 0; i < message.length(); i++) {
            text(context, renderer, String.valueOf(message.charAt(i)), x + offsets[i], y,
                    colour);
        }
    }

    /** Text ending at {@code right}, for a column of values that should line up. */
    public static void textRight(DrawContext context, TextRenderer renderer, String message,
                                 int right, int y, int colour) {
        text(context, renderer, message, RyneType.rightX(right, message, 0), y, colour);
    }

    /** Text in the middle of a box that starts at {@code x}. */
    public static void textCentre(DrawContext context, TextRenderer renderer, String message,
                                  int x, int width, int y, int colour) {
        text(context, renderer, message, RyneType.centreX(x, width, message, 0), y, colour);
    }

    /**
     * A divider: one faint line with a brighter stub at its left.
     *
     * <p>A full-width line at full strength cuts a panel in half. Fading it out is what
     * makes it read as a separation rather than a border.
     */
    public static void rule(DrawContext context, int x, int y, int width, int colour,
                            int accent) {
        gradient(context, x, y, width, 1, colour, colour & 0x00FFFFFF);
        box(context, x, y, Math.min(12, width), 1, accent);
    }

    /** Cut to fit a column, so a long name cannot run into the next one. */
    public static String trim(String message, int most) {
        return message.length() <= most ? message : message.substring(0, most - 1) + "...";
    }
}
