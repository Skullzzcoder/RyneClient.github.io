package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The rules of the three called games, and how to draw a round that ends the way it was
 * told to.
 *
 * <p>No Minecraft in here, which is the point: every one of these is a small piece of
 * arithmetic that is easy to get subtly wrong and impossible to check by eye once it is
 * behind a dispenser. Here it can be run, and it is -- against every combination of call,
 * named winner and bound there is, checking the one property that matters: <em>a round
 * drawn to a named winner really does resolve to that winner under the game's own rules.</em>
 * A rig that quietly disagrees with its own scoreboard is worse than no rig.
 *
 * <p>Each game is written as two halves that have to agree:
 * <ul>
 *   <li>{@code draw} -- given a call and who should take it, produce the numbers.
 *   <li>{@code winnerOf} -- given the numbers and the call, say who took it.
 * </ul>
 * Neither knows about the other. The test is that they round-trip.
 */
public final class Games {

    /** The three lanes of a race, strongest first, and what each is made of. */
    public static final List<String> LANES = List.of("diamond", "gold", "iron");

    /** How many pieces a lane has to land before it has finished. */
    public static final int RACE_LENGTH = 3;

    /** What a side can call in the two called games. */
    public static final String HIGH = "high";
    public static final String LOW = "low";
    public static final String ODD = "odd";
    public static final String EVEN = "even";

    private Games() {
    }

    /** The horse armour item for a lane, as SelfFakes looks items up. */
    public static String armourFor(String lane) {
        String clean = lane == null ? "" : lane.trim().toLowerCase(Locale.ROOT);
        switch (clean) {
            case "diamond": return "diamond_horse_armor";
            case "gold": case "golden": return "golden_horse_armor";
            // Minecraft has no bronze. Iron is the third tier and the one that looks it.
            case "iron": case "bronze": return "iron_horse_armor";
            default: return "";
        }
    }

    public static boolean isLane(String lane) {
        return !armourFor(lane).isEmpty();
    }

    // ------------------------------------------------------------------------ the race

    /**
     * The order the pieces come out in, for a race the named lane is to win.
     *
     * <p>A race is only interesting if it looks like one. Handing the winner all three of
     * its pieces first and then the losers theirs is a rig anyone can read across the
     * room, so the losers are dealt in among the winner's pieces -- as many as can be
     * without either of them reaching {@link #RACE_LENGTH} before the winner does.
     *
     * @param winner which lane finishes first; empty or unknown leaves it to chance
     * @return one lane name per dispense, in order
     */
    public static List<String> race(String winner, Random random) {
        List<String> lanes = new ArrayList<>(LANES);
        String taking = isLane(winner) ? winner.trim().toLowerCase(Locale.ROOT)
                : lanes.get(random.nextInt(lanes.size()));

        // Every piece that has to come out: three each.
        int[] left = new int[lanes.size()];
        for (int i = 0; i < left.length; i++) left[i] = RACE_LENGTH;

        List<String> order = new ArrayList<>();
        int winnerIndex = lanes.indexOf(taking);
        boolean decided = false;

        while (true) {
            // Once the winner has finished, whatever is left can come out in any order --
            // the race is over and the stragglers are just tidying up.
            if (decided) {
                int next = -1;
                for (int i = 0; i < left.length; i++) if (left[i] > 0) { next = i; break; }
                if (next < 0) return order;
                left[next]--;
                order.add(lanes.get(next));
                continue;
            }

            // Before that, a loser may only take a piece if doing so leaves it short of the
            // finish. That is what keeps the winner first while letting the race look close.
            List<Integer> allowed = new ArrayList<>();
            for (int i = 0; i < left.length; i++) {
                if (left[i] <= 0) continue;
                if (i == winnerIndex) { allowed.add(i); continue; }
                if (left[i] > 1) allowed.add(i);
            }
            if (allowed.isEmpty()) return order;

            int pick = allowed.get(random.nextInt(allowed.size()));
            left[pick]--;
            order.add(lanes.get(pick));
            if (pick == winnerIndex && left[winnerIndex] == 0) decided = true;
        }
    }

