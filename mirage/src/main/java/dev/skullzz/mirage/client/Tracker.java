package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What you have won and lost, worked out from chat.
 *
 * <p>Accounting, not advantage: every line this reads is one already on your screen. It
 * adds up what came in and what went out, and says whether you are up.
 *
 * <p>No Minecraft in here at all, deliberately. Money parsing is the part that goes wrong
 * quietly -- "$1.5M" and "$1,500" and "$1.5" are three different numbers and two of them
 * look alike -- so it is kept where it can be run against real lines on a machine with no
 * game on it. The chat event is somebody else's problem, in {@link ChatHook}.
 *
 * <p>Amounts are held in cents. A balance parsed as dollars loses half of "$1.50" and
 * rounds every rakeback share, and being out by a cent a trade is how a tally stops
 * matching the one in your head.
 */
public final class Tracker {

    /** One payment, in or out. */
    public static final class Payment {
        public final String player;
        public final long cents;
        public final boolean incoming;
        public final long at;

        public Payment(String player, long cents, boolean incoming, long at) {
            this.player = player;
            this.cents = cents;
            this.incoming = incoming;
            this.at = at;
        }

        /** Signed: what this did to your balance. */
        public long signed() {
            return this.incoming ? this.cents : -this.cents;
        }

        @Override
        public String toString() {
            return (this.incoming ? "+" : "-") + money(this.cents) + " " + this.player;
        }
    }

    /**
     * The lines a payment can arrive as.
     *
     * <p>Two shapes, because that is what the server sends, and both anchored to the start
     * of the line. Chat is written by other people: without the anchor, anybody typing
     * "you paid Bob $10000000" in public chat would land in your tally, and a tally that
     * can be written to by strangers is worse than no tally. A leading [tag] the server
     * adds is dropped first, since that is a prefix and not a sentence.
     */
    /**
     * The wordings a payment can arrive in.
     *
     * <p>Defaults here, editable in the config. These are a guess about how one server
     * words things, and a guess that is wrong makes the tracker count nothing and say
     * nothing -- which looks exactly like a quiet night. Correcting them without a rebuild
     * is the difference between a five-second fix and waiting for a new jar.
     *
     * <p>Each needs three groups: the player, the number and the scale letter, in whatever
     * order the wording puts them. All are anchored to the start of the line, because chat
     * is written by other people and without the anchor anyone typing "you paid Bob
     * $10000000" lands in your tally.
     */
    public static final List<String> DEFAULT_IN = List.of(
            "^([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?paid\\s+you\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)",
            "^([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?sent\\s+you\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)",
            "^you\\s+(?:have\\s+)?received\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)\\s+from\\s+([A-Za-z0-9_]{3,16})");

    public static final List<String> DEFAULT_OUT = List.of(
            "^you\\s+(?:have\\s+)?paid\\s+([A-Za-z0-9_]{3,16})\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)",
            "^you\\s+(?:have\\s+)?sent\\s+([A-Za-z0-9_]{3,16})\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)",
            "^you\\s+(?:have\\s+)?sent\\s+\\p{Sc}?\\s*([0-9][0-9,.]*)\\s*([kmbtKMBT]?)\\s+to\\s+([A-Za-z0-9_]{3,16})");

    private static String lastBad = "";

    public static String lastBad() {
        return lastBad;
    }

    static List<Pattern> compile(List<String> sources) {
        List<Pattern> made = new ArrayList<>();
        for (String source : sources) {
            try {
                made.add(Pattern.compile(source, Pattern.CASE_INSENSITIVE));
            } catch (RuntimeException bad) {
                // A pattern that will not compile is left out rather than taking the rest
                // with it: one bad line in a config should cost one wording, not all.
                lastBad = source + " -> " + bad.getMessage();
            }
        }
        return made;
    }

    /**
     * Whether a wording names the player before the number.
     *
     * <p>"Bob paid you $10" and "You received $10 from Bob" carry the same three things in
     * a different order, and a reader that assumed one order would read the amount as the
     * name. Decided from the pattern itself rather than a flag somebody has to remember.
     */
    static List<Boolean> playerFirst(List<String> sources) {
        List<Boolean> order = new ArrayList<>();
        for (String source : sources) {
            int name = source.indexOf("A-Za-z0-9_");
            int number = source.indexOf("[0-9][0-9,.]");
            order.add(name >= 0 && (number < 0 || name < number));
        }
        return order;
    }

    private static List<Pattern> in = compile(DEFAULT_IN);
    private static List<Pattern> out = compile(DEFAULT_OUT);
    private static List<Boolean> inPlayerFirst = playerFirst(DEFAULT_IN);
    private static List<Boolean> outPlayerFirst = playerFirst(DEFAULT_OUT);

    /** Replaces the wordings, falling back to the defaults for an empty list. */
    public static void setPatterns(List<String> incoming, List<String> outgoing) {
        List<String> useIn = incoming == null || incoming.isEmpty() ? DEFAULT_IN : incoming;
        List<String> useOut = outgoing == null || outgoing.isEmpty() ? DEFAULT_OUT : outgoing;

        in = compile(useIn);
        out = compile(useOut);
        inPlayerFirst = playerFirst(useIn);
        outPlayerFirst = playerFirst(useOut);
    }

