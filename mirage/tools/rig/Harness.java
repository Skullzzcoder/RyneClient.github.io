import dev.skullzz.mirage.client.RigQueue;
import java.util.List;

public class Harness {
    static int fails = 0;
    static void check(String what, boolean ok) {
        if (!ok) { System.out.println("FAIL " + what); fails++; }
    }

    public static void main(String[] argv) {
        RigQueue q = new RigQueue();
        check("starts empty", q.isEmpty() && q.peek() == null && q.take() == null);

        check("adds one", q.add("diamond", 1) == 1);
        check("adds several", q.add("emerald", 3) == 3);
        check("size counts them all", q.size() == 4);

        // Looking must never change it: the preview asks several times a second.
        check("peek does not consume", q.peek().equals("diamond") && q.size() == 4);
        check("peeking again is the same", q.peek().equals("diamond") && q.size() == 4);
        for (int i = 0; i < 50; i++) q.peek();
        check("fifty looks change nothing", q.size() == 4);

        // The plan runs past the end honestly.
        List<String> plan = q.plan(6, "cycled");
        check("the plan starts with the queue", plan.get(0).equals("diamond")
                && plan.get(1).equals("emerald"));
        check("and says what happens after it", plan.get(4).equals("cycled")
                && plan.get(5).equals("cycled"));
        check("planning does not consume", q.size() == 4);

        check("describe counts runs", q.describe().equals("diamond, 3x emerald"));

        check("take consumes", q.take().equals("diamond") && q.size() == 3);
        check("and moves on", q.peek().equals("emerald"));
        check("put back goes to the front", true);
        q.putBack("gold");
        check("put back is next", q.peek().equals("gold") && q.size() == 4);

        // Rubbish in is nothing added, rather than an entry nothing can match.
        check("null adds nothing", q.add(null, 3) == 0);
        check("blank adds nothing", q.add("   ", 3) == 0);
        check("entries are trimmed", new RigQueue().add(" x ", 1) == 1);
        RigQueue t = new RigQueue();
        t.add("  spaced  ", 1);
        check("and stored trimmed", t.peek().equals("spaced"));

        // A count of zero or less still means one, since asking for a thing means one.
        RigQueue z = new RigQueue();
        check("zero still adds one", z.add("a", 0) == 1);
        check("negative still adds one", z.add("b", -5) == 1);

        // Full is full, and says how many it actually took.
        RigQueue full = new RigQueue();
        check("fills to the cap", full.add("a", 1000) == RigQueue.MOST);
        check("and stops there", full.size() == RigQueue.MOST);
        check("adding more adds none", full.add("b", 5) == 0);
        full.putBack("c");
        check("put back on a full queue does not overflow", full.size() == RigQueue.MOST);

        check("clear empties it", true);
        q.clear();
        check("cleared is empty", q.isEmpty() && q.describe().equals("empty"));

        RigQueue copied = new RigQueue();
        copied.copyFrom(List.of("a", "b", "b"));
        check("copies in", copied.describe().equals("a, 2x b"));
        copied.copyFrom(null);
        check("copying nothing empties it", copied.isEmpty());

        System.out.println(fails == 0 ? "OK" : fails + " FAILED");
    }
}
