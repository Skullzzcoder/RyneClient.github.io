package dev.skullzz.mirage.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

import dev.skullzz.mirage.Mirage;

/**
 * What the mod draws over the game, and where.
 *
 * <p>Four pieces, each switched on separately and each dragged where you want it in the
 * HUD editor. Drawn through Fabric's HUD event, subscribed to by name through
 * {@link Events} rather than imported, because that package is versioned and a guessed
 * name is a build that does not compile.
 *
 * <p>Everything here reports rather than assists: the tracker's own arithmetic, where you
 * are standing, and the bearing to somewhere you marked yourself. None of it tells you
 * anything the game was not already showing you.
 */
public final class Hud {

    private static final String CALLBACK =
            "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback";

    /** How wide the compass strip is, and how much of the world it covers. */
    private static final int STRIP_WIDTH = 180;
    private static final double HALF_FOV = 60.0;

    /** One thing drawn on the HUD: where it is, and whether it is on. */
    public static final class Element {
        public final String id;
        public final String label;
        public int x;
        public int y;
        public boolean on;

        Element(String id, String label, int x, int y, boolean on) {
            this.id = id;
            this.label = label;
            this.x = x;
            this.y = y;
            this.on = on;
        }
    }

    private static final List<Element> ELEMENTS = new ArrayList<>(List.of(
            new Element("tracker", "Tracker bar", 0, 4, false),
            new Element("coords", "Coordinates", 4, 4, false),
            new Element("compass", "Waypoint compass", 0, 22, false),
            new Element("clock", "Session time", 4, 16, false)));

    private static boolean attached;
    private static String reason = "not attached yet";

    private Hud() {
    }

    public static List<Element> elements() {
        return ELEMENTS;
    }

    public static Element byId(String id) {
        for (Element element : ELEMENTS) {
            if (element.id.equals(id)) return element;
        }
        return null;
    }

    public static boolean attached() {
        return attached;
    }

    public static String reason() {
        return reason;
    }

    public static void register() {
        Events.Result result = Events.subscribe(CALLBACK, "EVENT",
                (proxy, method, args) -> {
                    onDraw(args);
                    return null;
                });
        attached = result.ok;
        reason = result.reason;
        if (!result.ok) Mirage.LOGGER.warn("Mirage could not draw a HUD: {}", reason);
    }

    /** Finds the thing to draw on among whatever the callback was handed. */
    private static void onDraw(Object[] args) {
        if (args == null) return;
        for (Object argument : args) {
            if (argument instanceof DrawContext context) {
                try {
                    paint(context, false);
                } catch (RuntimeException failure) {
                    // A HUD that throws takes the whole screen with it.
                    Mirage.LOGGER.warn("Mirage stumbled drawing the HUD", failure);
                }
                return;
            }
        }
    }

