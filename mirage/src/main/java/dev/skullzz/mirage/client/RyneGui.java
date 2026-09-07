package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The click GUI's shape: panels, where they are, what is under the pointer, and how far
 * through an animation each one is.
 *
 * <p>No Minecraft in here. Hit testing and drag arithmetic are exactly the kind of thing
 * that is wrong by four pixels and looks almost right, and easing that is tied to the
 * frame rate looks fine at 60fps and wrong at 30 -- so all of it is kept where it can be
 * run and checked rather than eyeballed in game.
 *
 * <p>What a row does is a {@link Runnable} and what it shows is a {@link BooleanSupplier},
 * both of which are the JDK's, so the panels can drive the real mod without this file
 * knowing anything about it.
 */
public final class RyneGui {

    public static final int TITLE_HEIGHT = 20;
    public static final int ROW_HEIGHT = 18;
    public static final int PANEL_WIDTH = 132;

    /** Below this a panel counts as shut and its rows stop taking clicks. */
    private static final float SHUT = 0.02f;

    /** What kind of thing a row is, which decides how it is drawn. */
    public enum Kind { TOGGLE, ACTION, LABEL }

    /** What shape a setting is, which is all the drawing needs to know about it. */
    public enum Shape { SWITCH, SLIDER, MODE }

    /**
     * One knob under a module: a switch, a number, or a choice from a list.
     *
     * <p>The value lives wherever it already lived -- a field on a rig, a flag in
     * SelfFakes -- and is reached through the two lambdas rather than copied here. A
     * settings panel holding its own copy of a value is a settings panel that disagrees
     * with the mod as soon as anything changes it any other way, and every one of these
     * can also be changed by a command.
     */
    public static final class Setting {
        public final String label;
        public final Shape shape;
        /** Reads the value now. For a SWITCH, 0 or 1; for a MODE, the index chosen. */
        public final java.util.function.DoubleSupplier get;
        /** Writes it. Given the same units {@link #get} reads. */
        public final java.util.function.DoubleConsumer set;
        /** SLIDER only. */
        public final double least;
        public final double most;
        public final double step;
        /** MODE only: what the indices mean. */
        public final List<String> options;

        private Setting(String label, Shape shape, java.util.function.DoubleSupplier get,
                        java.util.function.DoubleConsumer set, double least, double most,
                        double step, List<String> options) {
            this.label = label;
            this.shape = shape;
            this.get = get;
            this.set = set;
            this.least = least;
            this.most = most;
            this.step = step;
            this.options = options == null ? List.of() : options;
        }

        public static Setting switching(String label, BooleanSupplier get,
                                        java.util.function.Consumer<Boolean> set) {
            return new Setting(label, Shape.SWITCH, () -> get.getAsBoolean() ? 1 : 0,
                    value -> set.accept(value >= 0.5), 0, 1, 1, null);
        }

        public static Setting slider(String label, java.util.function.DoubleSupplier get,
                                     java.util.function.DoubleConsumer set,
                                     double least, double most, double step) {
            return new Setting(label, Shape.SLIDER, get, set, least, most,
                    step <= 0 ? 1 : step, null);
        }

        public static Setting mode(String label, java.util.function.DoubleSupplier get,
                                   java.util.function.DoubleConsumer set,
                                   List<String> options) {
            return new Setting(label, Shape.MODE, get, set, 0,
                    Math.max(0, (options == null ? 1 : options.size()) - 1), 1, options);
        }

        public boolean on() {
            return this.get.getAsDouble() >= 0.5;
        }

        /** The value now, never outside its own bounds however it got set. */
        public double value() {
            return clamp(this.get.getAsDouble());
        }

        public double clamp(double value) {
            if (this.shape == Shape.SLIDER || this.shape == Shape.MODE) {
                return Math.max(this.least, Math.min(this.most, value));
            }
            return value >= 0.5 ? 1 : 0;
        }

