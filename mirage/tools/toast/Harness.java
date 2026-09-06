import dev.skullzz.mirage.client.Toasts;

public class Harness {
    static int fails = 0;
    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("FAIL " + what); fails++; }
    }

    public static void main(String[] argv) {
        Toasts.clear();
        check("starts empty", Toasts.live().isEmpty());

        Toasts.add("hello", Toasts.Kind.PLAIN);
        check("adds one", Toasts.live().size() == 1);
        check("arrives part way in", Toasts.live().get(0).presence() == 0f);

        Toasts.tick(Toasts.SLIDE / 2);
        check("slides in", Toasts.live().get(0).presence() > 0.4f
                && Toasts.live().get(0).presence() < 0.6f);
        Toasts.tick(Toasts.SLIDE / 2);
        check("then sits at full", Toasts.live().get(0).presence() == 1f);

        // The part that goes wrong invisibly: one that never expires is a permanent smudge.
        Toasts.tick(Toasts.LIFE - Toasts.SLIDE - Toasts.SLIDE);
        check("still there just before leaving", Toasts.live().size() == 1);
        // Exactly at the start of the fade it is still fully there, which is right: the
        // fade begins after this instant. A hair past it, it must be on its way out.
        check("full right up to the fade", Toasts.live().get(0).presence() == 1f);
        Toasts.tick(Toasts.SLIDE / 2);
        check("and fading once past it", Toasts.live().get(0).presence() < 1f
                && Toasts.live().get(0).presence() > 0f);
        Toasts.tick(Toasts.SLIDE / 2);
        check("gone once spent", Toasts.live().isEmpty());

        // Nothing is empty, and nothing is null.
        Toasts.add(null, Toasts.Kind.PLAIN);
        Toasts.add("", Toasts.Kind.PLAIN);
        check("nothing adds nothing", Toasts.live().isEmpty());

        // The same thing twice restarts rather than stacking.
        Toasts.add("same", Toasts.Kind.GOOD);
        Toasts.tick(1f);
        Toasts.add("same", Toasts.Kind.GOOD);
        check("a repeat does not stack", Toasts.live().size() == 1);
        check("and restarts the clock", Toasts.live().get(0).age == 0f);

        // Full drops the oldest, because what just happened is what is worth reading.
        Toasts.clear();
        for (int i = 0; i < Toasts.MOST + 3; i++) {
            Toasts.add("n" + i, Toasts.Kind.PLAIN);
        }
        check("never more than the cap", Toasts.live().size() == Toasts.MOST);
        check("the newest survived",
                Toasts.live().get(Toasts.MOST - 1).text.equals("n" + (Toasts.MOST + 2)));
        check("the oldest went", Toasts.live().stream().noneMatch(t -> t.text.equals("n0")));

        // No time passing must not age anything, or a paused game empties the screen.
        Toasts.clear();
        Toasts.add("held", Toasts.Kind.PLAIN);
        for (int i = 0; i < 100; i++) Toasts.tick(0f);
        check("no time is no ageing", Toasts.live().size() == 1
                && Toasts.live().get(0).age == 0f);
        // Negative time must not un-age it. Without the guard the age goes deeply negative
        // and the toast then outlives everything, which is the permanent-smudge failure
        // by another route.
        for (int i = 0; i < 100; i++) Toasts.tick(-1f);
        check("negative time is ignored too", Toasts.live().size() == 1);
        Toasts.tick(Toasts.LIFE);
        check("and cannot buy a toast extra life", Toasts.live().isEmpty());

        System.out.println(fails == 0 ? "OK" : fails + " FAILED");
    }
}
