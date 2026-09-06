package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Short notices that slide in, sit there, and slide out again.
 *
 * <p>Chat is the wrong place for something you only need for two seconds -- it scrolls
 * away and it is in everyone's screenshot of the chat box. A toast says the thing where
 * you are already looking and then stops existing.
 *
 * <p>No Minecraft in here. The timing is the part that goes wrong invisibly: a toast that
 * never expires is a permanent smudge on the screen, and one whose fade is tied to the
 * frame rate looks right on the machine it was written on. Both are run rather than
 * eyeballed.
 */
public final class Toasts {

    /** How long one stays up, in seconds, and how long it takes to arrive and leave. */
    public static final float LIFE = 4.0f;
    public static final float SLIDE = 0.25f;

    /** More than this on screen at once is a wall, not a notice. */
    public static final int MOST = 4;

    /** What a toast is about, which decides its colour. */
    public enum Kind { PLAIN, GOOD, BAD, WARN }

    public static final class Toast {
        public final String text;
        public final Kind kind;
        /** Seconds since it appeared. */
        public float age;

        Toast(String text, Kind kind) {
            this.text = text;
            this.kind = kind;
        }

        /** 0 to 1: how far in it is. Slides in, sits, slides out. */
        public float presence() {
            if (this.age < SLIDE) return this.age / SLIDE;
            float leaving = LIFE - this.age;
            if (leaving < SLIDE) return Math.max(0f, leaving / SLIDE);
            return 1f;
        }

        public boolean done() {
            return this.age >= LIFE;
        }
    }

    private static final List<Toast> LIVE = new ArrayList<>();

    private Toasts() {
    }

    public static List<Toast> live() {
        return LIVE;
    }

    /**
     * Adds one, dropping the oldest if the screen is full.
     *
     * <p>The oldest rather than refusing the newest: what just happened is the thing worth
     * reading, and a queue that fills up and then ignores everything is a queue that stops
     * working exactly when things are happening.
     */
    public static void add(String text, Kind kind) {
        if (text == null || text.isEmpty()) return;

        // The same notice twice in a row restarts rather than stacking, so holding a key
        // does not build a column of identical toasts.
        if (!LIVE.isEmpty()) {
            Toast newest = LIVE.get(LIVE.size() - 1);
            if (newest.text.equals(text)) {
                newest.age = 0f;
                return;
            }
        }

        LIVE.add(new Toast(text, kind));
        while (LIVE.size() > MOST) LIVE.remove(0);
    }

    public static void clear() {
        LIVE.clear();
    }

    /** Ages everything and drops what is spent. */
    public static void tick(float seconds) {
        if (seconds <= 0) return;
        for (Toast toast : LIVE) toast.age += seconds;
        LIVE.removeIf(Toast::done);
    }
}
