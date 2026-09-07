"""The pointer trail and the click rings.

Both are lists of things with ages, and the mistake both invite is the same one: leaving
something in that should have aged out. A tail that never drops its oldest point becomes a
permanent smear across the menu; a ring that never expires means every click ever made is
still being drawn a thousand frames later. Neither looks wrong for the first few seconds,
which is exactly why they are asserted rather than eyeballed.

The rest is arithmetic that has to stay inside its own bounds -- life between 0 and 1, a
radius that only grows, a thickness of at least one pixel -- because a negative size or an
alpha over one is a drawing bug that shows up as something far stranger on screen than the
number that caused it.

Run for real: no Minecraft in RyneCursor, so it compiles and runs here."""
import io, os, shutil, subprocess, sys, tempfile

SRC = "src/main/java/dev/skullzz/mirage/client/RyneCursor.java"
DRAW = "src/main/java/dev/skullzz/mirage/client/RyneDraw.java"
SCREEN = "src/main/java/dev/skullzz/mirage/client/RyneClickScreen.java"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

source = io.open(SRC, encoding="utf-8").read()
draw = io.open(DRAW, encoding="utf-8").read()
screen = io.open(SCREEN, encoding="utf-8").read()

check("the cursor model has no Minecraft in it", "net.minecraft" not in source)

# A ring per click, not one per frame the button is held. The screen polls the button
# rather than taking a release event, so the edge has to be found before wasDown moves on.
check("a ring is thrown on the press edge only",
      "if (down && !this.wasDown" in screen
      and screen.index("if (down && !this.wasDown") < screen.index("this.wasDown = down;"))
check("the trail is drawn over the panels, not under them",
      "RyneDraw.cursor(" in screen
      and screen.index("for (RyneGui.Panel panel : GUI.panels()) paint(")
          < screen.index("RyneDraw.cursor("))
check("and can be turned off", "RyneCursor.on()" in screen)

# Drawn out of the same rectangle as everything else -- no new call to get wrong.
check("the effects use only the proven primitives",
      "box(context" in draw and "outline(context" in draw)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the model with")
    sys.exit(0)

HARNESS = r'''
import dev.skullzz.mirage.client.RyneCursor;

public class CursorHarness {
    static int bad = 0;

    static void want(String what, boolean ok) {
        if (!ok && bad < 10) System.out.println("FAILED: " + what);
        if (!ok) bad++;
    }

    public static void main(String[] args) {
        RyneCursor cursor = new RyneCursor();

        // The tail is bounded however far the pointer is dragged.
        for (int i = 0; i < 4000; i++) {
            cursor.move(i % 600, (i * 7) % 400);
            want("the tail never grows past its limit",
                    cursor.tail().size() <= RyneCursor.TAIL);
        }

        // A still pointer lays nothing down: the same point repeated stacks into a blob.
        cursor.clear();
        cursor.move(100, 100);
        int after = cursor.tail().size();
        for (int i = 0; i < 50; i++) cursor.move(100, 100);
        want("a still pointer lays down one point, not fifty",
                cursor.tail().size() == after);

        // Rings are bounded too, and every one of them expires.
        cursor.clear();
        for (int i = 0; i < 100; i++) cursor.click(50, 50);
        want("rings are capped", cursor.rings().size() <= RyneCursor.RINGS);
        for (int i = 0; i < 200; i++) cursor.tick(0.016f);
        want("every ring expires", cursor.rings().isEmpty());
        want("and so does the whole tail", cursor.tail().isEmpty());

        // A radius only ever grows, and stops at the reach. A ring that shrinks reads as
        // the click being undone.
        cursor.clear();
        cursor.click(10, 10);
        int last = -1;
        for (int i = 0; i < 60 && !cursor.rings().isEmpty(); i++) {
            RyneCursor.Ring ring = cursor.rings().get(0);
            int radius = ring.radius();
            want("the ring never shrinks", radius >= last);
            want("nor overshoots its reach", radius <= RyneCursor.RING_REACH);
            want("life stays between nothing and all of it",
                    ring.life() >= 0f && ring.life() <= 1f);
            last = radius;
            cursor.tick(0.016f);
        }

        // Life never goes negative, which would draw as a colour with a negative alpha.
        cursor.clear();
        cursor.move(5, 5);
        for (int i = 0; i < 40; i++) {
            for (RyneCursor.Point point : cursor.tail()) {
                want("point life stays in range", point.life() >= 0f && point.life() <= 1f);
            }
            cursor.tick(0.02f);
        }

        // A stall, a paused game or a first frame all hand over a nonsense delta. Acting
        // on one wipes the whole trail in a single frame.
        cursor.clear();
        cursor.move(1, 1);
        cursor.move(2, 2);
        int held = cursor.tail().size();
        cursor.tick(0f);
        cursor.tick(-5f);
        cursor.tick(900f);
        cursor.tick(Float.NaN);
        want("a nonsense delta changes nothing", cursor.tail().size() == held);

        // Thickness is at least a pixel: a zero-width rectangle draws nothing, so the
        // tail would simply vanish at its far end rather than tapering.
        for (int i = 0; i < RyneCursor.TAIL; i++) {
            for (float life = 0f; life <= 1f; life += 0.05f) {
                want("thickness is always at least a pixel",
                        RyneCursor.thickness(i, life) >= 1);
            }
        }

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
'''

work = tempfile.mkdtemp(prefix="mirage-cursor-")
try:
    harness = os.path.join(work, "CursorHarness.java")
    io.open(harness, "w", encoding="utf-8").write(HARNESS)
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, SRC, harness],
                           capture_output=True, text=True)
    check("the model compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "CursorHarness"],
                         capture_output=True, text=True)
    out = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    check("nothing outlives its age", out and out[-1] == "OK")
    for line in out:
        if line.startswith("FAILED"): fails.append(line[8:])
finally:
    shutil.rmtree(work, ignore_errors=True)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("the tail is bounded and expires, rings are capped and only grow, and a nonsense "
      "frame delta changes nothing")