        /** Where the handle sits, 0 at the left and 1 at the right. */
        public double fraction() {
            double span = this.most - this.least;
            return span <= 0 ? 0 : (value() - this.least) / span;
        }

        /**
         * Sets from a position along the track, snapped to the step.
         *
         * <p>Snapped before it is stored rather than only when it is shown, or a slider
         * reading "5" is holding 5.31 and every sum done with it is quietly a little off.
         */
        public void setFraction(double fraction) {
            double span = this.most - this.least;
            double raw = this.least + Math.max(0, Math.min(1, fraction)) * span;
            double snapped = this.least + Math.round((raw - this.least) / this.step)
                    * this.step;
            this.set.accept(clamp(snapped));
        }

        /** Steps a mode along, wrapping, for a click rather than a drag. */
        public void step(int delta) {
            if (this.shape == Shape.SWITCH) {
                this.set.accept(on() ? 0 : 1);
                return;
            }
            int count = (int) Math.round(this.most - this.least) + 1;
            if (count <= 0) return;
            int at = (int) Math.round(value() - this.least);
            this.set.accept(this.least + Math.floorMod(at + delta, count));
        }

        /** What it reads as, for the row it sits on. */
        public String shown() {
            switch (this.shape) {
                case SWITCH:
                    return on() ? "on" : "off";
                case MODE: {
                    int at = (int) Math.round(value());
                    return at >= 0 && at < this.options.size() ? this.options.get(at) : "-";
                }
                default: {
                    double value = value();
                    return value == Math.rint(value) && Math.abs(value) < 1e9
                            ? String.valueOf((long) value)
                            : String.format(java.util.Locale.ROOT, "%.2f", value);
                }
            }
        }
    }

    public static final class Row {
        public final String label;
        public final Kind kind;
        public final Runnable action;
        public final BooleanSupplier state;
        /** 0 to 1, how lit the row is. Eased toward 1 while the pointer is on it. */
        public float glow;

        /** The knobs a right-click opens. Empty for a module that has none. */
        public final List<Setting> settings = new ArrayList<>();
        /** Whether those knobs are showing. */
        public boolean expanded;
        /** 0 shut, 1 open, eased, so the settings slide out rather than appear. */
        public float openness;

        public Row(String label, Kind kind, Runnable action, BooleanSupplier state) {
            this.label = label;
            this.kind = kind;
            this.action = action;
            this.state = state;
        }

        public boolean on() {
            return this.state != null && this.state.getAsBoolean();
        }

        /** Adds a knob and hands the row back, so a module reads as one statement. */
        public Row with(Setting setting) {
            this.settings.add(setting);
            return this;
        }

        public boolean hasSettings() {
            return !this.settings.isEmpty();
        }

        /**
         * How tall this row is including whatever settings are showing.
         *
         * <p>Driven by openness rather than by expanded, so the rows below slide down with
         * the panel opening instead of jumping the moment it is clicked.
         */
        public int height() {
            return ROW_HEIGHT + Math.round(this.settings.size() * SETTING_HEIGHT
                    * this.openness);
        }
    }

    /** How tall one knob is, under the row it belongs to. */
    public static final int SETTING_HEIGHT = 12;

    public static final class Panel {
        public final String id;
        public final String title;
        public final List<Row> rows = new ArrayList<>();
        public int x;
        public int y;
        public boolean open = true;
        /** 0 shut, 1 open. Eased, so the panel slides rather than snapping. */
        public float openness = 1f;
        public float glow;

        /** Where it was built, so a layout can be put back. */
        public final int homeX;
        public final int homeY;

        public Panel(String id, String title, int x, int y) {
            this.id = id;
            this.title = title;
            this.x = x;
            this.y = y;
            this.homeX = x;
            this.homeY = y;
        }

        public Panel add(String label, Kind kind, Runnable action, BooleanSupplier state) {
            this.rows.add(new Row(label, kind, action, state));
            return this;
        }

