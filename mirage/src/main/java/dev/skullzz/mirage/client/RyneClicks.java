package dev.skullzz.mirage.client;

/**
 * A screen that wants the mouse click, hooked the way this mod hooks it.
 *
 * <p>Top-level, and that is the whole point. This began as an interface nested inside
 * {@link RyneClickScreen}, which then declared {@code implements RyneClickScreen.Clicks} --
 * a class naming a type nested inside itself as its own supertype. Java cannot resolve
 * that: it has to know the supertypes to know what is nested, and it has to resolve what
 * is nested to know the supertypes. The compiler calls it cyclic inheritance, and the
 * damage does not stop at one line -- with RyneClickScreen unresolvable, every other class
 * extending Screen fails too, so one mistake reported as thirty-four errors across seven
 * files that had nothing wrong with them.
 *
 * <p>Both layouts implement this: the stacked menu and the horizontal bar. The click event
 * is subscribed once, in {@link RyneClickScreen#register()}, and dispatched to whichever
 * of them is open.
 */
public interface RyneClicks {

    /** The pointer was clicked somewhere on this screen. Where is for the screen to ask. */
    void onClick();
}
