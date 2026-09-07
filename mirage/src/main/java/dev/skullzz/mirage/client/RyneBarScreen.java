package dev.skullzz.mirage.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.Window;
import net.minecraft.text.Text;

/**
 * The same modules as the stacked menu, along the top instead of down the side.
 *
 * <p>Categories in a row, and the one you click drops its modules out beneath it. It reads
 * {@link RyneClickScreen#shared()}, so this is a second layout over one model rather than a
 * second menu: a module toggled here is toggled there, a knob turned here is the same knob,
 * and neither can drift out of step with the other because there is only one of each.
 *
 * <p>Left-click a module to toggle it, right-click to open its settings -- the same as in
 * the stacked menu, for the same reason: two menus that answer the same click differently
 * are two things to remember.
 *
 * <p>All the geometry is in {@link RyneBar}, which has no Minecraft in it and is run by
 * check-gui.py. A tab whose hit box is not quite where it was drawn is invisible until it
 * starts clicking the neighbour.
 */
public class RyneBarScreen extends Screen implements RyneClickScreen.Clicks {

    private static final float FADE_SPEED = 16f;

    /** Which tab is dropped open, or -1. Kept across openings, like a real menu bar. */
    private static int open = -1;

    private final Screen parent;
    private float shown;
    private long lastFrame;
    private boolean wasDown;

    /** The slider being dragged, so it follows the pointer rather than needing a click each. */
    private RyneGui.Setting heldSetting;

    private static final RyneCursor CURSOR = new RyneCursor();

    public RyneBarScreen() {
        this(null);
    }

    public RyneBarScreen(Screen parent) {
        super(Text.literal("Ryne"));
        this.parent = parent;
    }

    private static List<RyneGui.Panel> panels() {
        return RyneClickScreen.shared().panels();
    }

    @Override
    protected void init() {
        this.lastFrame = System.nanoTime();
    }

    // ------------------------------------------------------------------ the plumbing

    /** Where the pointer is, in the units the bar is laid out in. */
    private double[] pointer() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        if (window.getWidth() == 0 || window.getHeight() == 0) return null;