        /**
         * How tall it is right now, part way through opening and any open settings
         * included.
         *
         * <p>Summed from the rows rather than counted, because a row with its settings
         * showing is taller than one without. Counting them is what makes the panel end
         * halfway down its own contents the moment anything expands.
         */
        public int height() {
            int rows = 0;
            for (Row row : this.rows) rows += row.height();
            return TITLE_HEIGHT + Math.round(rows * this.openness);
        }

        /** The y a row starts at, relative to the top of the panel's body. */
        public int rowTop(int index) {
            int y = 0;
            for (int i = 0; i < index && i < this.rows.size(); i++) {
                y += this.rows.get(i).height();
            }
            return y;
        }

        public boolean shut() {
            return this.openness <= SHUT;
        }
    }

    private final List<Panel> panels = new ArrayList<>();
    private Panel dragging;
    private int grabX;
    private int grabY;
    private double fromX;
    private double fromY;
    /** Whether the pointer has actually moved since the title was grabbed. */
    private boolean moved;

    /** Under this many pixels, a press and release on a title is a click, not a drag. */
    public static final int SLOP = 3;

    public List<Panel> panels() {
        return this.panels;
    }

    public Panel add(Panel panel) {
        this.panels.add(panel);
        return panel;
    }

    public Panel byId(String id) {
        for (Panel panel : this.panels) {
            if (panel.id.equals(id)) return panel;
        }
        return null;
    }

    // ---------------------------------------------------------------- what is where

    public static boolean inTitle(Panel panel, double mouseX, double mouseY) {
        return mouseX >= panel.x && mouseX < panel.x + PANEL_WIDTH
                && mouseY >= panel.y && mouseY < panel.y + TITLE_HEIGHT;
    }

    /**
     * Which row the pointer is over, or -1.
     *
     * <p>A row part way through sliding out does not take clicks: half a row is not a
     * target, and a click that lands on whatever the animation happens to be showing is
     * the kind of thing that fires the wrong toggle once in twenty.
     */
    public static int rowAt(Panel panel, double mouseX, double mouseY) {
        if (panel.shut()) return -1;
        if (mouseX < panel.x || mouseX >= panel.x + PANEL_WIDTH) return -1;

        double top = panel.y + TITLE_HEIGHT;
        double bottom = panel.y + panel.height();
        if (mouseY < top || mouseY >= bottom) return -1;

        // Walked rather than divided: the rows are no longer all the same height, so a
        // division lands on the wrong one as soon as anything above it is expanded.
        double walk = top;
        for (int i = 0; i < panel.rows.size(); i++) {
            double next = walk + panel.rows.get(i).height();
            if (mouseY < next) return i;
            walk = next;
        }
        return -1;
    }

    /**
     * Which of a row's settings the pointer is on, or -1 for the row's own line.
     *
     * <p>The row's own line comes first and is not a setting, so a click on the label
     * still toggles the module rather than the first knob under it.
     */
    public static int settingAt(Panel panel, int rowIndex, double mouseY) {
        if (rowIndex < 0 || rowIndex >= panel.rows.size()) return -1;

        Row row = panel.rows.get(rowIndex);
        if (!row.hasSettings() || row.openness <= SHUT) return -1;

        double top = panel.y + TITLE_HEIGHT + panel.rowTop(rowIndex) + ROW_HEIGHT;
        int index = (int) Math.floor((mouseY - top) / SETTING_HEIGHT);
        return index >= 0 && index < row.settings.size() ? index : -1;
    }

    /**
     * Opens or shuts a row's settings.
     *
     * @return whether there were any to open, so a right-click on a module with none can
     *     say so rather than appearing to do nothing
     */
    public static boolean toggleSettings(Row row) {
        if (row == null || !row.hasSettings()) return false;

        row.expanded = !row.expanded;
        return true;
    }

