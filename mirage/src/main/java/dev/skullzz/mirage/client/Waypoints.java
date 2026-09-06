package dev.skullzz.mirage.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.skullzz.mirage.Mirage;

/**
 * Places worth going back to.
 *
 * <p>Kept against the world they were marked in, the same way builds are, so a shop on one
 * server does not turn up as a bearing on another. A waypoint with no world recorded --
 * one saved before this did that -- shows everywhere, which is the old behaviour rather
 * than a waypoint that vanishes.
 */
public final class Waypoints {

    /** The colours a waypoint can be, so one word picks one rather than a hex code. */
    public static final String[] COLOURS =
            { "white", "red", "orange", "yellow", "green", "blue", "purple", "pink" };

    private static final int[] VALUES = {
        0xFFE8EAED, 0xFFE0655F, 0xFFE0A55F, 0xFFE8D25F,
        0xFF7FD18B, 0xFF5FA8E0, 0xFF9B7FE0, 0xFFE07FC0,
    };

    public static final class Mark {
        public final String name;
        public final int x;
        public final int y;
        public final int z;
        public final String colour;
        public final String world;

        public Mark(String name, int x, int y, int z, String colour, String world) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.z = z;
            this.colour = colour;
            this.world = world;
        }

        public int rgb() {
            for (int i = 0; i < COLOURS.length; i++) {
                if (COLOURS[i].equalsIgnoreCase(this.colour)) return VALUES[i];
            }
            return VALUES[0];
        }

        /** Whether it belongs where you are standing. */
        public boolean here(String currentWorld) {
            return this.world == null || this.world.isEmpty()
                    || this.world.equals(currentWorld);
        }
    }

    private static final List<Mark> marks = new ArrayList<>();
    private static boolean shown = true;

    private Waypoints() {
    }

    public static List<Mark> all() {
        return marks;
    }

    public static boolean shown() {
        return shown;
    }

    public static void setShown(boolean on) {
        shown = on;
        save();
    }

    /** Just the ones in this world, which is what a compass should be pointing at. */
    public static List<Mark> here(String world) {
        List<Mark> out = new ArrayList<>();
        for (Mark mark : marks) {
            if (mark.here(world)) out.add(mark);
        }
        return out;
    }

    public static Mark byName(String name) {
        for (Mark mark : marks) {
            if (mark.name.equalsIgnoreCase(name)) return mark;
        }
        return null;
    }

    /** Adds one, replacing any with the same name so a name always means one place. */
    public static Mark add(String name, int x, int y, int z, String colour, String world) {
        remove(name);
        Mark mark = new Mark(name, x, y, z, colour, world);
        marks.add(mark);
        save();
        return mark;
    }

    public static boolean remove(String name) {
        boolean gone = marks.removeIf(mark -> mark.name.equalsIgnoreCase(name));
        if (gone) save();
        return gone;
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>();
        for (Mark mark : marks) out.add(mark.name);
        return out;
    }

    // ----------------------------------------------------------------- persistence

    private static Path file() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("mirage-waypoints.json");
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("shown", shown);

        JsonArray list = new JsonArray();
        for (Mark mark : marks) {
            JsonObject one = new JsonObject();
            one.addProperty("name", mark.name);
            one.addProperty("x", mark.x);
            one.addProperty("y", mark.y);
            one.addProperty("z", mark.z);
            one.addProperty("colour", mark.colour);
            one.addProperty("world", mark.world);
            list.add(one);
        }
        root.add("marks", list);

        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), root.toString());
        } catch (IOException failure) {
            Mirage.LOGGER.warn("Mirage could not write the waypoints", failure);
        }
    }

    public static void load() {
        marks.clear();
        if (!Files.exists(file())) return;

        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(file()));
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();

            shown = !root.has("shown") || root.get("shown").getAsBoolean();
            if (!root.has("marks")) return;

            for (JsonElement element : root.getAsJsonArray("marks")) {
                JsonObject one = element.getAsJsonObject();
                if (!one.has("name")) continue;
                marks.add(new Mark(
                        one.get("name").getAsString(),
                        one.has("x") ? one.get("x").getAsInt() : 0,
                        one.has("y") ? one.get("y").getAsInt() : 0,
                        one.has("z") ? one.get("z").getAsInt() : 0,
                        one.has("colour") ? one.get("colour").getAsString() : "white",
                        one.has("world") ? one.get("world").getAsString() : ""));
            }
        } catch (IOException | RuntimeException failure) {
            Mirage.LOGGER.warn("Mirage could not read the waypoints", failure);
        }
    }
}
