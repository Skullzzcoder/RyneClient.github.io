import dev.skullzz.mirage.client.Compass;

public class Harness {
    static int fails = 0;
    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("FAIL " + what); fails++; }
    }
    static boolean near(double a, double b) { return Math.abs(a - b) < 0.001; }

    public static void main(String[] argv) {
        // Minecraft's yaw, written down and then checked rather than assumed.
        check("due south is yaw 0", near(Compass.yawTo(0, 10), 0));
        check("due west is yaw 90", near(Compass.yawTo(-10, 0), 90));
        check("due north is yaw 180", Math.abs(Math.abs(Compass.yawTo(0, -10)) - 180) < 0.001);
        check("due east is yaw -90", near(Compass.yawTo(10, 0), -90));

        check("wrap keeps small angles", near(Compass.wrap180(10), 10));
        check("wrap folds 350 to -10", near(Compass.wrap180(350), -10));
        check("wrap folds -350 to 10", near(Compass.wrap180(-350), 10));
        check("wrap folds 720 to 0", near(Compass.wrap180(720), 0));

        // Facing south, a point due south is straight ahead; due west is 90 to the left.
        check("straight ahead is zero", near(Compass.relative(0, 0, 10), 0));
        check("west of a southward player is left", Compass.relative(0, -10, 0) > 0);
        check("east of a southward player is right", Compass.relative(0, 10, 0) < 0);
        // Facing north (180), a point to the north is straight ahead.
        check("facing north, north is ahead",
                Math.abs(Compass.relative(180, 0, -10)) < 0.001);
        // The wrap-around case, which is where a naive subtraction goes wrong.
        check("across the wrap is still a small angle",
                Math.abs(Compass.relative(179, -1, -100)) < 5);

        check("dead ahead sits in the middle", Compass.offset(0, 200, 90) == 0);
        check("hard left sits at the left end", Compass.offset(-90, 200, 90) == -100);
        check("hard right sits at the right end", Compass.offset(90, 200, 90) == 100);
        check("halfway is halfway", Compass.offset(45, 200, 90) == 50);
        check("behind you is off the strip", Compass.offset(120, 200, 90) == Integer.MIN_VALUE);
        check("just past the edge is off", Compass.offset(90.5, 200, 90) == Integer.MIN_VALUE);
        check("a strip with no width is not drawn",
                Compass.offset(0, 200, 0) == Integer.MIN_VALUE);

        check("distance ignores height", near(Compass.flatDistance(3, 4), 5));
        check("under a thousand is metres", Compass.shortDistance(940).equals("940m"));
        check("over a thousand is thousands", Compass.shortDistance(1234).equals("1.2k"));

        check("yaw 0 is south", Compass.cardinal(0).equals("S"));
        check("yaw 90 is west", Compass.cardinal(90).equals("W"));
        check("yaw 180 is north", Compass.cardinal(180).equals("N"));
        check("yaw -90 is east", Compass.cardinal(-90).equals("E"));
        check("yaw 45 is south west", Compass.cardinal(45).equals("SW"));
        check("yaw 719 wraps round to south", Compass.cardinal(719).equals("S"));

        System.out.println(fails == 0 ? "OK" : fails + " FAILED");
    }
}