    /**
     * Which lane finished first in a given order of pieces.
     *
     * <p>Reads the order back the way a bystander counting armour would, so it can be
     * checked against what the race was told to do.
     */
    public static String raceWinner(List<String> order) {
        if (order == null || order.isEmpty()) return "";

        List<String> lanes = new ArrayList<>(LANES);
        int[] seen = new int[lanes.size()];
        for (String piece : order) {
            int index = lanes.indexOf(piece == null ? ""
                    : piece.trim().toLowerCase(Locale.ROOT));
            if (index < 0) continue;
            if (++seen[index] >= RACE_LENGTH) return lanes.get(index);
        }
        return "";
    }

    // -------------------------------------------------------------------- high or low

    /**
     * A pair of numbers for a called high-low, as {@code {theirs, yours}}.
     *
     * <p>They call whether their number will be above or below yours, and take the round
     * if it is. Ties go to the house, which is the ordinary rule and the reason a draw
     * never has to be re-run.
     *
     * @param call {@link #HIGH} or {@link #LOW}
     * @param theyWin whether the caller is to be right
     */
    public static int[] highLow(String call, boolean theyWin, int most, Random random) {
        int top = Math.max(2, most);
        boolean high = !LOW.equalsIgnoreCase(call == null ? "" : call.trim());

        // Right call and they win, or wrong call and they lose, both come out as: is
        // theirs above yours? Working it out once here is what keeps the two halves of
        // this class from disagreeing about what "low" means.
        boolean theirsAbove = high == theyWin;

        // A tie is a loss for them, so it is only ever available when they are to lose,
        // and then only as one of the ways to lose.
        if (!theyWin && random.nextInt(4) == 0) {
            int same = 1 + random.nextInt(top);
            return new int[] { same, same };
        }

        // Two different numbers, then handed out in whichever order the answer needs.
        int low = 1 + random.nextInt(top - 1);
        int highNumber = low + 1 + random.nextInt(top - low);
        return theirsAbove ? new int[] { highNumber, low } : new int[] { low, highNumber };
    }

    /** Whether the caller took a high-low round, read back from the numbers. */
    public static boolean highLowTheyWin(String call, int theirs, int yours) {
        boolean high = !LOW.equalsIgnoreCase(call == null ? "" : call.trim());
        if (theirs == yours) return false;
        return high ? theirs > yours : theirs < yours;
    }

    // ---------------------------------------------------------------------- odd or even

    /**
     * A number for a called odd-or-even.
     *
     * @param call {@link #ODD} or {@link #EVEN}
     * @param theyWin whether the caller is to be right
     */
    public static int oddEven(String call, boolean theyWin, int most, Random random) {
        int top = Math.max(2, most);
        boolean odd = !EVEN.equalsIgnoreCase(call == null ? "" : call.trim());
        boolean wantOdd = odd == theyWin;

        // How many of 1..top have the parity being asked for. There is always at least
        // one of each once top is two or more, which is why top is floored at two.
        int count = wantOdd ? (top + 1) / 2 : top / 2;
        int nth = random.nextInt(count);
        return wantOdd ? 1 + nth * 2 : 2 + nth * 2;
    }

    /** Whether the caller took an odd-even round, read back from the number. */
    public static boolean oddEvenTheyWin(String call, int number) {
        boolean odd = !EVEN.equalsIgnoreCase(call == null ? "" : call.trim());
        return odd == (Math.floorMod(number, 2) == 1);
    }

    /** The calls a game accepts, for saying so when something else is typed. */
    public static List<String> callsFor(boolean oddEvenGame) {
        return oddEvenGame ? List.of(ODD, EVEN) : List.of(HIGH, LOW);
    }
}