        return new double[] {
                client.mouse.getX() * window.getScaledWidth() / window.getWidth(),
                client.mouse.getY() * window.getScaledHeight() / window.getHeight() };
    }

    private boolean buttonDown(int button) {
        MinecraftClient client = MinecraftClient.getInstance();
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(), button)
                == GLFW.GLFW_PRESS;
    }

    @Override
    public void onClick() {
        double[] mouse = pointer();
        if (mouse == null) return;

        List<RyneGui.Panel> bar = panels();
        boolean right = buttonDown(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        int tab = RyneBar.tabAt(bar, mouse[0], mouse[1]);
        if (tab >= 0) {
            // Clicking the tab that is already down closes it, which is what a menu bar
            // does and saves reaching for anywhere else to dismiss it.
            open = open == tab ? -1 : tab;
            return;
        }

        int item = RyneBar.itemAt(bar, open, mouse[0], mouse[1]);
        if (item < 0) {
            // Anywhere else shuts the list. Leaving it open over the game is the thing
            // that makes a bar feel stuck.
            open = -1;
            return;
        }

        RyneGui.Row row = panels().get(open).rows.get(item);
        int setting = RyneBar.settingAt(bar, open, item, mouse[1]);

        if (setting >= 0) {
            RyneGui.Setting knob = row.settings.get(setting);
            if (knob.shape == RyneGui.Shape.SLIDER) {
                knob.setFraction(RyneBar.fractionAt(bar, open, mouse[0]));
                this.heldSetting = knob;
            } else {
                knob.step(right ? -1 : 1);
            }
            return;
        }

        if (right) {
            RyneGui.toggleSettings(row);
            return;
        }
        if (row.action != null) row.action.run();
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

        boolean down = buttonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        if (down && !this.wasDown && mouse != null) CURSOR.click((int) px, (int) py);
        if (!down) {
            this.heldSetting = null;
        } else if (this.heldSetting != null && mouse != null) {
            this.heldSetting.setFraction(RyneBar.fractionAt(panels(), open, px));
        }
        this.wasDown = down;

        if (mouse != null) CURSOR.move((int) px, (int) py);
        CURSOR.tick(seconds);

        this.shown = RyneGui.ease(this.shown, 1f, FADE_SPEED, seconds);
        RyneClickScreen.shared().tick(seconds, px, py);

        RyneTheme.Theme theme = RyneTheme.current();
        float a = this.shown;
        List<RyneGui.Panel> bar = panels();

        // Lighter than the stacked menu's wash: a bar is meant to sit over the game while
        // you carry on looking at it, not to take the screen over.
        RyneDraw.box(context, 0, 0, this.width, this.height, RyneGui.fade(0x30000000, a));

        int barWide = RyneBar.tabX(bar, bar.size()) - RyneBar.LEFT;
        RyneDraw.shadow(context, RyneBar.LEFT, RyneBar.TOP, barWide, RyneBar.HEIGHT, 3);
        RyneDraw.gradient(context, RyneBar.LEFT, RyneBar.TOP, barWide, RyneBar.HEIGHT,
                RyneGui.fade(RyneGui.blend(theme.card, 0xFFFFFFFF, 0.05f), a),
                RyneGui.fade(theme.panel, a));

        int hovered = RyneBar.tabAt(bar, px, py);
        for (int i = 0; i < bar.size(); i++) {
            RyneGui.Panel panel = bar.get(i);
            int x = RyneBar.tabX(bar, i);
            int wide = RyneBar.tabWidth(panel.title);
            boolean dropped = i == open;

            if (dropped || i == hovered) {
                RyneDraw.box(context, x, RyneBar.TOP, wide, RyneBar.HEIGHT,
                        RyneGui.fade(dropped ? theme.cardHover : theme.card, a));
            }
            // The underline is what says which tab is open, and it is the accent, so the
            // theme still reads as the theme.
            if (dropped) {
                RyneDraw.box(context, x, RyneBar.TOP + RyneBar.HEIGHT - 2, wide, 2,
                        RyneGui.fade(theme.accent, a));
            }
            RyneDraw.heading(context, this.textRenderer, panel.title, x + RyneBar.PADDING,
                    RyneBar.TOP + 6,
                    RyneGui.fade(dropped ? theme.text : theme.dim, a));
        }

        if (open >= 0 && open < bar.size()) paintList(context, bar, theme, a);

        RyneDraw.text(context, this.textRenderer,
                "left-click a module - right-click it for its settings",
                8, this.height - 12, RyneGui.fade(theme.dim, a));

        if (RyneCursor.on()) RyneDraw.cursor(context, CURSOR, theme.accent);
    }

    private void paintList(DrawContext context, List<RyneGui.Panel> bar,
                           RyneTheme.Theme theme, float a) {
        RyneGui.Panel panel = bar.get(open);
        int x = RyneBar.tabX(bar, open);
        int top = RyneBar.listTop();
        int height = RyneBar.listHeight(panel);

        RyneDraw.shadow(context, x, top, RyneBar.LIST_WIDTH, height, 3);
        RyneDraw.box(context, x, top, RyneBar.LIST_WIDTH, height,
                RyneGui.fade(theme.panel, a));

        int y = top;
        for (int i = 0; i < panel.rows.size(); i++) {
            RyneGui.Row row = panel.rows.get(i);
            boolean on = row.on();

            RyneDraw.box(context, x, y, RyneBar.LIST_WIDTH, RyneBar.ITEM_HEIGHT,
                    RyneGui.fade(RyneGui.blend(theme.panel, theme.cardHover, row.glow), a));
            if (on) {
                RyneDraw.box(context, x, y, 2, RyneBar.ITEM_HEIGHT,
                        RyneGui.fade(theme.accent, a));
            }
            RyneDraw.text(context, this.textRenderer,
                    RyneType.fit(row.label, RyneBar.LIST_WIDTH - 26, 0), x + 8, y + 4,
                    RyneGui.fade(on ? theme.accent : theme.text, a));
            // A dot on the modules that have knobs, so there is a reason to right-click.
            if (row.hasSettings()) {
                RyneDraw.box(context, x + RyneBar.LIST_WIDTH - 10, y + 6, 3, 3,
                        RyneGui.fade(row.expanded ? theme.accent : theme.dim, a));
            }
            y += RyneBar.ITEM_HEIGHT;

            if (row.openness > 0.02f) y = paintKnobs(context, bar, row, x, y, theme, a);
        }
    }

    private int paintKnobs(DrawContext context, List<RyneGui.Panel> bar, RyneGui.Row row,
                           int x, int y, RyneTheme.Theme theme, float a) {
        float open2 = row.openness;
        for (RyneGui.Setting setting : row.settings) {
            RyneDraw.box(context, x, y, RyneBar.LIST_WIDTH, RyneGui.SETTING_HEIGHT,
                    RyneGui.fade(theme.page, a * open2));
            RyneDraw.text(context, this.textRenderer,
                    RyneType.fit(setting.label, 46, 0), x + 12, y + 2,
                    RyneGui.fade(theme.dim, a * open2));

            if (setting.shape == RyneGui.Shape.SLIDER) {
                int trackX = RyneBar.trackLeft(bar, open);
                int wide = RyneBar.trackWidth();
                RyneDraw.box(context, trackX, y + 5, wide, 2,
                        RyneGui.fade(theme.line, a * open2));
                int filled = (int) Math.round(wide * setting.fraction());
                RyneDraw.box(context, trackX, y + 5, Math.max(1, filled), 2,
                        RyneGui.fade(theme.accent, a * open2));
                RyneDraw.box(context, trackX + Math.max(0, filled - 1), y + 3, 3, 6,
                        RyneGui.fade(theme.text, a * open2));
            }

            String shown = setting.shown();
            RyneDraw.text(context, this.textRenderer, shown,
                    RyneType.rightX(x + RyneBar.LIST_WIDTH - 6, shown, 0), y + 2,
                    RyneGui.fade(setting.shape == RyneGui.Shape.SWITCH && setting.on()
                            ? theme.accent : theme.text, a * open2));
            y += RyneGui.SETTING_HEIGHT;
        }
        return y;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
