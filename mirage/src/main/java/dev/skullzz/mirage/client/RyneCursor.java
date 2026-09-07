package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The pointer's trail, and the rings a click throws off.
 *
 * <p>No Minecraft in here. What it holds is a handful of points with ages on them and a
 * handful of expanding rings, all advanced by however many seconds have passed -- which is
 * arithmetic, and so can be run and checked rather than squinted at.
 *
 * <p>Two things are easy to get wrong here and both are the same mistake in different
 * clothes: leaving something in a list that should have aged out. A trail that never drops
 * its oldest point becomes a permanent smear across the screen, and a ripple that never
 * expires means every click ever made is still being drawn a thousand frames later. Both
 * look fine for the first few seconds of testing, which is exactly why they are asserted.
 */
public final class RyneCursor {

    /** How many points the tail is made of. Past this it reads as a smear, not a trail. */
    public static final int TAIL = 14;

    /** How long a point in the tail lives. */
    public static final float TAIL_SECONDS = 0.32f;

    /** How long a click ring lives, and how far it gets. */
    public static final float RING_SECONDS = 0.45f;
    public static final int RING_REACH = 26;

    /** More than this at once and a fast clicker is drawing rings over the whole menu. */
    public static final int RINGS = 6;

    /**
     * Whether the effects are drawn at all.
     *
     * <p>On by default and off in one command. A flourish nobody can turn off is not a
     * flourish, and a trail is exactly the sort of thing that is lovely for a week.
     */
    private static boolean on = true;

    public static boolean on() {
        return on;
    }

    public static void setOn(boolean drawing) {
        on = drawing;
    }

    /** One point of the tail. */
    public static final class Point {
        public final int x;
        public final int y;
        /** Seconds since it was laid down. */
        public float age;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /** 1 when just laid down, 0 as it expires. */
        public float life() {
            return Math.max(0f, 1f - this.age / TAIL_SECONDS);
        }
    }

    /** One ring thrown off by a click. */
    public static final class Ring {
        public final int x;
        public final int y;
        public float age;

        Ring(int x, int y) {
            this.x = x;
            this.y = y;
        }

        public float life() {
            return Math.max(0f, 1f - this.age / RING_SECONDS);
        }

        /** How far out it has got. Eased, so it leaps out and settles rather than crawls. */
        public int radius() {
            float through = Math.min(1f, this.age / RING_SECONDS);
            float eased = 1f - (1f - through) * (1f - through);
            return Math.round(RING_REACH * eased);
        }
    }

    private final List<Point> tail = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    public List<Point> tail() {
        return this.tail;
    }

    public List<Ring> rings() {
        return this.rings;
    }

    /**
     * Where the pointer is now.
     *
     * <p>A point is only laid down when it has actually moved. A still pointer laying one
     * down every frame stacks {@link #TAIL} of them on the same pixel, which draws as a
     * hard blob sitting under the cursor rather than as nothing at all.
     */
    public void move(int x, int y) {
        if (x == this.lastX && y == this.lastY) return;

        this.lastX = x;
        this.lastY = y;
        this.tail.add(new Point(x, y));
        while (this.tail.size() > TAIL) this.tail.remove(0);
    }

    /** A click, at the point it happened. */
    public void click(int x, int y) {
        this.rings.add(new Ring(x, y));
        while (this.rings.size() > RINGS) this.rings.remove(0);
    }

    /**
     * Ages everything by the time that has passed, and drops whatever has expired.
     *
     * @param seconds since the last frame; ignored if it is not a sane number, since a
     *     paused game and a first frame can both hand over something enormous
     */
    public void tick(float seconds) {
        if (!(seconds > 0f) || seconds > 1f) return;

        for (int i = this.tail.size() - 1; i >= 0; i--) {
            Point point = this.tail.get(i);
            point.age += seconds;
            if (point.age >= TAIL_SECONDS) this.tail.remove(i);
        }
        for (int i = this.rings.size() - 1; i >= 0; i--) {
            Ring ring = this.rings.get(i);
            ring.age += seconds;
            if (ring.age >= RING_SECONDS) this.rings.remove(i);
        }
    }

    /** Everything gone, for a screen closing or opening. */
    public void clear() {
        this.tail.clear();
        this.rings.clear();
        this.lastX = Integer.MIN_VALUE;
        this.lastY = Integer.MIN_VALUE;
    }

    /**
     * How wide the tail is at a given point, newest first.
     *
     * <p>Tapered, so it reads as a trail with a direction rather than a line of dots.
     */
    public static int thickness(int fromNewest, float life) {
        int taper = Math.max(1, 5 - fromNewest / 3);
        return Math.max(1, Math.round(taper * life));
    }
}