    /** The topmost panel under the pointer, so overlapping ones do not both answer. */
    public Panel topmostAt(double mouseX, double mouseY) {
        for (int i = this.panels.size() - 1; i >= 0; i--) {
            Panel panel = this.panels.get(i);
            if (mouseX >= panel.x && mouseX < panel.x + PANEL_WIDTH
                    && mouseY >= panel.y && mouseY < panel.y + panel.height()) {
                return panel;
            }
        }
        return null;
    }

    /** Brings a panel to the front, so a dragged one is not left under another. */
    public void raise(Panel panel) {
        if (this.panels.remove(panel)) this.panels.add(panel);
    }

    // --------------------------------------------------------------------- dragging

    public void beginDrag(Panel panel, double mouseX, double mouseY) {
        this.dragging = panel;
        // The offset within the title bar, so the panel does not jump to put its corner
        // under the pointer the moment it is grabbed.
        this.grabX = (int) Math.round(mouseX - panel.x);
        this.grabY = (int) Math.round(mouseY - panel.y);
        // Where the pointer started, so a drag is measured from the grab rather than from
        // the last frame. See dragTo.
        this.fromX = mouseX;
        this.fromY = mouseY;
        this.moved = false;
        raise(panel);
    }

    public boolean isDragging() {
        return this.dragging != null;
    }

    public Panel dragged() {
        return this.dragging;
    }

    public void dragTo(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (this.dragging == null) return;

        int x = (int) Math.round(mouseX) - this.grabX;
        int y = (int) Math.round(mouseY) - this.grabY;

        // Measured from where the pointer was grabbed, not from where the panel was last
        // frame. Against last frame this only ever noticed a jump of SLOP within a single
        // frame, so dragging steadily -- a pixel or two per frame, which is what dragging
        // across a screen actually looks like -- never counted as movement at all, and the
        // panel folded itself away on release as though you had tapped it.
        //
        // The pointer rather than the panel, too: at a screen edge the panel is clamped and
        // stops moving while the pointer keeps going, and that is still a drag.
        if (Math.abs(mouseX - this.fromX) >= SLOP || Math.abs(mouseY - this.fromY) >= SLOP) {
            this.moved = true;
        }

        this.dragging.x = x;
        this.dragging.y = y;
        clamp(this.dragging, screenWidth, screenHeight);
    }

    /** How close a panel has to come to an edge before it snaps to it. */
    public static final int SNAP = 8;

