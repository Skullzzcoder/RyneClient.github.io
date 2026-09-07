package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turning a build that is only being shown into one you can walk up and make real.
 *
 * <p>A fake build is a picture: it is drawn into your copy of the world and stops at the
 * edge of your screen. What it is good for beyond looking at is telling you exactly where
 * to put the real thing -- so this counts what the build needs, what is already standing,
 * and which block to walk to next. Place one, and the count goes down.
 *
 * <p>No Minecraft in here. It is handed a list of spots -- where, what, and whether the
 * real world already has it -- and everything after that is counting and sorting, which is
 * the part worth running under test. Reading the world is one loop, in FakeBlocks, where
 * the world already is.
 *
 * <p>The distinction that matters: a block that is already right is <em>done</em>, not
 * missing. Getting that backwards gives a tally that never reaches the end and a "nearest"
 * that sends you to a spot you already filled -- both of which look like the guide working
 * until you follow it.
 */
public final class BuildGuide {

    /** One position the build wants something at. */
    public static final class Spot {
        public final int x;
        public final int y;
        public final int z;
        /** What goes here, as a plain name -- "obsidian", not a block state. */
        public final String block;
        /** Whether the real world already has it. */
        public final boolean done;

        public Spot(int x, int y, int z, String block, boolean done) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block == null ? "" : block;
            this.done = done;
        }

        /** Distance from a point, squared -- comparing is all this is ever used for. */
        public long away(int fromX, int fromY, int fromZ) {
            long dx = this.x - (long) fromX;
            long dy = this.y - (long) fromY;
            long dz = this.z - (long) fromZ;
            return dx * dx + dy * dy + dz * dz;
        }

        @Override
        public String toString() {
            return this.block + " at " + this.x + " " + this.y + " " + this.z;
        }
    }

    /** How many of one block the build wants, and how many are already standing. */
    public static final class Need {
        public final String block;
        public int wanted;
        public int placed;

        Need(String block) {
            this.block = block;
        }

        public int left() {
            return Math.max(0, this.wanted - this.placed);
        }
    }

    private BuildGuide() {
    }

    /**
     * What the build needs, most still-missing first.
     *
     * <p>Ordered by what is left rather than by what it wants in total, because the
     * question this answers is "what should I go and get", and a material you have already
     * finished placing is not an answer to it.
     */
    public static List<Need> materials(List<Spot> spots) {
        Map<String, Need> tally = new LinkedHashMap<>();
        for (Spot spot : spots) {
            if (spot.block.isEmpty()) continue;

            Need need = tally.computeIfAbsent(spot.block, Need::new);
            need.wanted++;
            if (spot.done) need.placed++;
        }

        List<Need> out = new ArrayList<>(tally.values());
        out.sort((a, b) -> {
            if (a.left() != b.left()) return Integer.compare(b.left(), a.left());
            // A stable tiebreak, so the list does not reshuffle itself between two blocks
            // with the same number left every time it is printed.
            return a.block.compareTo(b.block);
        });
        return out;
    }

    /** How many spots are already standing. */
    public static int placed(List<Spot> spots) {
        int done = 0;
        for (Spot spot : spots) if (spot.done) done++;
        return done;
    }

    /** How far through, 0 to 1. An empty build is finished, not divided by zero. */
    public static double progress(List<Spot> spots) {
        return spots.isEmpty() ? 1 : placed(spots) / (double) spots.size();
    }

    /**
     * The nearest spot still to fill, or null when there is nothing left.
     *
     * <p>Nearest to you, so following it walks the build rather than sending you back and
     * forth across it. Ties are broken by position so two spots the same distance away do
     * not swap every tick and make the marker flicker between them.
     */
    public static Spot nearest(List<Spot> spots, int fromX, int fromY, int fromZ) {
        Spot best = null;
        long bestAway = Long.MAX_VALUE;

        for (Spot spot : spots) {
            if (spot.done) continue;

            long away = spot.away(fromX, fromY, fromZ);
            if (away > bestAway) continue;
            if (away == bestAway && best != null && !before(spot, best)) continue;
            best = spot;
            bestAway = away;
        }
        return best;
    }

    /** A fixed order for two spots, so a tie always resolves the same way. */
    private static boolean before(Spot a, Spot b) {
        if (a.y != b.y) return a.y < b.y;
        if (a.x != b.x) return a.x < b.x;
        return a.z < b.z;
    }

    /** Only what is left, for a guide that shrinks as you build. */
    public static List<Spot> remaining(List<Spot> spots) {
        List<Spot> left = new ArrayList<>();
        for (Spot spot : spots) if (!spot.done) left.add(spot);
        return left;
    }

    /**
     * The whole thing as lines to print.
     *
     * @param spots every position the build wants filled
     * @param fromX where you are, for the nearest one
     */
    public static List<String> lines(String name, List<Spot> spots, int fromX, int fromY,
                                     int fromZ, int mostMaterials) {
        List<String> out = new ArrayList<>();
        int done = placed(spots);

        out.add("--- " + name + " ---");
        if (spots.isEmpty()) {
            out.add("Nothing showing. Stand a build up first.");
            return out;
        }

        out.add(done + " of " + spots.size() + " placed  ("
                + Math.round(progress(spots) * 100) + "%)");

        if (done >= spots.size()) {
            out.add("Finished. Every block is really there.");
            return out;
        }

        for (Need need : materials(spots)) {
            if (need.left() == 0) continue;
            if (out.size() > mostMaterials) {
                out.add("  ...");
                break;
            }
            out.add("  " + pad(need.block, 22) + need.left() + " left  (" + need.placed
                    + "/" + need.wanted + ")");
        }

        Spot next = nearest(spots, fromX, fromY, fromZ);
        if (next != null) {
            out.add("Nearest: " + next.block + " at " + next.x + " " + next.y + " "
                    + next.z);
        }
        return out;
    }

    /** Left-aligned in a column, so the counts line up in a monospaced chat. */
    static String pad(String text, int width) {
        String clean = text == null ? "" : text;
        if (clean.length() >= width) return clean.substring(0, width - 1) + " ";
        return clean + " ".repeat(width - clean.length());
    }

    /** "minecraft:obsidian" is not what anybody calls it. */
    public static String shortName(String id) {
        if (id == null) return "";

        String clean = id.trim().toLowerCase(Locale.ROOT);
        int colon = clean.indexOf(':');
        return colon < 0 ? clean : clean.substring(colon + 1);
    }
}
