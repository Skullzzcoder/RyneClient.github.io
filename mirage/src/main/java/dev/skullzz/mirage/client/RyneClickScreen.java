package dev.skullzz.mirage.client;

import java.lang.reflect.Proxy;

import org.lwjgl.glfw.GLFW;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.Event;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

/**
 * The click GUI: panels you drag where you like, that slide open and shut.
 *
 * <p>Nothing here is a {@link net.minecraft.client.gui.widget.ButtonWidget}. Everything is
 * drawn and hit-tested by hand, which is the only way to get panels that move and animate
 * -- and it is done through the paths this mod has already watched compile: the click
 * event and the render event, both subscribed to the way FakeClicks does, the pointer read
 * the way FakeClicks reads it, and rectangles through {@link RyneDraw}.
 *
 * <p>The arithmetic underneath -- where a click lands, where a dragged panel ends up, how
 * far through an animation everything is -- lives in {@link RyneGui}, which has no
 * Minecraft in it and is run by check-gui.py. Being four pixels out is invisible in a
 * screenshot and obvious in use.
 */
public class RyneClickScreen extends Screen implements RyneClicks {

    /** How long the whole menu takes to fade in, in seconds. */
    private static final float FADE_SPEED = 16f;

    private static final RyneGui GUI = new RyneGui();
    private static boolean built;

    /**
     * The modules, for whichever layout is showing them.
     *
     * <p>One model, two ways of laying it out: this stacked menu and the horizontal bar.
     * A module toggled in one is toggled in the other, and a knob turned in one is the
     * same knob -- because there is only one of each.
     */
    static RyneGui shared() {
        buildOnce();
        return GUI;
    }



    private final Screen parent;
    private float shown;
    private long lastFrame;
    /** Whether the button was down last frame, so a press and a release can be told apart. */
    private boolean wasDown;

    /** Where a knob's track starts and stops inside the panel. Shared with the drawing. */
    private static final int TRACK_LEFT = 74;
    private static final int TRACK_RIGHT = 12;

    /** The slider being dragged, so it follows the pointer rather than needing a click each. */
    private RyneGui.Setting heldSetting;
    private RyneGui.Panel heldPanel;

    /** Shared, so the trail carries across when one menu opens another. */
    private static final RyneCursor CURSOR = new RyneCursor();

    public RyneClickScreen() {
        this(null);
    }

    public RyneClickScreen(Screen parent) {
        super(Text.literal("Ryne"));
        this.parent = parent;
    }

    // ------------------------------------------------------------------- the panels