    /**
     * Draws everything switched on.
     *
     * @param editing true from the HUD editor, which draws every element whether it is on
     *                or not so there is something to drag
     */
    public static void paint(DrawContext context, boolean editing) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) return;
        if (!SelfFakes.enabled()) return;
        // Nothing over a menu, unless the menu is the one for arranging this.
        if (!editing && client.currentScreen != null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        for (Element element : ELEMENTS) {
            if (!element.on && !editing) continue;
            element.x = RyneGui.clampX(element.x, 40, width);
            element.y = RyneGui.clampY(element.y, 10, height);

            switch (element.id) {
                case "tracker" -> tracker(context, client, element, width, editing);
                case "coords" -> coords(context, client, element, editing);
                case "compass" -> compass(context, client, element, width, editing);
                case "clock" -> clock(context, client, element, editing);
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------- the pieces

    private static void tracker(DrawContext context, MinecraftClient client, Element element,
                                int screenWidth, boolean editing) {
        String line = trackerLine();
        if (line == null) {
            if (!editing) return;
            line = "tracker (nothing to show yet)";
        }

        RyneTheme.Theme theme = RyneTheme.current();
        Tracker.Session session = Sessions.current();
        boolean up = session == null || session.net() >= 0;
        boolean warning = session != null && session.lossStreak() >= Sessions.alertAfter();

        int width = line.length() * 6 + 16;
        // x of 0 means centred, which is where a bar like this usually wants to be and
        // saves dragging it to the middle by eye.
        int x = element.x == 0 ? (screenWidth - width) / 2 : element.x;

        RyneDraw.box(context, x, element.y, width, 14, 0xC00B0D12);
        RyneDraw.box(context, x, element.y, 3, 14,
                warning ? 0xFFE0A55F : up ? theme.accent : 0xFFE0655F);
        RyneDraw.text(context, client.textRenderer, line, x + 8, element.y + 3,
                warning ? 0xFFE0A55F : up ? 0xFFE8EAED : 0xFFE0655F);
    }

    /** What the bar says, or null when there is nothing worth a line. */
    static String trackerLine() {
        if (!ChatHook.attached()) return "tracker: cannot read chat";
        if (!Sessions.tracking()) return null;

        Tracker.Session session = Sessions.current();
        if (session == null) return "tracker: no session";

        String text = Tracker.money(session.net()) + "   "
                + session.wins() + "W / " + session.losses() + "L";
        int run = session.lossStreak();
        return run >= 2 ? text + "   " + run + " out in a row" : text;
    }

    private static void coords(DrawContext context, MinecraftClient client, Element element,
                               boolean editing) {
        ClientPlayerEntity player = client.player;
        String line = (int) Math.floor(player.getX()) + " "
                + (int) Math.floor(player.getY()) + " "
                + (int) Math.floor(player.getZ())
                + "   " + Compass.cardinal(player.getYaw());

        RyneDraw.box(context, element.x, element.y, line.length() * 6 + 10, 12, 0xA00B0D12);
        RyneDraw.text(context, client.textRenderer, line, element.x + 5, element.y + 2,
                RyneTheme.current().text);
    }

    private static void clock(DrawContext context, MinecraftClient client, Element element,
                              boolean editing) {
        Tracker.Session session = Sessions.current();
        if (session == null && !editing) return;

        long millis = session == null ? 0 : System.currentTimeMillis() - session.started;
        long minutes = millis / 60000;
        String line = "session " + (minutes / 60) + "h " + (minutes % 60) + "m";

        RyneDraw.box(context, element.x, element.y, line.length() * 6 + 10, 12, 0xA00B0D12);
        RyneDraw.text(context, client.textRenderer, line, element.x + 5, element.y + 2,
                RyneTheme.current().dim);
    }

    /**
     * A strip across the top with a mark for each waypoint ahead of you.
     *
     * <p>Only what is in front: something behind you has no place on a strip that reads
     * left to right as what you are looking at.
     */
    private static void compass(DrawContext context, MinecraftClient client, Element element,
                                int screenWidth, boolean editing) {
        if (!Waypoints.shown() && !editing) return;

        RyneTheme.Theme theme = RyneTheme.current();
        int left = element.x == 0 ? (screenWidth - STRIP_WIDTH) / 2 : element.x;
        int middle = left + STRIP_WIDTH / 2;

        RyneDraw.box(context, left, element.y, STRIP_WIDTH, 13, 0xA00B0D12);
        RyneDraw.box(context, middle, element.y, 1, 13, theme.accent);

        ClientPlayerEntity player = client.player;
        String world = FakeBlocks.worldKey();
        List<Waypoints.Mark> marks = Waypoints.here(world);

        if (marks.isEmpty()) {
            if (editing) {
                RyneDraw.text(context, client.textRenderer, "compass (no waypoints)",
                        left + 6, element.y + 3, theme.dim);
            }
            return;
        }

        for (Waypoints.Mark mark : marks) {
            double dx = mark.x - player.getX();
            double dz = mark.z - player.getZ();
            int offset = Compass.offset(Compass.relative(player.getYaw(), dx, dz),
                    STRIP_WIDTH, HALF_FOV);
            if (offset == Integer.MIN_VALUE) continue;

            int x = middle + offset;
            RyneDraw.box(context, x - 1, element.y + 1, 3, 5, mark.rgb());

            String label = mark.name + " "
                    + Compass.shortDistance(Compass.flatDistance(dx, dz));
            RyneDraw.text(context, client.textRenderer, label,
                    x - label.length() * 3, element.y + 7, mark.rgb());
        }
    }

    // ----------------------------------------------------------------- persistence

    private static Path file() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("mirage-hud.json");
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Element element : ELEMENTS) {
            JsonObject one = new JsonObject();
            one.addProperty("x", element.x);
            one.addProperty("y", element.y);
            one.addProperty("on", element.on);
            root.add(element.id, one);
        }

        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), root.toString());
        } catch (IOException failure) {
            Mirage.LOGGER.warn("Mirage could not write the HUD layout", failure);
        }
    }

    public static void load() {
        if (!Files.exists(file())) return;

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file()));
            if (!parsed.isJsonObject()) return;

            for (Element element : ELEMENTS) {
                JsonElement saved = parsed.getAsJsonObject().get(element.id);
                if (saved == null || !saved.isJsonObject()) continue;

                JsonObject one = saved.getAsJsonObject();
                if (one.has("x")) element.x = one.get("x").getAsInt();
                if (one.has("y")) element.y = one.get("y").getAsInt();
                if (one.has("on")) element.on = one.get("on").getAsBoolean();
            }
        } catch (IOException | RuntimeException failure) {
            Mirage.LOGGER.warn("Mirage could not read the HUD layout", failure);
        }
    }
}
