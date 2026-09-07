package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Map art without the commands: save the one in your hand, put a saved one back onto it,
 * or bring a picture in off the disk.
 *
 * <p>Everything here already worked -- {@link MapArt} has done all of it since the map
 * import went in. What it did not have was anywhere to click, so importing meant typing a
 * file name you had to get exactly right, for a folder you had to find first. That is the
 * whole reason this screen exists: the designs you have are a list you pick from, and the
 * pictures on disk are a list too, so neither is something to remember.
 *
 * <p>Nothing here paints a map you do not own. A map's picture comes from the server, so
 * the client has pixels only for maps it has been shown; hold one, look at it once, and it
 * becomes yours to repaint. A map you have never seen has nothing to repaint and says so.
 */
public class RyneMapScreen extends Screen {

    private static final int SIDEBAR = 200;
    private static final int PAD = 14;
    private static final int ROW = 24;

    /** As many as fit down the side before it wants a scrollbar, which this does not have. */
    private static final int LISTED = 11;

    private final Screen parent;
    private String said;
    private TextFieldWidget nameField;

    public RyneMapScreen() {
        this(null, "");
    }

    public RyneMapScreen(Screen parent) {
        this(parent, "");
    }

    private RyneMapScreen(Screen parent, String said) {
        super(Text.literal("Map art"));
        this.parent = parent;
        this.said = said;
    }

    // ---------------------------------------------------------------------- geometry

    private int left() {
        return (this.width - Math.min(this.width - 20, 880)) / 2;
    }

    private int right() {
        return this.width - left();
    }

    private int top() {
        return (this.height - Math.min(this.height - 20, 470)) / 2;
    }

    private int bottom() {
        return this.height - top();
    }

    private int panel() {
        return left() + SIDEBAR + PAD;
    }

    // ------------------------------------------------------------------------ layout

    @Override
    protected void init() {
        int y = top() + 48;

        // The designs already saved, down the left. Clicking one paints it onto the map
        // in your hand, which is the thing you actually want to do with a design.
        List<String> designs = new ArrayList<>(MapArt.names());
        for (int i = 0; i < Math.min(LISTED, designs.size()); i++) {
            String pick = designs.get(i);
            button(RyneType.fit(pick, SIDEBAR - PAD * 2 - 8, 0),
                    left() + PAD, y, SIDEBAR - PAD * 2, () -> apply(pick));
            y += ROW;
        }
        if (designs.size() > LISTED) {
            y += 4;
        }

        int x = panel();
        int row = top() + 48;

        // The one button this screen is for.
        button("Save the map I am holding", x, row, 230, this::saveHeld);
        button("Repaint held from a design", x + 236, row, 220, () -> {
            if (designs.isEmpty()) {
                say("No designs saved yet. Hold a map and save it first.");
                return;
            }
            say("Pick one from the list on the left -- clicking it paints it onto the map "
                    + "in your hand.");
        });
        row += 30;

        this.nameField = new TextFieldWidget(this.textRenderer, x, row, 230, 20,
                Text.literal("name"));
        this.nameField.setMaxLength(24);
        this.addDrawableChild(this.nameField);
        button("Forget that design", x + 236, row, 220, () -> {
            String name = fieldText();
            if (name.isEmpty()) {
                say("Type the name of the design to forget.");
                return;
            }
            say(MapArt.forget(name) ? "Forgot '" + name + "'."
                    : "No design called '" + name + "'.");
        });
        row += 34;

        // Pictures on disk. The names come from the folder, so there is nothing to spell.
        List<String> pictures = MapArt.picturesCached();
        if (pictures.isEmpty()) {
            button("Look for pictures again", x, row, 230, () -> {
                int found = MapArt.pictures().size();
                say(found == 0
                        ? "Nothing found. Put a PNG in: " + MapArt.pictureFolder()
                        : found + " found. Reopen this to see them.");
            });
            row += 30;
        } else {
            int column = 0;
            for (int i = 0; i < Math.min(6, pictures.size()); i++) {
                String picture = pictures.get(i);
                button(RyneType.fit(picture, 140, 0), x + column * 152, row, 146,
                        () -> importPicture(picture));
                column++;
                if (column == 3) {
                    column = 0;
                    row += 24;
                }
            }
            if (column != 0) row += 24;
            row += 10;
        }

        // Card faces, which is what the maps in the frames are for.
        button("Paint held: card face from the field", x, row, 300, this::paintCard);
        row += 30;

        button("Done", right() - PAD - 90, bottom() - PAD - 20, 90, this::close);
    }

    // ------------------------------------------------------------------------ actions

    private String fieldText() {
        return this.nameField == null ? "" : this.nameField.getText().trim();
    }

