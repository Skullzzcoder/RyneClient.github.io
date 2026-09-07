package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings as the browser sees them: a flat list of knobs with addresses, and the
 * parsing of a request to change one.
 *
 * <p>No Minecraft in here, and no game state either. The dashboard already works this way
 * and it is not a style choice: the HTTP server runs on its own threads, and reading or
 * writing the game from one of those is a race with the client tick. So the tick describes
 * the settings into these plain objects and publishes them, and a request from the browser
 * becomes a {@link Change} that the next tick applies. Nothing here touches the game; it
 * is the paperwork either side of the thread boundary, which is exactly the part worth
 * being able to run under test.
 *
 * <p>An address is {@code panel/row/knob}. Not an index into a flat list: the modules are
 * built fresh each time the menu is opened, and a list position quietly means something
 * different the moment one is added. A page left open in a browser tab overnight and
 * clicked in the morning must either do what it says or nothing at all.
 */
public final class WebSettings {

    /** One knob, described rather than referenced. */
    public static final class Knob {
        public final String panel;
        public final String row;
        public final int index;
        public final String label;
        /** "switch", "slider" or "mode" -- what the page draws. */
        public final String shape;
        public final double value;
        public final double least;
        public final double most;
        public final double step;
        public final List<String> options;
        /** What it reads as, so the page does not have to reimplement the formatting. */
        public final String shown;

        public Knob(String panel, String row, int index, String label, String shape,
                    double value, double least, double most, double step,
                    List<String> options, String shown) {
            this.panel = panel;
            this.row = row;
            this.index = index;
            this.label = label;
            this.shape = shape;
            this.value = value;
            this.least = least;
            this.most = most;
            this.step = step;
            this.options = options == null ? List.of() : List.copyOf(options);
            this.shown = shown;
        }

        public String address() {
            return this.panel + "/" + this.row + "/" + this.index;
        }
    }

    /** One module: whether it is on, and whatever knobs hang off it. */
    public static final class Module {
        public final String panel;
        public final String row;
        public final String label;
        public final boolean toggle;
        public final boolean on;
        public final List<Knob> knobs = new ArrayList<>();

        public Module(String panel, String row, String label, boolean toggle, boolean on) {
            this.panel = panel;
            this.row = row;
            this.label = label;
            this.toggle = toggle;
            this.on = on;
        }

        public String address() {
            return this.panel + "/" + this.row;
        }
    }

    /** What a browser asked for, once it has been read and found to make sense. */
    public static final class Change {
        public final String panel;
        public final String row;
        /** -1 means the module itself rather than one of its knobs. */
        public final int index;
        public final double value;

        Change(String panel, String row, int index, double value) {
            this.panel = panel;
            this.row = row;
            this.index = index;
            this.value = value;
        }

        public boolean isModule() {
            return this.index < 0;
        }

        @Override
        public String toString() {
            return this.panel + "/" + this.row + (isModule() ? "" : "/" + this.index)
                    + " = " + this.value;
        }
    }

    private WebSettings() {
    }

    // ------------------------------------------------------------------------ reading

