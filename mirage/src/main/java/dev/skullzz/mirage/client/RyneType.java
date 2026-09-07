package dev.skullzz.mirage.client;

/**
 * How text is set: widths, letter spacing, and where a string starts so that it lands
 * where you meant it to.
 *
 * <p>No Minecraft in here, for the usual two reasons: it can be run and tested with no
 * game installed, and the numbers below are values rather than an API. The widths are
 * Minecraft's own default font, written down. Asking the game for them means
 * {@code TextRenderer.getWidth}, which this mod has never watched compile -- and a
 * heading two pixels off is a heading, while a guessed method name is a build.
 *
 * <p>The trick this exists for: <em>tracking</em>. Drawing a title one character at a
 * time with a gap between them is what separates a heading from a sentence, and it is the
 * single biggest difference between a menu that looks assembled and one that looks
 * designed. It only works on capitals -- Minecraft's uppercase and digits are a uniform
 * six pixels, so the spacing stays even, while lowercase is ragged (an "i" is three, an
 * "m" is six) and tracking it just looks broken. So headings are set in capitals and
 * body text is left alone, drawn in one call with the font's own kerning.
 */
public final class RyneType {

    /** What almost every capital and digit advances by, including its one-pixel gap. */
    public static final int ADVANCE = 6;

    /** Enough to read as deliberate; more than three and the word stops being a word. */
    public static final int TRACKING = 2;

    private RyneType() {
    }

    /**
     * How wide one character is, gap included.
     *
     * <p>Only the ones that are not six are listed. Being wrong about one of these costs a
     * pixel of alignment, which is why it is safe to write them down rather than ask.
     */
    public static int advance(char letter) {
        switch (letter) {
            // One pixel of ink.
            case 'i': case '!': case '.': case ',': case ':': case ';': case '|':
            case '\'': case '`':
                return 2;
            case 'l':
                return 3;
            case ' ': case 'I': case '[': case ']': case '"': case 't':
                return 4;
            case 'f': case 'k': case '(': case ')': case '{': case '}':
            case '<': case '>': case '*':
                return 5;
            case '@': case '~':
                return 7;
            default:
                return ADVANCE;
        }
    }

    /** How wide a string is with no extra spacing. */
    public static int width(String text) {
        return width(text, 0);
    }

    /** How wide a string is once every gap between characters has been widened. */
    public static int width(String text, int tracking) {
        if (text == null || text.isEmpty()) return 0;

        int total = 0;
        for (int i = 0; i < text.length(); i++) total += advance(text.charAt(i));
        // Between the characters, not after the last one: a trailing gap would push
        // right-aligned text off by exactly one tracking every time.
        return total + Math.max(0, text.length() - 1) * Math.max(0, tracking);
    }

    /** The heading form of a label: capitals, which are the only even-width characters. */
    public static String caps(String text) {
        return text == null ? "" : text.toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Where each character goes, relative to the start of the string.
     *
     * <p>The whole point of drawing a heading one character at a time.
     */
    public static int[] offsets(String text, int tracking) {
        if (text == null || text.isEmpty()) return new int[0];

        int[] out = new int[text.length()];
        int x = 0;
        for (int i = 0; i < text.length(); i++) {
            out[i] = x;
            x += advance(text.charAt(i)) + Math.max(0, tracking);
        }
        return out;
    }

    /** The x to start at so the string ends at {@code right}. */
    public static int rightX(int right, String text, int tracking) {
        return right - width(text, tracking);
    }

    /** The x to start at so the string sits in the middle of a box. */
    public static int centreX(int x, int boxWidth, String text, int tracking) {
        return x + Math.max(0, (boxWidth - width(text, tracking)) / 2);
    }

    /**
     * Cut to fit a column measured in pixels rather than characters.
     *
     * <p>{@link RyneDraw#trim} counts characters, which is close enough for chat and wrong
     * for a column: "WWWW" and "iiii" are the same length and nothing like the same width.
     */
    public static String fit(String text, int pixels, int tracking) {
        if (text == null || text.isEmpty()) return "";
        if (width(text, tracking) <= pixels) return text;

        // Measured on the joined string rather than on the two halves added together.
        // Joining puts one more gap between the text and the dots, and that gap is
        // exactly the overrun the first version of this shipped: every fitted label was
        // one tracking wider than the column it was promised to fit inside.
        String tail = "...";
        for (int kept = text.length() - 1; kept > 0; kept--) {
            String candidate = text.substring(0, kept) + tail;
            if (width(candidate, tracking) <= pixels) return candidate;
        }
        return width(tail, tracking) <= pixels ? tail : "";
    }
}