    /**
     * Builds the panels once, and never again.
     *
     * <p>Once, because where they have been dragged to is the state worth keeping: a menu
     * that rebuilds its panels on every open is a menu that forgets where you put them.
     */
    private static void buildOnce() {
        if (built) return;
        built = true;

        GUI.add(new RyneGui.Panel("client", "Client", 12, 12)
                .add("Rigs", RyneGui.Kind.TOGGLE, () -> {
                    SelfFakes.setRigsOn(!SelfFakes.rigsOn());
                    if (!SelfFakes.rigsOn()) ClientDispensers.standDown();
                }, SelfFakes::rigsOn)
                .add("Everything", RyneGui.Kind.TOGGLE,
                        () -> SelfFakes.setEnabled(!SelfFakes.enabled()), SelfFakes::enabled)
                .add("Quiet", RyneGui.Kind.TOGGLE,
                        () -> SelfFakes.setQuiet(!SelfFakes.quiet()), SelfFakes::quiet)
                .add("Reset layout", RyneGui.Kind.ACTION, () -> {
                    GUI.reset();
                    RyneLayout.save(GUI);
                }, null));

        GUI.add(new RyneGui.Panel("tracker", "Tracker", 12, 122)
                .add("Tracking", RyneGui.Kind.TOGGLE,
                        () -> Sessions.setTracking(!Sessions.tracking()), Sessions::tracking)
                .add("HUD bar", RyneGui.Kind.TOGGLE,
                        () -> Sessions.setHud(!Sessions.hud()), Sessions::hud)
                .add("Session", RyneGui.Kind.ACTION, () -> {
                    if (Sessions.current() == null) Sessions.start();
                    else Sessions.stop();
                }, null)
                .add("Open it", RyneGui.Kind.ACTION, () -> open(new RyneTrackerScreen()), null));

        GUI.add(new RyneGui.Panel("hud", "HUD", 12, 232)
                .add("Tracker bar", RyneGui.Kind.TOGGLE,
                        () -> toggleHud("tracker"), () -> shows("tracker"))
                .add("Coordinates", RyneGui.Kind.TOGGLE,
                        () -> toggleHud("coords"), () -> shows("coords"))
                .add("Compass", RyneGui.Kind.TOGGLE,
                        () -> toggleHud("compass"), () -> shows("compass"))
                .add("Session time", RyneGui.Kind.TOGGLE,
                        () -> toggleHud("clock"), () -> shows("clock"))
                .add("Arrange it", RyneGui.Kind.ACTION,
                        () -> open(new RyneHudScreen()), null));

        GUI.add(new RyneGui.Panel("world", "World", 160, 12)
                .add("Take all builds", RyneGui.Kind.ACTION, () -> {
                    FakeBlocks.takeAll();
                    FakeBlocks.persist();
                }, null)
                .add("Schematics", RyneGui.Kind.ACTION,
                        () -> open(new MirageSchematicsScreen()), null)
                .add("Fake items", RyneGui.Kind.ACTION,
                        () -> open(new FakeItemsScreen()), null));

        GUI.add(new RyneGui.Panel("rigs", "Rigs", 160, 100)
                .add("Fire watched", RyneGui.Kind.ACTION,
                        ClientDispensers::fireAllWatched, null)
                .add("Refill", RyneGui.Kind.ACTION, ClientDispensers::refillWatched, null)
                .add("Next rig", RyneGui.Kind.ACTION,
                        () -> ClientDispensers.cycleProfile(1), null)
                .add("Open rigs", RyneGui.Kind.ACTION, () -> open(new RyneRigScreen()), null));

        RyneGui.Panel themes = new RyneGui.Panel("theme", "Theme", 308, 12);
        for (int i = 0; i < RyneTheme.ALL.size(); i++) {
            int index = i;
            themes.add(RyneTheme.ALL.get(i).name, RyneGui.Kind.TOGGLE, () -> {
                RyneTheme.choose(index);
                SelfFakes.save();
            }, () -> RyneTheme.index() == index);
        }
        GUI.add(themes);

        // The knobs, hung on the modules they belong to. Right-click a module to see them.
        // Each reads and writes wherever the value already lives, so a setting changed
        // here and the same setting changed by a command are the same setting.
        knob("tracker", "Tracking", RyneGui.Setting.slider("alert after",
                () -> Sessions.alertAfter(), value -> Sessions.setAlertAfter((int) value),
                2, 12, 1));
        knob("tracker", "Tracking", RyneGui.Setting.slider("rakeback %",
                () -> Sessions.rakebackBps() / 100.0,
                value -> Sessions.setRakebackBps((int) Math.round(value * 100)), 0, 50, 1));

        knob("client", "Rigs", RyneGui.Setting.switching("quiet", SelfFakes::quiet,
                SelfFakes::setQuiet));
        knob("client", "Reset layout", RyneGui.Setting.switching("cursor trail",
                RyneCursor::on, RyneCursor::setOn));

        knob("hud", "Tracker bar", RyneGui.Setting.switching("toasts",
                () -> shows("toasts"), on -> toggleHud("toasts")));

        RyneLayout.load(GUI);
    }

    /**
     * Hangs a knob on a named module.
     *
     * <p>By name rather than by holding the row as it is built, so a panel still reads as
     * one statement. A name that matches nothing is ignored rather than thrown: a knob
     * that quietly does not appear is a smaller problem than a menu that will not open.
     */
    private static void knob(String panelId, String rowLabel, RyneGui.Setting setting) {
        RyneGui.Panel panel = GUI.byId(panelId);
        if (panel == null) return;

        for (RyneGui.Row row : panel.rows) {
            if (row.label.equals(rowLabel)) {
                row.with(setting);
                return;
            }
        }
    }

    private static boolean shows(String id) {
        Hud.Element element = Hud.byId(id);
        return element != null && element.on;
    }

    private static void toggleHud(String id) {
        Hud.Element element = Hud.byId(id);
        if (element == null) return;

        element.on = !element.on;
        Hud.save();
        // The tracker bar has a second switch of its own; they must not disagree.
        if (id.equals("tracker")) Sessions.setHud(element.on);
    }

