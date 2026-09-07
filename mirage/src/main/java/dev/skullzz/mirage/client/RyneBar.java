package dev.skullzz.mirage.client;

import java.util.List;

/**
 * The geometry of the horizontal bar: where each tab sits and what is under the pointer.
 *
 * <p>The same modules as the stacked menu, laid out the other way. It reads the very same
 * {@link RyneGui} panels -- a panel is a tab, its rows are the list that drops out of that
 * tab -- so a module toggled here is toggled there, and a setting turned here is the same
 * setting. Two layouts over one model, rather than two menus to keep in step.
 *
 * <p>No Minecraft in here. Laying out a row of tabs is measuring strings and adding up
 * widths, and the thing that goes wrong with it -- a tab whose hit box is not where it was
 * drawn -- is invisible until something is a few pixels off and clicks the wrong one. So
 * the drawing and the hit testing are both taken from here, and the test is that they
 * agree across every tab.
 */
public final class RyneBar {

    /** How tall the bar itself is. */
    public static final int HEIGHT = 20;

    /** Space either side of a tab's label. */
    public static final int PADDING = 10;

    /** How wide a dropped-open list is, regardless of the tab above it. */
    public static final int LIST_WIDTH = 124;

    /** How tall one module in the list is. */
    public static final int ITEM_HEIGHT = 15;

    /** Where the bar sits. Kept here so the drawing and the hit testing cannot disagree. */
    public static final int LEFT = 8;
    public static final int TOP = 8;

    private RyneBar() {
    }

    /** How wide one tab is, label included. */
    public static int tabWidth(String title) {
        return RyneType.width(RyneType.caps(title), RyneType.TRACKING) + PADDING * 2;
    }

    /** Where a tab starts, measured along the bar from {@link #LEFT}. */
    public static int tabX(List<RyneGui.Panel> panels, int index) {
        int x = LEFT;
        for (int i = 0; i < index && i < panels.size(); i++) {
            x += tabWidth(panels.get(i).title);
        }
        return x;
    }

    /** Which tab the pointer is on, or -1. */
    public static int tabAt(List<RyneGui.Panel> panels, double mouseX, double mouseY) {
        if (mouseY < TOP || mouseY >= TOP + HEIGHT) return -1;

        for (int i = 0; i < panels.size(); i++) {
            int x = tabX(panels, i);
            if (mouseX >= x && mouseX < x + tabWidth(panels.get(i).title)) return i;
        }
        return -1;
    }

    /** The top of the list that drops out of a tab. */
    public static int listTop() {
        return TOP + HEIGHT;
    }

    /**
     * How tall an open list is, the settings showing under any module included.
     *
     * <p>Summed from the rows for the same reason the stacked panel sums them: a module
     * with its knobs out is taller than one without, and counting them instead leaves the
     * list ending part way down its own contents.
     */
    public static int listHeight(RyneGui.Panel panel) {
        int height = 0;
        for (RyneGui.Row row : panel.rows) {
            height += ITEM_HEIGHT + Math.round(row.settings.size() * RyneGui.SETTING_HEIGHT
                    * row.openness);
        }
        return height;
    }

    /** How tall one module's line is, its own knobs included. */
    public static int itemHeight(RyneGui.Row row) {
        return ITEM_HEIGHT + Math.round(row.settings.size() * RyneGui.SETTING_HEIGHT
                * row.openness);
    }

    /** The y a module starts at, relative to {@link #listTop()}. */
    public static int itemTop(RyneGui.Panel panel, int index) {
        int y = 0;
        for (int i = 0; i < index && i < panel.rows.size(); i++) {
            y += itemHeight(panel.rows.get(i));
        }
        return y;
    }

    /**
     * Which module of an open tab the pointer is on, or -1.
     *
     * <p>Walked rather than divided, since the rows are not all the same height once
     * anything is expanded.
     */
    public static int itemAt(List<RyneGui.Panel> panels, int open, double mouseX,
                             double mouseY) {
        if (open < 0 || open >= panels.size()) return -1;

        int x = tabX(panels, open);
        if (mouseX < x || mouseX >= x + LIST_WIDTH) return -1;

        RyneGui.Panel panel = panels.get(open);
        double walk = listTop();
        if (mouseY < walk) return -1;

        for (int i = 0; i < panel.rows.size(); i++) {
            double next = walk + itemHeight(panel.rows.get(i));
            if (mouseY < next) return i;
            walk = next;
        }
        return -1;
    }

    /**
     * Which of a module's settings the pointer is on, or -1 for the module's own line.
     *
     * <p>The module's own line comes first and is not a setting, so a click on the name
     * still toggles it rather than turning the first knob under it.
     */
    public static int settingAt(List<RyneGui.Panel> panels, int open, int item,
                                double mouseY) {
        if (open < 0 || open >= panels.size()) return -1;

        RyneGui.Panel panel = panels.get(open);
        if (item < 0 || item >= panel.rows.size()) return -1;

        RyneGui.Row row = panel.rows.get(item);
        if (!row.hasSettings() || row.openness <= 0.02f) return -1;

        double top = listTop() + itemTop(panel, item) + ITEM_HEIGHT;
        int index = (int) Math.floor((mouseY - top) / RyneGui.SETTING_HEIGHT);
        return index >= 0 && index < row.settings.size() ? index : -1;
    }

    /** Where a knob's track runs, so the drawing and a click on it agree. */
    public static int trackLeft(List<RyneGui.Panel> panels, int open) {
        return tabX(panels, open) + 62;
    }

    public static int trackWidth() {
        return LIST_WIDTH - 62 - 32;
    }

    /** Where along a track an x is, 0 at the left and 1 at the right. */
    public static double fractionAt(List<RyneGui.Panel> panels, int open, double mouseX) {
        int width = trackWidth();
        return width <= 0 ? 0 : (mouseX - trackLeft(panels, open)) / width;
    }
}