    /**
     * Saves the picture of the map in your hand under a name.
     *
     * <p>Every way this can fail is a different fix, so none of them share a message: no
     * map in hand, a map the client has never been shown, or no name typed.
     */
    private void saveHeld() {
        String name = fieldText();
        if (name.isEmpty()) {
            say("Type a name in the box first, then hold the map and press this.");
            return;
        }

        int id = MapArt.heldMapId();
        if (id < 0) {
            say("Hold the map you want to save. It has to be a filled map, not a blank one.");
            return;
        }

        say(MapArt.save(name, id)
                ? "Saved map #" + id + " as '" + name + "'. It is in the list now."
                : MapArt.lastReason());
    }

    /** Paints a saved design onto the map in your hand. */
    private void apply(String name) {
        int id = MapArt.heldMapId();
        if (id < 0) {
            say("Hold the map you want '" + name + "' painted onto.");
            return;
        }

        say(MapArt.load(name, id) ? "Painted '" + name + "' onto map #" + id + "."
                : MapArt.lastReason());
    }

    private void importPicture(String picture) {
        String name = fieldText();
        String under = name.isEmpty() ? stem(picture) : name;
        say(MapArt.importPicture(picture, under)
                ? "Imported " + picture + " as '" + under + "'. Hold a map and click it "
                        + "in the list to put it on."
                : MapArt.lastReason());
    }

    /**
     * Paints a card face onto the held map, for the frames a blackjack table is made of.
     *
     * <p>The characters {@link MapArt} can draw are the ones it has glyphs for, so a face
     * it cannot draw is refused here rather than painted as blanks.
     */
    private void paintCard() {
        String face = RyneType.caps(fieldText());
        if (face.isEmpty()) {
            say("Type the face in the box: a number, or A, J, Q or K.");
            return;
        }

        for (int i = 0; i < face.length(); i++) {
            if (!MapArt.canDraw(face.charAt(i))) {
                say("No glyph for '" + face.charAt(i) + "', so it would paint blank.");
                return;
            }
        }

        int id = MapArt.heldMapId();
        if (id < 0) {
            say("Hold the map that goes in the frame.");
            return;
        }

        byte[] pixels = MapArt.render(face, MapArt.BLACK, MapArt.WHITE);
        say(MapArt.paint(id, pixels) ? "Map #" + id + " now reads " + face + "."
                : MapArt.lastReason());
    }

    /** A file name without its extension, which is a reasonable default design name. */
    static String stem(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        String cut = dot <= 0 ? fileName : fileName.substring(0, dot);
        // Names go through commands elsewhere, where a space would split the argument.
        return cut.replace(' ', '_');
    }

    // ------------------------------------------------------------------------ chrome

    private ButtonWidget button(String label, int x, int y, int width, Runnable action) {
        ButtonWidget widget = ButtonWidget.builder(Text.literal(label),
                        ignored -> action.run())
                .dimensions(x, y, width, 20).build();
        this.addDrawableChild(widget);
        return widget;
    }

    private void say(String message) {
        this.said = message;
        reopen();
    }

    /** Reopened rather than rebuilt in place, like every other screen here. */
    private void reopen() {
        if (this.client == null) return;
        this.client.setScreen(new RyneMapScreen(this.parent, this.said));
    }

    /**
     * Public because it overrides Screen's own, which is public. Every other screen here
     * gets this right; this one was written from scratch and did not.
     */
    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RyneTheme.Theme theme = RyneTheme.current();

        RyneDraw.box(context, left(), top(), right() - left(), bottom() - top(), theme.page);
        RyneDraw.box(context, left(), top(), SIDEBAR, bottom() - top(), theme.panel);
        RyneDraw.box(context, left() + SIDEBAR, top(), 1, bottom() - top(), theme.line);
        RyneDraw.box(context, left() + PAD, top() + PAD, 4, 18, theme.accent);

        RyneDraw.heading(context, this.textRenderer, "MAP ART", left() + PAD + 12,
                top() + PAD + 5, theme.text);
        RyneDraw.heading(context, this.textRenderer, "DESIGNS", left() + PAD, top() + 34,
                theme.dim);

        super.render(context, mouseX, mouseY, delta);

        int x = panel();
        int held = MapArt.heldMapId();
        RyneDraw.text(context, this.textRenderer,
                held < 0 ? "Holding: nothing (hold a filled map)" : "Holding: map #" + held,
                x, top() + PAD + 6, held < 0 ? theme.dim : theme.accent);

        if (!MapArt.picturesCached().isEmpty()) {
            RyneDraw.text(context, this.textRenderer, "Pictures in "
                            + RyneType.fit(MapArt.pictureFolder().toString(), 460, 0),
                    x, bottom() - PAD - 46, theme.dim);
        }
        if (this.said != null && !this.said.isEmpty()) {
            RyneDraw.text(context, this.textRenderer,
                    RyneType.fit(this.said, right() - x - PAD - 100, 0),
                    x, bottom() - PAD - 30, theme.text);
        }
    }
}