    private static void open(Screen screen) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) client.setScreen(screen);
    }

    // ------------------------------------------------------------------ the plumbing

    /**
     * Hooks the click event for this screen.
     *
     * <p>Through the screen event rather than by overriding mouseClicked, which moved in
     * this version and is one of the four names the keys screen is still unfinished over.
     */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof RyneClicks menu)) return;
            listen(ScreenMouseEvents.allowMouseClick(screen), menu);
        });
    }

    private static void listen(Event<ScreenMouseEvents.AllowMouseClick> event,
                               RyneClicks menu) {
        Object listener = Proxy.newProxyInstance(
                RyneClickScreen.class.getClassLoader(),
                new Class<?>[] { ScreenMouseEvents.AllowMouseClick.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "ryne menu";
                        };
                    }
                    menu.onClick();
                    // Never let the click through: everything on this screen is ours.
                    return false;
                });

        @SuppressWarnings("unchecked")
        Event<Object> untyped = (Event<Object>) (Event<?>) event;
        untyped.register(listener);
    }

    @Override
    protected void init() {
        buildOnce();
        GUI.clampAll(this.width, this.height);
        this.lastFrame = System.nanoTime();
    }

    /** Where the pointer is, in the same units the panels are laid out in. */
    private double[] pointer() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        if (window.getWidth() == 0 || window.getHeight() == 0) return null;

        return new double[] {
                client.mouse.getX() * window.getScaledWidth() / window.getWidth(),
                client.mouse.getY() * window.getScaledHeight() / window.getHeight() };
    }

    private boolean mouseDown() {
        return buttonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    /**
     * Which button, asked of GLFW rather than read out of the event.
     *
     * <p>The click event does carry the button, but as one of four positional arguments
     * reached through a reflective proxy -- and the position of an argument is exactly the
     * sort of thing that moves between versions without a compile error to say so. This
     * is the same call the screen already polls the left button with.
     */
    private boolean buttonDown(int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button)
                == GLFW.GLFW_PRESS;
    }

    /**
     * One click: a title grabs the panel, a row does its thing, a right-click opens its
     * settings, and a click on a knob turns it.
     */
    @Override
    public void onClick() {
        double[] mouse = pointer();
        if (mouse == null) return;

        RyneGui.Panel panel = GUI.topmostAt(mouse[0], mouse[1]);
        if (panel == null) return;

        boolean right = buttonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        if (RyneGui.inTitle(panel, mouse[0], mouse[1])) {
            // Only the left button drags. A right-click on a title would otherwise start a
            // drag that nothing ever ends, since the release poll watches the left button.
            if (!right) GUI.beginDrag(panel, mouse[0], mouse[1]);
            return;
        }

        int row = RyneGui.rowAt(panel, mouse[0], mouse[1]);
        if (row < 0) return;

        RyneGui.Row hit = panel.rows.get(row);
        int setting = RyneGui.settingAt(panel, row, mouse[1]);

        if (setting >= 0) {
            turn(panel, hit, setting, mouse[0], right);
            return;
        }

        if (right) {
            RyneGui.toggleSettings(hit);
            return;
        }
        if (hit.action != null) hit.action.run();
    }

    /** A click on one knob: a switch flips, a mode steps, a slider goes where you clicked. */
    private void turn(RyneGui.Panel panel, RyneGui.Row row, int index, double mouseX,
                      boolean right) {
        RyneGui.Setting setting = row.settings.get(index);
        if (setting.shape == RyneGui.Shape.SLIDER) {
            setting.setFraction(trackFraction(panel, mouseX));
            // Held, so it follows the pointer instead of needing a click per value.
            this.heldSetting = setting;
            this.heldPanel = panel;
            return;
        }
        // Right steps backwards, which is the only way round a list of four is bearable.
        setting.step(right ? -1 : 1);
    }

    /** Where along a knob's track a given x is, 0 at the left and 1 at the right. */
    private static double trackFraction(RyneGui.Panel panel, double mouseX) {
        double left = panel.x + TRACK_LEFT;
        double width = RyneGui.PANEL_WIDTH - TRACK_LEFT - TRACK_RIGHT;
        return width <= 0 ? 0 : (mouseX - left) / width;
    }

    // ---------------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float seconds = Math.min(0.1f, (now - this.lastFrame) / 1_000_000_000f);
        this.lastFrame = now;

        double[] mouse = pointer();
        double px = mouse == null ? -1 : mouse[0];
        double py = mouse == null ? -1 : mouse[1];

        // A press that began on a title bar drags until the button comes back up. Polled
        // rather than taken from a release event, because the press event is the only one
        // this mod has watched compile.
        boolean down = mouseDown();
        if (!down && this.wasDown) {
            // Released without having moved: that was a click on the title, which folds
            // the panel away rather than leaving it where it was.
            RyneGui.Panel wasDragging = GUI.dragged();
            RyneGui.Panel tapped = GUI.endDrag();
            if (tapped != null) {
                tapped.open = !tapped.open;
            } else if (wasDragging != null) {
                // Lined up on release rather than while moving: snapping mid-drag makes
                // the panel fight the pointer, which feels like a bug even when it is not.
                GUI.snap(wasDragging, this.width, this.height);
                GUI.clampAll(this.width, this.height);
            }
            RyneLayout.save(GUI);
        }
        // The press edge, before wasDown is moved on: a ring per click, not one per frame
        // the button is held down.
        if (down && !this.wasDown && mouse != null) CURSOR.click((int) px, (int) py);
        // A slider follows the pointer while the button is held, and lets go the instant
        // it is released -- otherwise it keeps tracking after you have moved on.
        if (!down) {
            this.heldSetting = null;
            this.heldPanel = null;
        } else if (this.heldSetting != null && this.heldPanel != null && mouse != null) {
            this.heldSetting.setFraction(trackFraction(this.heldPanel, px));
        }
        this.wasDown = down;
        if (down && GUI.isDragging()) GUI.dragTo(px, py, this.width, this.height);

        if (mouse != null) CURSOR.move((int) px, (int) py);
        CURSOR.tick(seconds);

        this.shown = RyneGui.ease(this.shown, 1f, FADE_SPEED, seconds);
        GUI.tick(seconds, px, py);

        RyneTheme.Theme theme = RyneTheme.current();
        // A wash over the world, not a blackout: the point of a click menu is that you
        // can still see what you are doing behind it.
        RyneDraw.box(context, 0, 0, this.width, this.height,
                RyneGui.fade(0x60000000, this.shown));

        for (RyneGui.Panel panel : GUI.panels()) paint(context, panel, theme);

        RyneDraw.text(context, this.textRenderer,
                "drag a title to move it - it snaps to edges - click it to fold it away",
                8, this.height - 12, RyneGui.fade(theme.dim, this.shown));

        // Last, so it is over the panels rather than under them -- a trail that vanishes
        // behind the thing you are pointing at is worse than no trail.
        if (RyneCursor.on()) RyneDraw.cursor(context, CURSOR, theme.accent);
    }

    private void paint(DrawContext context, RyneGui.Panel panel, RyneTheme.Theme theme) {
        int width = RyneGui.PANEL_WIDTH;
        int height = panel.height();
        float a = this.shown;
        boolean held = GUI.dragged() == panel;

        // Sitting above the game rather than printed on it.
        RyneDraw.shadow(context, panel.x, panel.y, width, height, 3);
        RyneDraw.rounded(context, panel.x, panel.y, width, height,
                RyneGui.fade(theme.panel, a));

        // The title bar lifts toward the accent as the pointer crosses it, so it is
        // obvious which strip is the handle without a label saying so.
        int bar = RyneGui.blend(theme.card, theme.accent, panel.glow * 0.55f);
        RyneDraw.gradient(context, panel.x + 1, panel.y + 1, width - 2,
                RyneGui.TITLE_HEIGHT - 1,
                RyneGui.fade(RyneGui.blend(bar, 0xFFFFFFFF, 0.06f), a),
                RyneGui.fade(bar, a));
        RyneDraw.box(context, panel.x, panel.y + 2, 2, RyneGui.TITLE_HEIGHT - 4,
                RyneGui.fade(theme.accent, a));

        if (held) {
            RyneDraw.outline(context, panel.x, panel.y, width, height,
                    RyneGui.fade(theme.accent, a));
        }

        RyneDraw.heading(context, this.textRenderer, panel.title, panel.x + 9, panel.y + 6,
                RyneGui.fade(theme.text, a));
        // A caret that turns as the panel opens, rather than two different characters:
        // the shape moving is what says the click did something.
        RyneDraw.box(context, panel.x + width - 14, panel.y + 9, 6, 1,
                RyneGui.fade(theme.dim, a));
        if (!panel.open) {
            RyneDraw.box(context, panel.x + width - 12, panel.y + 7, 1, 5,
                    RyneGui.fade(theme.dim, a * (1f - panel.openness)));
        }

        if (panel.shut()) return;

        int y = panel.y + RyneGui.TITLE_HEIGHT;
        int bottom = panel.y + height;
        for (RyneGui.Row row : panel.rows) {
            if (y >= bottom) break;
            paintRow(context, panel, row, y, Math.min(RyneGui.ROW_HEIGHT, bottom - y),
                    theme, a);
            if (row.openness > 0.02f) {
                paintSettings(context, panel, row, y + RyneGui.ROW_HEIGHT, bottom, theme, a);
            }
            // The row's own height, not a fixed one: an expanded row pushes the rest down.
            y += row.height();
        }
    }

    private void paintRow(DrawContext context, RyneGui.Panel panel, RyneGui.Row row, int y,
                          int height, RyneTheme.Theme theme, float a) {
        boolean on = row.on();
        int back = RyneGui.blend(theme.panel, theme.cardHover, row.glow);
        RyneDraw.box(context, panel.x, y, RyneGui.PANEL_WIDTH, height,
                RyneGui.fade(back, a));

        // A wash of accent that grows from the left as the pointer arrives, fading out
        // across the row. It is the whole reason this is drawn by hand rather than
        // assembled from buttons.
        if (row.glow > 0.01f) {
            int reach = Math.round(RyneGui.PANEL_WIDTH * 0.55f * row.glow);
            RyneDraw.gradient(context, panel.x, y, Math.max(1, reach), height,
                    RyneGui.fade(RyneGui.blend(back, theme.accent, 0.28f * row.glow), a),
                    RyneGui.fade(back, a));
            RyneDraw.box(context, panel.x, y, Math.max(1, Math.round(2 * row.glow)), height,
                    RyneGui.fade(theme.accent, a));
        }

        if (height >= 8) {
            RyneDraw.text(context, this.textRenderer,
                    RyneType.fit(row.label, RyneGui.PANEL_WIDTH - 34, 0),
                    panel.x + 10, y + 5, RyneGui.fade(on ? theme.accent : theme.text, a));
            if (row.kind == RyneGui.Kind.TOGGLE) {
                RyneDraw.box(context, panel.x + RyneGui.PANEL_WIDTH - 18, y + 6, 8, 6,
                        RyneGui.fade(on ? theme.accent : theme.line, a));
            }
        }
    }

    /**
     * The knobs under a module, once it has been right-clicked open.
     *
     * <p>Indented and on a darker ground, so they read as belonging to the row above
     * rather than as more modules. Each is one line: name on the left, value on the right,
     * and for a slider a track between them with the filled part showing where it is.
     */
    private void paintSettings(DrawContext context, RyneGui.Panel panel, RyneGui.Row row,
                               int y, int bottom, RyneTheme.Theme theme, float a) {
        int width = RyneGui.PANEL_WIDTH;
        float open = row.openness;

        for (int i = 0; i < row.settings.size(); i++) {
            int lineY = y + i * RyneGui.SETTING_HEIGHT;
            if (lineY >= bottom) return;

            RyneGui.Setting setting = row.settings.get(i);
            int high = Math.min(RyneGui.SETTING_HEIGHT, bottom - lineY);
            RyneDraw.box(context, panel.x, lineY, width, high,
                    RyneGui.fade(theme.page, a * open));
            // A hairline down the indent, so the group reads as one thing.
            RyneDraw.box(context, panel.x + 10, lineY, 1, high,
                    RyneGui.fade(theme.line, a * open));

            if (high < 8) continue;

            RyneDraw.text(context, this.textRenderer,
                    RyneType.fit(setting.label, TRACK_LEFT - 22, 0),
                    panel.x + 18, lineY + 2, RyneGui.fade(theme.dim, a * open));

            if (setting.shape == RyneGui.Shape.SLIDER) {
                int trackX = panel.x + TRACK_LEFT;
                int trackWide = width - TRACK_LEFT - TRACK_RIGHT - 26;
                RyneDraw.box(context, trackX, lineY + 5, trackWide, 2,
                        RyneGui.fade(theme.line, a * open));
                int filled = (int) Math.round(trackWide * setting.fraction());
                RyneDraw.box(context, trackX, lineY + 5, Math.max(1, filled), 2,
                        RyneGui.fade(theme.accent, a * open));
                // The handle, so there is something to aim at rather than a bare bar.
                RyneDraw.box(context, trackX + Math.max(0, filled - 1), lineY + 3, 3, 6,
                        RyneGui.fade(theme.text, a * open));
            }

            String shown = setting.shown();
            RyneDraw.text(context, this.textRenderer, shown,
                    RyneType.rightX(panel.x + width - 8, shown, 0), lineY + 2,
                    RyneGui.fade(setting.shape == RyneGui.Shape.SWITCH && setting.on()
                            ? theme.accent : theme.text, a * open));
        }
    }

    @Override
    public void close() {
        RyneLayout.save(GUI);
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
