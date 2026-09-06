import dev.skullzz.mirage.client.Tracker;

public class Harness {
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--self-test")) {
            System.out.println(Tracker.selfTest());
            return;
        }
        for (String line : args) {
            Tracker.Payment p = Tracker.read(line, 0L);
            System.out.println(p == null ? "-" : (p.incoming ? "IN" : "OUT")
                    + " " + p.player + " " + p.cents);
        }
    }
}
