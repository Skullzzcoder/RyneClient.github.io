"""The three called games: the race, high-low, and odd-even.

Each game is written as two halves that must agree -- one that draws a round to a named
winner, and one that reads a round back and says who took it. Neither knows about the
other, which is the whole point: the test is that they round-trip, over every combination
of call, named winner and bound, many times each because the draws are random.

That property is the one that matters. A rig whose scoreboard quietly disagrees with what
came out of the machine is worse than no rig at all -- it looks like it is working right
up until the round it costs you, and there is nothing on screen to say which round that
was. Every branch here is a chance for the two halves to drift apart: a "low" call read
as "below yours" in one half and "not above yours" in the other differs only on ties, and
only sometimes.

Run for real: no Minecraft in Games, so it compiles and runs here."""
import io, os, shutil, subprocess, sys, tempfile

SRC = "src/main/java/dev/skullzz/mirage/client/Games.java"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

source = io.open(SRC, encoding="utf-8").read()
check("the rules have no Minecraft in them", "net.minecraft" not in source)

# Minecraft has no bronze horse armour. Asking for one and silently getting nothing is how
# a lane ends up never firing.
check("bronze is mapped to the armour that exists",
      '"bronze"' in source and "iron_horse_armor" in source)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the rules with")
    sys.exit(0)

HARNESS = r'''
import dev.skullzz.mirage.client.Games;
import java.util.*;

public class GamesHarness {
    static int bad = 0;

    static void want(String what, boolean ok) {
        if (!ok && bad < 12) System.out.println("FAILED: " + what);
        if (!ok) bad++;
    }

    public static void main(String[] args) {
        Random random = new Random(20260907L);

        // ---- the race -------------------------------------------------------------
        for (int run = 0; run < 4000; run++) {
            for (String lane : Games.LANES) {
                List<String> order = Games.race(lane, random);
                want("race to " + lane + " is won by " + lane,
                        lane.equals(Games.raceWinner(order)));
                // Every piece comes out exactly once: three lanes, three each.
                want("race deals every piece",
                        order.size() == Games.LANES.size() * Games.RACE_LENGTH);
                for (String each : Games.LANES) {
                    int seen = 0;
                    for (String piece : order) if (piece.equals(each)) seen++;
                    want("race deals " + each + " three times", seen == Games.RACE_LENGTH);
                }
            }
            // An unnamed winner still has to produce a real race with a real winner.
            List<String> loose = Games.race("", random);
            want("an unrigged race still has a winner",
                    Games.LANES.contains(Games.raceWinner(loose)));
        }

        // A race that is over before it starts is not a race. The losers must get pieces
        // in among the winner's, or the rig is readable across the room.
        int close = 0;
        for (int run = 0; run < 2000; run++) {
            List<String> order = Games.race("diamond", random);
            // Position of the winner's last piece: if it were always third, every race
            // would be diamond-diamond-diamond and nobody would play twice.
            int seen = 0, at = -1;
            for (int i = 0; i < order.size(); i++) {
                if (order.get(i).equals("diamond") && ++seen == Games.RACE_LENGTH) {
                    at = i; break;
                }
            }
            if (at > Games.RACE_LENGTH - 1) close++;
        }
        want("a rigged race does not just fire the winner three times first", close > 1500);

        // ---- high or low ----------------------------------------------------------
        for (int most = 2; most <= 12; most++) {
            for (String call : new String[] {"high", "low", "HIGH", "Low", ""}) {
                for (int t = 0; t < 2; t++) {
                    boolean theyWin = t == 1;
                    for (int run = 0; run < 400; run++) {
                        int[] pair = Games.highLow(call, theyWin, most, random);
                        want("highLow(" + call + ", " + theyWin + ") agrees with itself",
                                Games.highLowTheyWin(call, pair[0], pair[1]) == theyWin);
                        want("highLow stays inside 1.." + most,
                                pair[0] >= 1 && pair[0] <= Math.max(2, most)
                                        && pair[1] >= 1 && pair[1] <= Math.max(2, most));
                    }
                }
            }
        }
        // A tie is a loss for the caller, so it must never turn up in a round they win.
        for (int run = 0; run < 4000; run++) {
            int[] pair = Games.highLow("high", true, 9, random);
            want("a round the caller wins is never a tie", pair[0] != pair[1]);
        }

        // ---- odd or even ----------------------------------------------------------
        for (int most = 2; most <= 12; most++) {
            for (String call : new String[] {"odd", "even", "ODD", "Even", ""}) {
                for (int t = 0; t < 2; t++) {
                    boolean theyWin = t == 1;
                    for (int run = 0; run < 400; run++) {
                        int number = Games.oddEven(call, theyWin, most, random);
                        want("oddEven(" + call + ", " + theyWin + ") agrees with itself",
                                Games.oddEvenTheyWin(call, number) == theyWin);
                        want("oddEven stays inside 1.." + most,
                                number >= 1 && number <= Math.max(2, most));
                    }
                }
            }
        }

        // Every number the game says it can deal has to actually turn up. Parity and range
        // both hold while the top number is quietly unreachable -- and a side whose nine
        // never once comes out over an evening is a rig somebody reads for free.
        for (int most = 2; most <= 12; most++) {
            for (String call : new String[] {"odd", "even"}) {
                for (int t = 0; t < 2; t++) {
                    boolean theyWin = t == 1;
                    Set<Integer> seen = new HashSet<>();
                    for (int run = 0; run < 3000; run++) {
                        seen.add(Games.oddEven(call, theyWin, most, random));
                    }
                    boolean wantOdd = call.equals("odd") == theyWin;
                    for (int n = wantOdd ? 1 : 2; n <= most; n += 2) {
                        want("oddEven(" + call + ", " + theyWin + ", " + most
                                + ") can deal " + n, seen.contains(n));
                    }
                }
            }
        }
        for (int most = 2; most <= 12; most++) {
            Set<Integer> seen = new HashSet<>();
            for (int run = 0; run < 4000; run++) {
                int[] pair = Games.highLow("high", true, most, random);
                seen.add(pair[0]);
                seen.add(pair[1]);
            }
            for (int n = 1; n <= most; n++) {
                want("highLow can deal " + n + " out of " + most, seen.contains(n));
            }
        }

        // ---- the lanes ------------------------------------------------------------
        want("bronze is a lane", Games.isLane("bronze"));
        want("bronze is iron armour",
                Games.armourFor("bronze").equals("iron_horse_armor"));
        want("gold and golden are the same lane",
                Games.armourFor("gold").equals(Games.armourFor("golden")));
        want("nonsense is not a lane", !Games.isLane("cheese"));
        want("nor is nothing", !Games.isLane(""));

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
'''

work = tempfile.mkdtemp(prefix="mirage-games-")
try:
    harness = os.path.join(work, "GamesHarness.java")
    io.open(harness, "w", encoding="utf-8").write(HARNESS)
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, SRC, harness],
                           capture_output=True, text=True)
    check("the rules compile with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "GamesHarness"],
                         capture_output=True, text=True)
    out = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    check("every round resolves to the winner it was drawn for", out and out[-1] == "OK")
    for line in out:
        if line.startswith("FAILED"): fails.append(line[8:])
finally:
    shutil.rmtree(work, ignore_errors=True)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("the race, high-low and odd-even each draw and read back the same result across "
      "every call and bound, and a rigged race still looks like a race")