    /**
     * One field out of a query string, undone from its URL encoding.
     *
     * <p>Written out rather than reached for, because URLDecoder turns a bare "+" into a
     * space, and a module can be called "45/45/10".
     */
    public static String field(String query, String name) {
        if (query == null || name == null) return "";

        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals < 0) continue;
            if (!part.substring(0, equals).equals(name)) continue;
            return unescape(part.substring(equals + 1));
        }
        return "";
    }

    /** Percent-decoding, and nothing else -- a plus stays a plus. */
    public static String unescape(String text) {
        if (text == null || text.indexOf('%') < 0) return text == null ? "" : text;

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            if (letter == '%' && i + 2 < text.length()) {
                try {
                    out.append((char) Integer.parseInt(text.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException notHex) {
                    // Left as written: a stray percent in a name is likelier than an
                    // encoding this does not understand.
                }
            }
            out.append(letter);
        }
        return out.toString();
    }

    /**
     * Reads a request to change something, or gives back nothing.
     *
     * <p>Nothing is trusted: a request naming a module that does not exist, or a knob past
     * the end of one, or a value that is not a number, produces null rather than a guess.
     * This is reached over a socket, and the difference between "did nothing" and "did
     * something to the wrong setting" is the whole reason it is checked here.
     *
     * @param known the modules as last published, which is what the browser was looking at
     */
    public static Change read(String query, List<Module> known) {
        String panel = field(query, "p");
        String row = field(query, "r");
        // "i", not "k": the key is "k", and a knob index sharing the name would
        // have been read as one and the other read as the other.
        String rawIndex = field(query, "i");
        String rawValue = field(query, "v");

        if (panel.isEmpty() || row.isEmpty() || rawValue.isEmpty()) return null;

        Module module = null;
        for (Module each : known) {
            if (each.panel.equals(panel) && each.row.equals(row)) {
                module = each;
                break;
            }
        }
        if (module == null) return null;

        int index = -1;
        if (!rawIndex.isEmpty()) {
            try {
                index = Integer.parseInt(rawIndex);
            } catch (NumberFormatException notANumber) {
                return null;
            }
            if (index < 0 || index >= module.knobs.size()) return null;
        }

        double value;
        try {
            value = Double.parseDouble(rawValue);
        } catch (NumberFormatException notANumber) {
            return null;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) return null;

        // Held to the knob's own bounds here as well as where it is applied. The bounds
        // came from the same snapshot the browser was shown, so a page that has gone stale
        // cannot push a slider past where it was told the end was.
        if (index >= 0) {
            Knob knob = module.knobs.get(index);
            value = Math.max(knob.least, Math.min(knob.most, value));
        } else {
            value = value >= 0.5 ? 1 : 0;
        }
        return new Change(panel, row, index, value);
    }

    // ------------------------------------------------------------------------ writing

    /** The modules as JSON, for the page to draw itself from. */
    public static String toJson(List<Module> modules) {
        StringBuilder json = new StringBuilder("[");
        for (int m = 0; m < modules.size(); m++) {
            Module module = modules.get(m);
            if (m > 0) json.append(',');
            json.append("{\"panel\":\"").append(escape(module.panel))
                    .append("\",\"row\":\"").append(escape(module.row))
                    .append("\",\"label\":\"").append(escape(module.label))
                    .append("\",\"toggle\":").append(module.toggle)
                    .append(",\"on\":").append(module.on)
                    .append(",\"knobs\":[");
            for (int k = 0; k < module.knobs.size(); k++) {
                Knob knob = module.knobs.get(k);
                if (k > 0) json.append(',');
                json.append("{\"index\":").append(knob.index)
                        .append(",\"label\":\"").append(escape(knob.label))
                        .append("\",\"shape\":\"").append(escape(knob.shape))
                        .append("\",\"value\":").append(number(knob.value))
                        .append(",\"least\":").append(number(knob.least))
                        .append(",\"most\":").append(number(knob.most))
                        .append(",\"step\":").append(number(knob.step))
                        .append(",\"shown\":\"").append(escape(knob.shown))
                        .append("\",\"options\":[");
                for (int o = 0; o < knob.options.size(); o++) {
                    if (o > 0) json.append(',');
                    json.append('"').append(escape(knob.options.get(o))).append('"');
                }
                json.append("]}");
            }
            json.append("]}");
        }
        return json.append(']').toString();
    }

    /**
     * A number JSON will accept.
     *
     * <p>Java prints whole doubles as "3.0", which is valid JSON, and NaN or Infinity as
     * words that are not. Those cannot arise from a clamped knob, but a value read from a
     * game field can be anything at all, and one bad number makes the whole page fail to
     * parse rather than one setting look wrong.
     */
    public static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "0";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** Enough escaping for a JSON string, including the control characters. */
    public static String escape(String text) {
        if (text == null) return "";

        StringBuilder out = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char letter = text.charAt(i);
            switch (letter) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    // A raw control character is not legal in a JSON string, and one in a
                    // rig name would break the page rather than show oddly.
                    if (letter < 0x20) out.append(String.format("\\u%04x", (int) letter));
                    else out.append(letter);
            }
        }
        return out.toString();
    }
}
