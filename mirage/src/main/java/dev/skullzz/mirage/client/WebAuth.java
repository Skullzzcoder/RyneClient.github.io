package dev.skullzz.mirage.client;

import java.security.SecureRandom;

/**
 * A key on the dashboard, so a web page you happen to be reading cannot drive your client.
 *
 * <p>The dashboard listens on 127.0.0.1, which sounds like it is only reachable by you. It
 * is not. Any page open in your browser can send a request to your own machine: an
 * {@code <img src="http://127.0.0.1:25599/power?on=0">} on any site you visit would have
 * thrown the master switch, and until now nothing stopped it. Nobody has to find your
 * computer for that -- the browser is already on it.
 *
 * <p>So every request that changes something carries a key, made fresh each time the
 * server starts and never written to disk. A page that does not know it gets 403 and
 * changes nothing. The key is in the URL the mod prints in chat, and the dashboard's own
 * links carry it, so you paste the address once and never think about it again.
 *
 * <p>Reads are left open. A page that can see which rig is selected is a much smaller
 * matter than one that can select it, and keeping /state open means anything you have
 * already pointed at the dashboard keeps working.
 */
public final class WebAuth {

    /** Long enough that guessing is not a strategy; short enough to paste. */
    private static final int LENGTH = 24;

    /** No look-alikes: this gets read off a screen and typed often enough to matter. */
    private static final String ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789";

    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String key = "";

    private WebAuth() {
    }

    /** A fresh key. Called when the server starts, so a restart invalidates the old one. */
    public static String renew() {
        StringBuilder made = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            made.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        key = made.toString();
        return key;
    }

    public static String key() {
        return key;
    }

    /** Forgotten, for a server that has stopped. */
    public static void clear() {
        key = "";
    }

    /**
     * Whether a request carries the key.
     *
     * <p>Compared in constant time. The difference is unmeasurable over a socket on the
     * same machine and it costs one loop, which is a better trade than explaining why a
     * comparison that returns early was fine.
     *
     * <p>An empty key never matches. A server that failed to make one is shut, not open.
     */
    public static boolean allows(String query) {
        return matches(WebSettings.field(query, "k"));
    }

    public static boolean matches(String offered) {
        String expected = key;
        if (expected.isEmpty() || offered == null || offered.isEmpty()) return false;
        if (offered.length() != expected.length()) return false;

        int differences = 0;
        for (int i = 0; i < expected.length(); i++) {
            differences |= expected.charAt(i) ^ offered.charAt(i);
        }
        return differences == 0;
    }

    /** The address to open, key included. */
    public static String address(String host, int port) {
        return "http://" + host + ":" + port + "/?k=" + key;
    }
}