    /**
     * The nearest thing worth lining up with, or the value unchanged.
     *
     * <p>Snapping is the difference between a menu you arrange and one you fiddle with.
     * Nearest rather than first, because two candidates a pixel apart would otherwise be
     * decided by the order they happen to be checked in.
     */
    public static int snapTo(int value, int[] candidates) {
        int best = value;
        int bestGap = SNAP + 1;

        for (int candidate : candidates) {
            int gap = Math.abs(candidate - value);
            if (gap <= SNAP && gap < bestGap) {
                bestGap = gap;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Where a dragged panel should land: the screen edges, and the edges of every other
     * panel, so a column of them lines up without being nudged into place.
     */
    public void snap(Panel panel, int screenWidth, int screenHeight) {
        List<Integer> xs = new ArrayList<>();
        List<Integer> ys = new ArrayList<>();

        xs.add(0);
        xs.add(screenWidth - PANEL_WIDTH);
        xs.add((screenWidth - PANEL_WIDTH) / 2);
        ys.add(0);
        ys.add(screenHeight - panel.height());

        for (Panel other : this.panels) {
            if (other == panel) continue;
            xs.add(other.x);
            xs.add(other.x + PANEL_WIDTH);
            xs.add(other.x - PANEL_WIDTH);
            ys.add(other.y);
            ys.add(other.y + other.height());
            ys.add(other.y - panel.height());
        }

        panel.x = snapTo(panel.x, toArray(xs));
        panel.y = snapTo(panel.y, toArray(ys));
    }

    private static int[] toArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }

    /** Puts every panel back where it was built, for a layout that has got away from you. */
    public void reset() {
        for (Panel panel : this.panels) {
            panel.x = panel.homeX;
            panel.y = panel.homeY;
            panel.open = true;
        }
    }

    /**
     * Lets go.
     *
     * @return the panel that was pressed and released without moving, which is a click on
     *         its title rather than a drag, or null if it was a drag. Told apart by a few
     *         pixels of slop, because a hand never holds still and a title that folded
     *         away every time you nudged it would be unusable.
     */
    public Panel endDrag() {
        Panel was = this.dragging;
        boolean tapped = was != null && !this.moved;
        this.dragging = null;
        this.moved = false;
        return tapped ? was : null;
    }

    /**
     * Keeps a panel where it can be got at.
     *
     * <p>Its title bar has to stay on screen, or a panel dragged off the edge is a panel
     * that cannot be dragged back. The body is allowed to hang off the bottom, since the
     * handle is what you need.
     */
    public static void clamp(Panel panel, int screenWidth, int screenHeight) {
        panel.x = clampX(panel.x, PANEL_WIDTH, screenWidth);
        panel.y = clampY(panel.y, TITLE_HEIGHT, screenHeight);
    }

    /**
     * Keeps a box on screen, given how much of it has to stay visible.
     *
     * <p>Shared with the HUD, so a panel and a HUD element cannot end up following
     * different rules about what "off the edge" means. A negative screen -- a window
     * mid-resize reports one -- gives zero rather than a negative position.
     */
    public static int clampX(int x, int keepVisible, int screenWidth) {
        return Math.max(0, Math.min(x, Math.max(0, screenWidth - keepVisible)));
    }

    public static int clampY(int y, int keepVisible, int screenHeight) {
        return Math.max(0, Math.min(y, Math.max(0, screenHeight - keepVisible)));
    }

    public void clampAll(int screenWidth, int screenHeight) {
        for (Panel panel : this.panels) clamp(panel, screenWidth, screenHeight);
    }

    // -------------------------------------------------------------------- animation

    /**
     * Moves a value toward a target, at a rate that does not depend on the frame rate.
     *
     * <p>The naive {@code value += (target - value) * 0.2f} is twice as fast at 120fps as
     * at 60, so the same menu feels different on two machines. This is the same curve
     * measured in seconds instead of frames.
     */
    public static float ease(float value, float target, float perSecond, float seconds) {
        if (seconds <= 0f) return value;
        float step = 1f - (float) Math.exp(-perSecond * seconds);
        float moved = value + (target - value) * step;
        // Land exactly, so a panel does not sit at 0.999 open forever.
        return Math.abs(target - moved) < 0.001f ? target : moved;
    }

    /** Advances every animation by however long the last frame took. */
    public void tick(float seconds, double mouseX, double mouseY) {
        Panel hovered = topmostAt(mouseX, mouseY);

        for (Panel panel : this.panels) {
            panel.openness = ease(panel.openness, panel.open ? 1f : 0f, 14f, seconds);

            boolean onTitle = panel == hovered && inTitle(panel, mouseX, mouseY);
            panel.glow = ease(panel.glow, onTitle ? 1f : 0f, 18f, seconds);

            int over = panel == hovered ? rowAt(panel, mouseX, mouseY) : -1;
            for (int i = 0; i < panel.rows.size(); i++) {
                Row row = panel.rows.get(i);
                row.glow = ease(row.glow, i == over ? 1f : 0f, 18f, seconds);
                row.openness = ease(row.openness, row.expanded ? 1f : 0f, 16f, seconds);
            }
        }
    }

    /** Blends two 0xAARRGGBB colours, channel by channel. */
    public static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int a = (from >>> shift) & 0xFF;
            int b = (to >>> shift) & 0xFF;
            out |= (Math.round(a + (b - a) * t) & 0xFF) << shift;
        }
        return out;
    }

    /** A colour with its alpha scaled, for fading the whole menu in. */
    public static int fade(int colour, float amount) {
        int alpha = Math.round(((colour >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, amount)));
        return (alpha << 24) | (colour & 0xFFFFFF);
    }
}
