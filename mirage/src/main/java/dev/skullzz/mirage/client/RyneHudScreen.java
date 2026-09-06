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
 * Arranging the HUD: everything is drawn where it really sits and dragged where you want.
 *
 * <p>The whole HUD is drawn live rather than as boxes standing in for it, so what you are
 * moving is the thing itself. Switched-off elements are drawn too, dimmed, because an
 * element you cannot see is one you cannot turn on.
 *
 * <p>Clicks come through the same screen event the click menu uses, and a release is
 * noticed by the button no longer being down -- the press is the only mouse event this
 * mod has watched compile.
 */
public class RyneHudScreen extends Screen {

    private final Screen parent;
    private Hud.Element dragging;
    private int grabX;
    private int grabY;
    private boolean moved;
    private boolean wasDown;

    public RyneHudScreen() {
        this(null);
    }

    public RyneHudScreen(Screen parent) {
        super(Text.literal("HUD"));
        this.parent = parent;
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof RyneHudScreen editor)) return;
            editor.listen(ScreenMouseEvents.allowMouseClick(screen));
        });
    }

    private void listen(Event<ScreenMouseEvents.AllowMouseClick> event) {
        Object listener = Proxy.newProxyInstance(
                RyneHudScreen.class.getClassLoader(),
                new Class<?>[] { ScreenMouseEvents.AllowMouseClick.class },
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> "ryne hud";
                        };
                    }
                    press();
                    return false;
                });

        @SuppressWarnings("unchecked")
        Event<Object> untyped = (Event<Object>) (Event<?>) event;
        untyped.register(listener);
    }

    private double[] pointer() {
        MinecraftClient client = MinecraftClient.getInstance();
        Window window = client.getWindow();
        if (window.getWidth() == 0 || window.getHeight() == 0) return null;
        return new double[] {
                client.mouse.getX() * window.getScaledWidth() / window.getWidth(),
                client.mouse.getY() * window.getScaledHeight() / window.getHeight() };
    }

    private boolean mouseDown() {
        MinecraftClient client = MinecraftClient.getInstance();
        return GLFW.glfwGetMouseButton(client.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    /** Roughly where an element sits, for grabbing it. Generous on purpose. */
    private static boolean inside(Hud.Element element, double mouseX, double mouseY,
                                  int screenWidth) {
        int width = element.id.equals("compass") ? 180 : 140;
        int x = element.x == 0 ? (screenWidth - width) / 2 : element.x;
        return mouseX >= x - 2 && mouseX <= x + width + 2
                && mouseY >= element.y - 2 && mouseY <= element.y + 16;
    }

    private void press() {
        double[] mouse = pointer();
        if (mouse == null) return;

        // Last first, so the one drawn on top is the one grabbed.
        for (int i = Hud.elements().size() - 1; i >= 0; i--) {
            Hud.Element element = Hud.elements().get(i);
            if (!inside(element, mouse[0], mouse[1], this.width)) continue;

            this.dragging = element;
            this.grabX = (int) Math.round(mouse[0]) - element.x;
            this.grabY = (int) Math.round(mouse[1]) - element.y;
            this.moved = false;
            return;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        RyneTheme.Theme theme = RyneTheme.current();
        RyneDraw.box(context, 0, 0, this.width, this.height, 0x90000000);

        double[] mouse = pointer();
        boolean down = mouseDown();

        if (down && this.dragging != null && mouse != null) {
            int x = (int) Math.round(mouse[0]) - this.grabX;
            int y = (int) Math.round(mouse[1]) - this.grabY;
            if (Math.abs(x - this.dragging.x) >= RyneGui.SLOP
                    || Math.abs(y - this.dragging.y) >= RyneGui.SLOP) {
                this.moved = true;
            }
            this.dragging.x = RyneGui.clampX(x, 40, this.width);
            this.dragging.y = RyneGui.clampY(y, 10, this.height);
        }

        if (!down && this.wasDown && this.dragging != null) {
            // Pressed and released without moving: that is a click, which switches the
            // element on or off. Same three pixels of slop as the click menu.
            if (!this.moved) this.dragging.on = !this.dragging.on;
            Hud.save();
            if (this.dragging.id.equals("tracker")) Sessions.setHud(this.dragging.on);
            this.dragging = null;
        }
        this.wasDown = down;

        // The real HUD, drawn as it really is, including what is switched off.
        Hud.paint(context, true);

        // A frame round each, so a switched-off element is still something to aim at.
        for (Hud.Element element : Hud.elements()) {
            int width = element.id.equals("compass") ? 180 : 140;
            int x = element.x == 0 ? (this.width - width) / 2 : element.x;
            int edge = element == this.dragging ? theme.accent
                    : element.on ? theme.line : 0x60FFFFFF;

            RyneDraw.box(context, x - 2, element.y - 2, width + 4, 1, edge);
            RyneDraw.box(context, x - 2, element.y + 14, width + 4, 1, edge);
            RyneDraw.box(context, x - 2, element.y - 2, 1, 17, edge);
            RyneDraw.box(context, x + width + 1, element.y - 2, 1, 17, edge);

            if (!element.on) {
                RyneDraw.text(context, this.textRenderer, element.label + " (off)",
                        x + 2, element.y + 18, theme.dim);
            }
        }

        super.render(context, mouseX, mouseY, delta);

        RyneDraw.text(context, this.textRenderer,
                "drag to move, click to switch on or off, Escape when you are done",
                8, this.height - 14, theme.dim);
    }

    @Override
    public void close() {
        Hud.save();
        if (this.client != null) this.client.setScreen(this.parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