    public static int wordings() {
        return in.size() + out.size();
    }

    /** A leading [tag] or (tag) the server puts in front, and nothing else. */
    private static final Pattern LEADING_TAG = Pattern.compile("^(?:\\[[^\\]]{0,24}\\]|\\([^)]{0,24}\\))\\s*");

    private Tracker() {
    }

    /**
     * Reads one chat line, or gives back nothing.
     *
     * <p>"You paid" is tried first. A line containing both would otherwise be read as
     * money coming in when it went out, and being wrong about the direction is worse than
     * missing the line entirely.
     */
    public static Payment read(String line, long at) {
        if (line == null || line.isEmpty()) return null;
        String clean = strip(line);

        Payment going = match(clean, out, outPlayerFirst, false, at);
        if (going != null) return going;
        return match(clean, in, inPlayerFirst, true, at);
    }

    /** The first of these wordings that fits, read the way its own order says. */
    private static Payment match(String line, List<Pattern> patterns, List<Boolean> order,
                                 boolean incoming, long at) {
        for (int i = 0; i < patterns.size(); i++) {
            Matcher found = patterns.get(i).matcher(line);
            if (!found.find() || found.groupCount() < 3) continue;

            boolean nameFirst = i >= order.size() || order.get(i);
            String player = found.group(nameFirst ? 1 : 3);
            Long cents = amount(found.group(nameFirst ? 2 : 1),
                    found.group(nameFirst ? 3 : 2));
            if (cents != null) return new Payment(player, cents, incoming, at);
        }
        return null;
    }

    /**
     * Colour codes off, exotic characters flattened, and a leading server tag off.
     *
     * <p>Only the outermost tag, and only from the front: stripping anywhere would let
     * "[x] you paid" be smuggled into the middle of somebody's chat message.
     */
    static String strip(String line) {
        String clean = flatten(line.replaceAll("\u00a7.", ""));
        Matcher tag = LEADING_TAG.matcher(clean);
        return tag.find() ? clean.substring(tag.end()).trim() : clean;
    }

