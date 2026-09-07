"""The build guide: what a fake build still needs, and where to go next.

The point of the guide is to be followed, so the way it fails is by sending you somewhere.
Three mistakes all look like it working right up until you walk:

  * counting a block that is already standing as still missing, so the tally never finishes
  * pointing at a spot you have already filled
  * picking a different "nearest" every tick when two are the same distance, so the marker
    flickers between them and neither is reachable

All three are arithmetic over a list, so all three can be run here.

Run for real: no Minecraft in BuildGuide."""
import io, os, shutil, subprocess, sys, tempfile

SRC = "src/main/java/dev/skullzz/mirage/client/BuildGuide.java"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

source = io.open(SRC, encoding="utf-8").read()
check("the guide has no Minecraft in it", "net.minecraft" not in source)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK")
    sys.exit(0)

HARNESS = r'''
import dev.skullzz.mirage.client.BuildGuide;
import java.util.*;

public class GuideHarness {
    static int bad = 0;
    static void want(String what, boolean ok) {
        if (!ok && bad < 12) System.out.println("FAILED: " + what);
        if (!ok) bad++;
    }

    static BuildGuide.Spot at(int x, int y, int z, String block, boolean done) {
        return new BuildGuide.Spot(x, y, z, block, done);
    }

    public static void main(String[] args) {
        List<BuildGuide.Spot> spots = new ArrayList<>(List.of(
                at(0, 0, 0, "obsidian", true),
                at(1, 0, 0, "obsidian", false),
                at(2, 0, 0, "obsidian", false),
                at(0, 1, 0, "glass", false),
                at(9, 9, 9, "glass", true)));

        want("placed counts what is standing", BuildGuide.placed(spots) == 2);
        want("progress is placed over all",
                Math.abs(BuildGuide.progress(spots) - 0.4) < 1e-9);
        want("what is left excludes what is done",
                BuildGuide.remaining(spots).size() == 3);

        List<BuildGuide.Need> needs = BuildGuide.materials(spots);
        want("every block is tallied", needs.size() == 2);
        want("the one with most left comes first", needs.get(0).block.equals("obsidian"));
        want("wanted counts all of them", needs.get(0).wanted == 3);
        want("placed counts the standing one", needs.get(0).placed == 1);
        want("left is the difference", needs.get(0).left() == 2);
        want("and the other is tallied too",
                needs.get(1).block.equals("glass") && needs.get(1).left() == 1);

        // The nearest must never be one already standing.
        BuildGuide.Spot next = BuildGuide.nearest(spots, 0, 0, 0);
        want("the nearest is a spot still to fill", next != null && !next.done);
        want("and it is the actual nearest", next != null && next.x == 1 && next.y == 0);
        want("standing on a done spot does not point at it",
                BuildGuide.nearest(spots, 0, 0, 0).away(0, 0, 0) > 0);

        // A tie must resolve the same way every time, or the marker flickers.
        List<BuildGuide.Spot> tied = List.of(
                at(5, 0, 0, "stone", false),
                at(-5, 0, 0, "stone", false),
                at(0, 5, 0, "stone", false));
        String first = String.valueOf(BuildGuide.nearest(tied, 0, 0, 0));
        for (int i = 0; i < 200; i++) {
            want("a tie always picks the same spot",
                    String.valueOf(BuildGuide.nearest(tied, 0, 0, 0)).equals(first));
        }
        // And reordering the list must not change the answer either.
        List<BuildGuide.Spot> shuffled = new ArrayList<>(tied);
        Collections.reverse(shuffled);
        want("nor does the order it was given in",
                String.valueOf(BuildGuide.nearest(shuffled, 0, 0, 0)).equals(first));

        // A finished build has nothing to point at.
        List<BuildGuide.Spot> finished = List.of(at(0, 0, 0, "obsidian", true));
        want("a finished build has no nearest",
                BuildGuide.nearest(finished, 0, 0, 0) == null);
        want("and reads as finished", BuildGuide.progress(finished) == 1);
        want("an empty build is finished rather than divided by zero",
                BuildGuide.progress(List.of()) == 1);
        want("and has no nearest either",
                BuildGuide.nearest(List.of(), 0, 0, 0) == null);

        // The lines people actually read.
        List<String> lines = BuildGuide.lines("tower", spots, 0, 0, 0, 20);
        want("the lines say how far through", String.join("\n", lines).contains("2 of 5"));
        want("and name what is left", String.join("\n", lines).contains("obsidian"));
        want("and where to go", String.join("\n", lines).contains("Nearest:"));
        want("a finished build says so",
                String.join("\n", BuildGuide.lines("t", finished, 0, 0, 0, 20))
                        .contains("Finished"));
        want("an empty one says to stand a build up",
                String.join("\n", BuildGuide.lines("t", List.of(), 0, 0, 0, 20))
                        .contains("Nothing showing"));

        // A long material list is cut rather than flooding chat.
        List<BuildGuide.Spot> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) many.add(at(i, 0, 0, "block" + i, false));
        want("a huge material list is cut short",
                BuildGuide.lines("big", many, 0, 0, 0, 10).size() <= 13);

        want("a namespace is dropped",
                BuildGuide.shortName("minecraft:obsidian").equals("obsidian"));
        want("a bare name survives", BuildGuide.shortName("obsidian").equals("obsidian"));
        want("and nothing is nothing", BuildGuide.shortName(null).isEmpty());

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
'''

work = tempfile.mkdtemp(prefix="mirage-guide-")
try:
    harness = os.path.join(work, "GuideHarness.java")
    io.open(harness, "w", encoding="utf-8").write(HARNESS)
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, SRC, harness],
                           capture_output=True, text=True)
    check("it compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)
    run = subprocess.run(["java", "-cp", classes, "GuideHarness"],
                         capture_output=True, text=True)
    out = [l for l in run.stdout.splitlines() if not l.startswith("Picked up")]
    check("the guide never points at a block already placed", out and out[-1] == "OK")
    for line in out:
        if line.startswith("FAILED"): fails.append(line[8:])
finally:
    shutil.rmtree(work, ignore_errors=True)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("the tally finishes, the nearest is always still missing, and a tie picks the same "
      "spot however the list is ordered")
