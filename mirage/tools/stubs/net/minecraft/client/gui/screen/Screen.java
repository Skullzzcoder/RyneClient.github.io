package net.minecraft.client.gui.screen;

/**
 * Enough of Minecraft's Screen for javac to attribute the bodies of the mod's own screens.
 *
 * <p>Not a reimplementation and not used at runtime -- it exists only so the offline
 * compile in check-compile.py can get past "extends Screen" and actually analyse what is
 * inside. Without it javac treats the whole class as erroneous and never looks at a single
 * method body, which is how a duplicate local and an override that narrowed access both
 * reached a real build.
 *
 * <p>Only the members the mod overrides or reads need to be right. close() being public
 * here is the entire point of one of the errors this caught.
 */
public abstract class Screen {
    public int width;
    public int height;

    protected Screen(Object title) {
    }

    public void close() {
    }

    protected void init() {
    }

    public boolean shouldPause() {
        return true;
    }
}
