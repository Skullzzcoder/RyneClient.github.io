import dev.skullzz.mirage.client.RyneGui;
import dev.skullzz.mirage.client.RyneBar;
import dev.skullzz.mirage.client.RyneType;

/** Drives the click GUI's model and prints what it decided, one case per line. */
public class Harness {
    static int fails = 0;

    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("FAIL " + what); fails++; }
    }

    public static void main(String[] args) {
        RyneGui gui = new RyneGui();
        RyneGui.Panel a = gui.add(new RyneGui.Panel("a", "Alpha", 10, 10));
        a.add("one", RyneGui.Kind.TOGGLE, () -> {}, () -> true);
        a.add("two", RyneGui.Kind.ACTION, () -> {}, null);
        a.add("three", RyneGui.Kind.ACTION, () -> {}, null);

        // --- geometry
        check("title height", RyneGui.TITLE_HEIGHT == 20);
        check("open panel height", a.height() == 20 + 3 * 18);
        check("in title at top-left", RyneGui.inTitle(a, 10, 10));
        check("in title at bottom-right", RyneGui.inTitle(a, 10 + 131, 29));
        check("not in title one past the right", !RyneGui.inTitle(a, 10 + 132, 15));
        check("not in title one below", !RyneGui.inTitle(a, 15, 30));

        // Rows start immediately under the title, and each is exactly ROW_HEIGHT.
        check("first row at its top edge", RyneGui.rowAt(a, 15, 30) == 0);
        check("first row at its bottom edge", RyneGui.rowAt(a, 15, 47) == 0);
        check("second row starts at 48", RyneGui.rowAt(a, 15, 48) == 1);
        check("last row is the last", RyneGui.rowAt(a, 15, 20 + 10 + 3 * 18 - 1) == 2);
        check("one past the last row is nothing", RyneGui.rowAt(a, 15, 10 + 20 + 3 * 18) == -1);
        check("the title is not a row", RyneGui.rowAt(a, 15, 15) == -1);
        check("outside to the left is nothing", RyneGui.rowAt(a, 9, 35) == -1);

        // --- a shut panel takes no row clicks, however it is drawn
        a.open = false;
        a.openness = 0f;
        check("a shut panel has only its title", a.height() == 20);
        check("a shut panel takes no row clicks", RyneGui.rowAt(a, 15, 35) == -1);
        check("but its title still answers", RyneGui.inTitle(a, 15, 15));
        a.openness = 0.5f;
        check("a half open panel still takes clicks on what is shown",
                RyneGui.rowAt(a, 15, 35) == 0);
        a.open = true;
        a.openness = 1f;

        // --- overlapping panels: only the topmost answers
        RyneGui.Panel b = gui.add(new RyneGui.Panel("b", "Beta", 10, 10));
        b.add("only", RyneGui.Kind.ACTION, () -> {}, null);
        check("the later panel is on top", gui.topmostAt(15, 15) == b);
        gui.raise(a);
        check("raising brings it to the front", gui.topmostAt(15, 15) == a);

        // --- dragging keeps the grab offset
        gui.beginDrag(a, 40, 18);
        gui.dragTo(140, 118, 1000, 600);
        check("dragged by the delta, not snapped", a.x == 110 && a.y == 110);
        check("dragging raises it", gui.topmostAt(115, 115) == a);
        check("a real drag is not a tap", gui.endDrag() == null);
        check("drag ends", !gui.isDragging());

        // Pressed and released on a title without moving: that folds the panel, and must
        // not be confused with a drag, or a nudge while clicking loses the panel.
        gui.beginDrag(a, a.x + 5, a.y + 5);
        check("no movement at all is a tap", gui.endDrag() == a);
        gui.beginDrag(a, a.x + 5, a.y + 5);
        gui.dragTo(a.x + 6, a.y + 6, 1000, 600);
        check("a hand-sized wobble is still a tap", gui.endDrag() == a);
        gui.beginDrag(a, a.x + 5, a.y + 5);
        gui.dragTo(a.x + 40, a.y + 5, 1000, 600);
        check("moving well past the slop is a drag", gui.endDrag() == null);

        // The way a panel is actually dragged: a pixel or two per frame, for a long way.
        // Every test above moves in one jump, which is why this went unnoticed -- movement
        // was measured against the panel's position last frame, so a steady drag never
        // exceeded the slop in any single frame and the panel folded itself away on
        // release as though it had been tapped.
        gui.beginDrag(a, a.x + 5, a.y + 5);
        double slowX = a.x + 5;
        for (int step = 0; step < 200; step++) {
            slowX += 1;
            gui.dragTo(slowX, a.y + 5, 1000, 600);
        }
        check("a slow drag across the screen is a drag, not a tap", gui.endDrag() == null);

        // And at an edge, where the panel is clamped and stops moving while the pointer
        // keeps going. Measured against the panel that reads as no movement at all.
        gui.beginDrag(a, a.x + 5, a.y + 5);
        for (int step = 0; step < 300; step++) gui.dragTo(-step, a.y + 5, 1000, 600);
        check("dragging into an edge is still a drag", gui.endDrag() == null);

        // --- settings: the knobs a right-click opens
        //
        // Every one of these is arithmetic that shows up on screen as something stranger
        // than the number behind it: a slider handle off the end of its track, a mode that
        // wraps to nothing, a value that reads 5 while holding 5.31.
        double[] held = { 3 };
        RyneGui.Setting slider = RyneGui.Setting.slider("size", () -> held[0],
                v -> held[0] = v, 1, 10, 1);
        check("a slider starts where its value is", slider.value() == 3);
        check("and its handle is proportional",
                Math.abs(slider.fraction() - (3 - 1) / 9.0) < 1e-9);

        slider.setFraction(0);
        check("dragged to the left end it is the least", slider.value() == 1);
        slider.setFraction(1);
        check("and to the right end the most", slider.value() == 10);
        slider.setFraction(2);
        check("past the end it is still the most", slider.value() == 10);
        slider.setFraction(-1);
        check("and before the start still the least", slider.value() == 1);

        // Snapped when stored, not only when shown.
        RyneGui.Setting stepped = RyneGui.Setting.slider("step", () -> held[0],
                v -> held[0] = v, 0, 10, 2);
        for (int i = 0; i <= 20; i++) {
            stepped.setFraction(i / 20.0);
            check("a stepped slider only ever holds a step",
                    Math.abs(held[0] / 2 - Math.rint(held[0] / 2)) < 1e-9);
            check("and stays inside its bounds", held[0] >= 0 && held[0] <= 10);
        }

        // A value set from outside, past the ends, still reads inside them.
        held[0] = 999;
        check("a value from elsewhere is clamped when read", stepped.value() == 10);
        held[0] = -999;
        check("at both ends", stepped.value() == 0);

        double[] which = { 0 };
        RyneGui.Setting mode = RyneGui.Setting.mode("mode", () -> which[0],
                v -> which[0] = v, java.util.List.of("first", "second", "third"));
        check("a mode reads its option", mode.shown().equals("first"));
        mode.step(1);
        check("stepping moves along", mode.shown().equals("second"));
        mode.step(1);
        mode.step(1);
        check("and wraps rather than running off the end", mode.shown().equals("first"));
        mode.step(-1);
        check("backwards too", mode.shown().equals("third"));

        boolean[] flag = { false };
        RyneGui.Setting sw = RyneGui.Setting.switching("on", () -> flag[0],
                v -> flag[0] = v);
        check("a switch starts off", !sw.on() && sw.shown().equals("off"));
        sw.step(1);
        check("and flips", sw.on() && flag[0]);
        sw.step(1);
        check("and back", !sw.on() && !flag[0]);

        // --- a row with settings is taller, and the rows below it move down
        RyneGui.Panel s1 = gui.add(new RyneGui.Panel("s", "Settings", 400, 400));
        s1.add("plain", RyneGui.Kind.TOGGLE, () -> { }, () -> false);
        s1.add("knobs", RyneGui.Kind.TOGGLE, () -> { }, () -> false);
        RyneGui.Row knobs = s1.rows.get(1);
        knobs.with(RyneGui.Setting.slider("a", () -> 1, v -> { }, 0, 5, 1));
        knobs.with(RyneGui.Setting.slider("b", () -> 1, v -> { }, 0, 5, 1));
        s1.openness = 1f;

        int shutHeight = s1.height();
        check("a row with settings shut is the same as one without",
                knobs.height() == RyneGui.ROW_HEIGHT);
        check("only settings that have a module have any", !s1.rows.get(0).hasSettings());
        check("right-clicking one with none does nothing",
                !RyneGui.toggleSettings(s1.rows.get(0)));
        check("and one with some opens", RyneGui.toggleSettings(knobs) && knobs.expanded);

        knobs.openness = 1f;
        check("an open row is taller by its settings",
                knobs.height() == RyneGui.ROW_HEIGHT + 2 * RyneGui.SETTING_HEIGHT);
        check("and the panel grew by exactly that",
                s1.height() == shutHeight + 2 * RyneGui.SETTING_HEIGHT);

        // The row under the pointer has to be found by walking, not dividing: with the
        // first row expanded, dividing lands on the wrong one every time.
        RyneGui.Row first = s1.rows.get(0);
        first.with(RyneGui.Setting.slider("c", () -> 1, v -> { }, 0, 5, 1));
        first.expanded = true;
        first.openness = 1f;
        double bodyTop = s1.y + RyneGui.TITLE_HEIGHT;
        check("the first row is still the first row",
                RyneGui.rowAt(s1, s1.x + 10, bodyTop + 2) == 0);
        check("its setting line belongs to it too",
                RyneGui.rowAt(s1, s1.x + 10,
                        bodyTop + RyneGui.ROW_HEIGHT + 2) == 0);
        check("and that line is its first setting",
                RyneGui.settingAt(s1, 0, bodyTop + RyneGui.ROW_HEIGHT + 2) == 0);
        check("the row's own line is not a setting",
                RyneGui.settingAt(s1, 0, bodyTop + 2) == -1);
        check("the second row starts below all of that",
                RyneGui.rowAt(s1, s1.x + 10,
                        bodyTop + first.height() + 2) == 1);
        check("and past the bottom is nothing",
                RyneGui.rowAt(s1, s1.x + 10, bodyTop + s1.height() + 40) == -1);

        // --- the horizontal bar: the same modules laid out the other way
        //
        // The failure this invites is a tab whose hit box is not where it was drawn. It is
        // invisible until a few pixels of drift make a click land on the neighbour, so the
        // drawing and the hit testing come from the same place and are checked against
        // each other across every tab.
        java.util.List<RyneGui.Panel> bar = gui.panels();
        for (int i = 0; i < bar.size(); i++) {
            int tabX = RyneBar.tabX(bar, i);
            int wide = RyneBar.tabWidth(bar.get(i).title);
            check("a tab is found at its own left edge",
                    RyneBar.tabAt(bar, tabX + 1, RyneBar.TOP + 2) == i);
            check("and at its right edge",
                    RyneBar.tabAt(bar, tabX + wide - 1, RyneBar.TOP + 2) == i);
            check("and not one pixel past it",
                    RyneBar.tabAt(bar, tabX + wide, RyneBar.TOP + 2) != i);
            check("a tab is wider than its label",
                    wide > RyneType.width(RyneType.caps(bar.get(i).title),
                            RyneType.TRACKING));
            if (i > 0) {
                check("tabs do not overlap",
                        tabX >= RyneBar.tabX(bar, i - 1)
                                + RyneBar.tabWidth(bar.get(i - 1).title));
            }
        }
        check("above the bar is not a tab", RyneBar.tabAt(bar, RyneBar.LEFT + 2, 0) == -1);
        check("below the bar is not a tab",
                RyneBar.tabAt(bar, RyneBar.LEFT + 2, RyneBar.TOP + RyneBar.HEIGHT + 1) == -1);

        // A dropped-open list: every module reachable, and every one only itself.
        RyneGui.Panel dropped = bar.get(0);
        for (RyneGui.Row row : dropped.rows) row.openness = 0f;
        for (int i = 0; i < dropped.rows.size(); i++) {
            int itemY = RyneBar.listTop() + RyneBar.itemTop(dropped, i);
            int inside = RyneBar.tabX(bar, 0) + 4;
            check("a module is found on its own line",
                    RyneBar.itemAt(bar, 0, inside, itemY + 2) == i);
            check("its own line is not a setting",
                    RyneBar.settingAt(bar, 0, i, itemY + 2) == -1);
        }
        check("past the end of the list is nothing",
                RyneBar.itemAt(bar, 0, RyneBar.tabX(bar, 0) + 4,
                        RyneBar.listTop() + RyneBar.listHeight(dropped) + 30) == -1);
        check("beside the list is nothing",
                RyneBar.itemAt(bar, 0, RyneBar.tabX(bar, 0) + RyneBar.LIST_WIDTH + 5,
                        RyneBar.listTop() + 2) == -1);
        check("a tab that is not open has no modules",
                RyneBar.itemAt(bar, -1, RyneBar.LEFT + 4, RyneBar.listTop() + 2) == -1);

        // With a module expanded, the ones below it move down and stay findable -- the
        // same walking-not-dividing property the stacked panel needs.
        RyneGui.Row expanded = dropped.rows.get(0);
        expanded.with(RyneGui.Setting.slider("x", () -> 1, v -> { }, 0, 4, 1));
        expanded.with(RyneGui.Setting.slider("y", () -> 1, v -> { }, 0, 4, 1));
        expanded.expanded = true;
        expanded.openness = 1f;
        check("the expanded module is still first",
                RyneBar.itemAt(bar, 0, RyneBar.tabX(bar, 0) + 4,
                        RyneBar.listTop() + 2) == 0);
        check("its knobs belong to it",
                RyneBar.itemAt(bar, 0, RyneBar.tabX(bar, 0) + 4,
                        RyneBar.listTop() + RyneBar.ITEM_HEIGHT + 2) == 0);
        check("and are found in order",
                RyneBar.settingAt(bar, 0, 0,
                        RyneBar.listTop() + RyneBar.ITEM_HEIGHT + 2) == 0
                        && RyneBar.settingAt(bar, 0, 0, RyneBar.listTop()
                                + RyneBar.ITEM_HEIGHT + RyneGui.SETTING_HEIGHT + 2) == 1);
        if (dropped.rows.size() > 1) {
            check("the next module has moved down below them",
                    RyneBar.itemAt(bar, 0, RyneBar.tabX(bar, 0) + 4,
                            RyneBar.listTop() + RyneBar.itemHeight(expanded) + 2) == 1);
        }
        check("the list grew by exactly the knobs",
                RyneBar.itemHeight(expanded)
                        == RyneBar.ITEM_HEIGHT + 2 * RyneGui.SETTING_HEIGHT);

        // A track a click lands on has to be the track that was drawn.
        check("the left of a track is nothing along it",
                Math.abs(RyneBar.fractionAt(bar, 0, RyneBar.trackLeft(bar, 0))) < 1e-9);
        check("and the right of it is all the way",
                Math.abs(RyneBar.fractionAt(bar, 0,
                        RyneBar.trackLeft(bar, 0) + RyneBar.trackWidth()) - 1) < 1e-9);
        check("a track fits inside its list",
                RyneBar.trackLeft(bar, 0) + RyneBar.trackWidth()
                        <= RyneBar.tabX(bar, 0) + RyneBar.LIST_WIDTH);

        expanded.expanded = false;
        expanded.openness = 0f;
        expanded.settings.clear();

        // --- a panel may not be dragged off where it cannot be got back
        gui.beginDrag(a, a.x + 5, a.y + 5);
        gui.dragTo(-500, -500, 1000, 600);
        check("cannot be dragged off the top left", a.x == 0 && a.y == 0);
        gui.dragTo(5000, 5000, 1000, 600);
        check("cannot be dragged off the bottom right",
                a.x == 1000 - RyneGui.PANEL_WIDTH && a.y == 600 - RyneGui.TITLE_HEIGHT);
        check("its handle is still on screen", a.y + RyneGui.TITLE_HEIGHT <= 600);
        gui.endDrag();

        // --- the clamp the HUD shares, including a window mid-resize
        check("clamped inside stays put", RyneGui.clampX(50, 100, 1000) == 50);
        check("clamped past the right comes back", RyneGui.clampX(990, 100, 1000) == 900);
        check("clamped past the left comes back", RyneGui.clampX(-50, 100, 1000) == 0);
        check("a screen narrower than the box gives zero, not a negative",
                RyneGui.clampX(40, 100, 60) == 0);
        check("the same holds vertically", RyneGui.clampY(-5, 20, 400) == 0
                && RyneGui.clampY(500, 20, 400) == 380);

        // --- snapping: near enough lines up, far enough is left alone
        check("far away is untouched", RyneGui.snapTo(50, new int[] { 0, 200 }) == 50);
        check("near an edge snaps to it", RyneGui.snapTo(3, new int[] { 0, 200 }) == 0);
        check("exactly at the limit still snaps",
                RyneGui.snapTo(RyneGui.SNAP, new int[] { 0 }) == 0);
        check("one past the limit does not",
                RyneGui.snapTo(RyneGui.SNAP + 1, new int[] { 0 }) == RyneGui.SNAP + 1);
        // Nearest, not first: order of candidates must not decide the answer.
        check("the nearest wins, whatever the order",
                RyneGui.snapTo(6, new int[] { 0, 8 }) == 8
                && RyneGui.snapTo(6, new int[] { 8, 0 }) == 8);
        check("no candidates changes nothing", RyneGui.snapTo(42, new int[0]) == 42);

        RyneGui snapping = new RyneGui();
        RyneGui.Panel one = snapping.add(new RyneGui.Panel("one", "One", 0, 0));
        one.add("r", RyneGui.Kind.ACTION, () -> {}, null);
        RyneGui.Panel two = snapping.add(new RyneGui.Panel("two", "Two", 200, 200));
        two.add("r", RyneGui.Kind.ACTION, () -> {}, null);

        two.x = RyneGui.PANEL_WIDTH + 3;
        two.y = 4;
        snapping.snap(two, 1000, 600);
        check("a panel snaps beside another", two.x == RyneGui.PANEL_WIDTH);
        check("and to its top edge", two.y == 0);
        check("a panel never snaps to itself", one.x == 0 && one.y == 0);

        // --- resetting puts them back where they were built
        two.x = 500; two.y = 400; two.open = false;
        snapping.reset();
        check("reset restores the position", two.x == 200 && two.y == 200);
        check("and opens it again", two.open);

        // --- easing is measured in seconds, not frames
        float slow = RyneGui.ease(0f, 1f, 14f, 1f / 30f);
        float fast = RyneGui.ease(0f, 1f, 14f, 1f / 60f);
        float twice = RyneGui.ease(fast, 1f, 14f, 1f / 60f);
        check("one big step matches two small ones", Math.abs(slow - twice) < 0.002f);
        check("easing moves toward the target", slow > 0f && slow < 1f);
        check("easing lands exactly", RyneGui.ease(0.9999f, 1f, 14f, 1f) == 1f);
        check("no time is no movement", RyneGui.ease(0.3f, 1f, 14f, 0f) == 0.3f);

        // --- colours
        check("blend at zero is the first", RyneGui.blend(0x11223344, 0x99887766, 0f) == 0x11223344);
        check("blend at one is the second", RyneGui.blend(0x11223344, 0x99887766, 1f) == 0x99887766);
        check("blend halfway is between",
                ((RyneGui.blend(0xFF000000, 0xFF0000FF, 0.5f)) & 0xFF) == 128);
        check("fade halves the alpha", ((RyneGui.fade(0xFF123456, 0.5f) >>> 24) & 0xFF) == 128);
        check("fade keeps the colour", (RyneGui.fade(0xFF123456, 0.5f) & 0xFFFFFF) == 0x123456);

        // --- the animation loop touches everything
        // Put back where it started: the clamp test above left it in the far corner, and
        // hovering (15, 15) there tests nothing. The first run of this failed on exactly
        // that, which is the test being wrong rather than the code.
        a.x = 10;
        a.y = 10;
        gui.tick(1f / 60f, 15, 15);
        check("a hovered title lights up", a.glow > 0f);
        b.open = false;
        for (int i = 0; i < 200; i++) gui.tick(1f / 60f, -1, -1);
        check("a closed panel finishes closing", b.openness == 0f);
        check("an unhovered title goes dark", a.glow == 0f);

        System.out.println(fails == 0 ? "OK" : fails + " FAILED");
    }
}