    /**
     * Every kind of space a server can write, turned into the one kind a pattern matches.
     *
     * <p>A chat line does not arrive as the plain text it looks like on screen. Servers
     * running a custom font put their currency glyph in the private use area, and they
     * separate words with no-break and zero-width spaces. Java's {@code \s} matches none
     * of that -- not U+00A0, not U+200B -- so "You paid Bob $ 1" written with any of them
     * failed every wording while looking, on screen and in a log file, exactly like the
     * line that works. Guessing the next such character one at a time is a losing game,
     * so this flattens the whole class of them instead:
     *
     * <ul>
     *   <li>anything Unicode calls a space becomes a single ordinary space,
     *   <li>zero-width joiners, bidi marks, stray controls and custom-font glyphs are
     *       dropped -- they carry no meaning a payment line depends on,
     *   <li>runs of space collapse, so a dropped glyph does not leave a double gap.
     * </ul>
     *
     * <p>Nothing here can add a word, so an anchored wording stays anchored: a line that
     * did not start with "you paid" still does not.
     */
    static String flatten(String text) {
        StringBuilder out = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            if (Character.isWhitespace(letter) || Character.isSpaceChar(letter)) {
                // Held rather than written, so trailing space never reaches the result.
                pendingSpace = out.length() > 0;
                continue;
            }
            int kind = Character.getType(letter);
            // Invisible either way, and never the thing that tells a server message apart
            // from a player's: safe to drop wherever they turn up.
            if (kind == Character.FORMAT || kind == Character.CONTROL) continue;
            // A custom-font glyph is visible, so at the front of a line it is a prefix --
            // a rank badge, a channel marker -- and dropping it would hand the "^you paid"
            // anchor to anyone who can type one. Kept there, where it defeats the anchor
            // exactly as an unrecognised prefix should; dropped once real text has begun,
            // which is where the currency glyph sits.
            if (kind == Character.PRIVATE_USE || kind == Character.UNASSIGNED) {
                if (out.length() == 0) {
                    out.append(letter);
                    pendingSpace = false;
                }
                continue;
            }
            if (pendingSpace) out.append(' ');
            pendingSpace = false;
            out.append(letter);
        }
        return out.toString();
    }

    /**
     * "$1.5M", "1,500", "2b" -- in cents.
     *
     * <p>Returns nothing rather than guessing when the text is not a number this
     * understands. A tally that silently counts a misread line is worse than one with a
     * gap in it, because only one of them is visible.
     */
    static Long amount(String digits, String suffix) {
        String text = digits.replace(",", "");
        // Anything else malformed -- two decimal points, a stray character -- is left to
        // BigDecimal below, which refuses it. A second guard here only reads as though it
        // were doing something.
        if (text.isEmpty()) return null;

        long multiplier = switch (suffix == null ? "" : suffix.toLowerCase(Locale.ROOT)) {
            case "k" -> 1_000L;
            case "m" -> 1_000_000L;
            case "b" -> 1_000_000_000L;
            case "t" -> 1_000_000_000_000L;
            default -> 1L;
        };

        try {
            java.math.BigDecimal value = new java.math.BigDecimal(text)
                    .multiply(java.math.BigDecimal.valueOf(multiplier))
                    .multiply(java.math.BigDecimal.valueOf(100));
            // Half-up, and only ever at the cent: a fraction of a cent cannot be paid.
            java.math.BigDecimal cents = value.setScale(0, java.math.RoundingMode.HALF_UP);
            if (cents.compareTo(java.math.BigDecimal.valueOf(Long.MAX_VALUE)) > 0) return null;
            long result = cents.longValueExact();
            return result <= 0 ? null : result;
        } catch (ArithmeticException | NumberFormatException notANumber) {
            return null;
        }
    }

    /**
     * Runs the parser over lines whose answers are known, and says what it got wrong.
     *
     * <p>Not a substitute for the test harness. This answers a different question, from
     * inside the game: is the jar that is loaded the one you think it is? A wording fix
     * that was never rebuilt looks exactly like a wording fix that did not work, and
     * three rounds of this went by without a way to tell those apart.
     *
     * <p>It runs against the wordings actually in force, so it also catches a bad edit to
     * paymentIn / paymentOut in the config.
     *
     * @return "OK", or the first thing this build gets wrong
     */
    public static String selfTest() {
        if (!is(read("You paid Notch $ 1", 0L), false, "Notch", 100))
            return "FAILED on a space after the $ -- this build is older than that fix";
        if (!is(read("You paid Notch $\u00a01", 0L), false, "Notch", 100))
            return "FAILED on a no-break space -- this build is older than that fix";
        if (!is(read("You paid Notch \ue000 1", 0L), false, "Notch", 100))
            return "FAILED on a custom-font glyph -- this build is older than that fix";
        if (!is(read("Notch paid you $1,500", 0L), true, "Notch", 150000))
            return "FAILED on money coming in";
        // The one that matters most: chat is written by other people.
        if (read("<Griefer> you paid Bob $999", 0L) != null)
            return "FAILED -- somebody else's chat message counts as your money";
        return "OK";
    }

    private static boolean is(Payment payment, boolean incoming, String player, long cents) {
        return payment != null && payment.incoming == incoming
                && player.equals(payment.player) && payment.cents == cents;
    }

    /** Cents back into something to read. */
    public static String money(long cents) {
        long whole = Math.abs(cents) / 100;
        long part = Math.abs(cents) % 100;
        String sign = cents < 0 ? "-" : "";
        String body = String.format("%,d", whole);
        return sign + "$" + body + (part == 0 ? "" : String.format(".%02d", part));
    }

    // ------------------------------------------------------------------- a session

    /** One sitting: everything since you pressed start. */
    public static final class Session {
        public final long started;
        public long ended;
        public final List<Payment> payments = new ArrayList<>();

        public Session(long started) {
            this.started = started;
        }

        public long in() {
            long total = 0;
            for (Payment payment : this.payments) if (payment.incoming) total += payment.cents;
            return total;
        }

        public long out() {
            long total = 0;
            for (Payment payment : this.payments) if (!payment.incoming) total += payment.cents;
            return total;
        }

        public long net() {
            return in() - out();
        }

        public int wins() {
            int count = 0;
            for (Payment payment : this.payments) if (payment.incoming) count++;
            return count;
        }

        public int losses() {
            return this.payments.size() - wins();
        }

        /**
         * How many payments out in a row, right now.
         *
         * <p>The number the alert watches. Counted from the end, because what matters is
         * what is happening rather than what happened.
         */
        public int lossStreak() {
            int run = 0;
            for (int i = this.payments.size() - 1; i >= 0; i--) {
                if (this.payments.get(i).incoming) break;
                run++;
            }
            return run;
        }

        public int winStreak() {
            int run = 0;
            for (int i = this.payments.size() - 1; i >= 0; i--) {
                if (!this.payments.get(i).incoming) break;
                run++;
            }
            return run;
        }

        /** The longest run either way this session, for the record rather than the alert. */
        public int worstLossRun() {
            return longestRun(false);
        }

        public int bestWinRun() {
            return longestRun(true);
        }

        private int longestRun(boolean incoming) {
            int best = 0;
            int run = 0;
            for (Payment payment : this.payments) {
                if (payment.incoming == incoming) {
                    run++;
                    best = Math.max(best, run);
                } else {
                    run = 0;
                }
            }
            return best;
        }

        /**
         * What each player has sent you, and what a rake of this many basis points owes
         * them back.
         *
         * <p>Basis points rather than a percentage, so 12.5% is a whole number here and
         * not a rounding decision taken twice.
         */
        public Map<String, long[]> rakeback(int basisPoints) {
            Map<String, long[]> owed = new LinkedHashMap<>();
            for (Payment payment : this.payments) {
                if (!payment.incoming) continue;
                long[] row = owed.computeIfAbsent(payment.player, key -> new long[2]);
                row[0] += payment.cents;
            }
            for (long[] row : owed.values()) {
                row[1] = Math.round(row[0] * (basisPoints / 10000.0));
            }
            return owed;
        }
    }
}
