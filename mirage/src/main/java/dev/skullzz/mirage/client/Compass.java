package dev.skullzz.mirage.client;

/**
 * Which way a thing is, and how far.
 *
 * <p>No Minecraft in here. A bearing with one sign wrong points at the mirror image of
 * where you meant, which looks entirely plausible until you walk the wrong way for two
 * minutes -- so the arithmetic is kept where it can be run against known answers.
 *
 * <p>Minecraft's yaw is its own thing and worth writing down: 0 faces positive Z (south),
 * 90 faces negative X (west), 180 negative Z (north), 270 positive X (east). So the
 * heading that would face a point is atan2(-dx, dz), which is what everything below is
 * built on.
 */
public final class Compass {

    private Compass() {
    }

    /** The yaw that would be facing a point this far away. */
    public static double yawTo(double dx, double dz) {
        return Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** Any angle brought into -180..180, so "10 degrees left" never reads as 350. */
    public static double wrap180(double degrees) {
        double turned = (degrees + 180.0) % 360.0;
        if (turned < 0) turned += 360.0;
        return turned - 180.0;
    }

    /** How far off straight ahead something is: negative left, positive right. */
    public static double relative(double playerYaw, double dx, double dz) {
        return wrap180(yawTo(dx, dz) - playerYaw);
    }

    /**
     * Where on a strip a bearing sits.
     *
     * @return pixels from the middle, or {@link Integer#MIN_VALUE} when it is behind you
     *         and does not belong on the strip at all
     */
    public static int offset(double relativeDegrees, int width, double halfFieldOfView) {
        if (halfFieldOfView <= 0) return Integer.MIN_VALUE;
        if (Math.abs(relativeDegrees) > halfFieldOfView) return Integer.MIN_VALUE;
        return (int) Math.round(relativeDegrees / halfFieldOfView * (width / 2.0));
    }

    /** Flat distance, ignoring height, which is what "how far" usually means. */
    public static double flatDistance(double dx, double dz) {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Rounded the way a person would say it: 940, 1.2k, 14.3k. */
    public static String shortDistance(double blocks) {
        if (blocks < 1000) return Math.round(blocks) + "m";
        return String.format("%.1fk", blocks / 1000.0);
    }

    /**
     * N, NE, E and so on -- the coarse answer, for when the strip is not on.
     *
     * <p>The list starts at south because yaw 0 does, and the angle is brought into
     * 0..360 rather than -180..180: folding it to -180..180 first and then shifting by
     * 180 turns every direction into its opposite, which is a compass that is confidently
     * and consistently wrong.
     */
    public static String cardinal(double yaw) {
        String[] points = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };
        double turned = ((yaw % 360.0) + 360.0) % 360.0;
        int index = (int) Math.round(turned / 45.0) % 8;
        return points[index];
    }
}
