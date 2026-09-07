"""Setting text: widths, tracking, alignment.

Typography is the part of a menu that is easiest to get subtly wrong and hardest to
notice: a heading two pixels off does not look broken, it just looks cheap. And because
this mod may not ask Minecraft how wide a string is -- TextRenderer.getWidth has never
been watched compile here -- the widths are written down, which means they have to be
checked against themselves rather than against the game.

The properties that actually matter are arithmetic, so they can be run for real: tracking
goes between characters and never after the last one (a trailing gap pushes every
right-aligned column off by exactly one), a fitted string never exceeds its column, and
centring is stable.

Run for real: no Minecraft in RyneType, so it compiles and runs here."""
import io, os, shutil, subprocess, sys, tempfile

SRC = "src/main/java/dev/skullzz/mirage/client/RyneType.java"
DRAW = "src/main/java/dev/skullzz/mirage/client/RyneDraw.java"
HERE = os.path.dirname(os.path.abspath(__file__))

fails = []
def check(name, cond):
    if not cond: fails.append(name)

source = io.open(SRC, encoding="utf-8").read()
draw = io.open(DRAW, encoding="utf-8").read()

check("the type layer has no Minecraft in it", "net.minecraft" not in source)
# The name appears in the javadoc explaining why it is not used, so this looks for a
# call rather than the word.
check("nor asks the game how wide a string is", ".getWidth(" not in source)

# Headings are drawn one character at a time, which is only even for capitals. Tracking
# lowercase looks broken, so heading() must be the thing that uppercases.
check("headings are set in capitals", "RyneType.caps(message)" in draw)
check("and drawn character by character",
      "message.charAt(i)" in draw and "offsets[i]" in draw)
# Tracking of zero has to fall back to one call: drawing per-character with no gap loses
# the font's own kerning and looks worse than what it replaced.
check("no tracking means one ordinary call",
      "if (tracking <= 0) {" in draw and "text(context, renderer, message, x, y, colour);" in draw)
check("the divider fades rather than cutting the panel in half",
      "colour & 0x00FFFFFF" in draw)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the widths with")
    sys.exit(0)

HARNESS = r'''
import dev.skullzz.mirage.client.RyneType;

public class TypeHarness {
    static int bad = 0;

    static void want(String what, boolean ok) {
        if (!ok) { System.out.println("FAILED: " + what); bad++; }
    }

    public static void main(String[] args) {
        // Tracking goes between the characters, never after the last one. Getting this
        // wrong is invisible on the left and one tracking off on every right-aligned
        // column in the mod.
        want("no trailing gap", RyneType.width("AB", 2) == RyneType.width("AB", 0) + 2);
        want("three characters, two gaps",
                RyneType.width("ABC", 2) == RyneType.width("ABC", 0) + 4);
        want("one character has no gap at all",
                RyneType.width("A", 5) == RyneType.width("A", 0));
        want("nothing is nothing", RyneType.width("", 4) == 0);

        // Offsets and width have to agree, or a heading is centred to the wrong place.
        int[] offsets = RyneType.offsets("HELLO", 2);
        int last = offsets[4] + RyneType.advance('O');
        want("offsets end where width says", last == RyneType.width("HELLO", 2));
        want("the first character is at zero", offsets[0] == 0);

        // Right alignment is the whole reason widths are written down.
        want("right aligned text ends at the edge",
                RyneType.rightX(100, "ABC", 2) + RyneType.width("ABC", 2) == 100);
        want("centring leaves equal-ish margins",
                RyneType.centreX(0, 100, "ABC", 0) == (100 - RyneType.width("ABC", 0)) / 2);
        want("centring never starts left of the box",
                RyneType.centreX(10, 4, "A LONG HEADING", 2) >= 10);

        // A fitted string must never be wider than its column -- that is the only promise
        // it makes, and the one a caller relies on to stop columns colliding.
        String[] samples = {"", "i", "WWWWWWWWWWWWWWWW", "iiiiiiiiiiiiiiii",
                            "Skullzz paid you", "a b c d e f g h i j k"};
        for (String sample : samples) {
            for (int pixels = 0; pixels <= 80; pixels += 7) {
                for (int tracking = 0; tracking <= 2; tracking++) {
                    String cut = RyneType.fit(sample, pixels, tracking);
                    want("fit(" + sample + ", " + pixels + ") fits",
                            RyneType.width(cut, tracking) <= pixels);
                    want("fit keeps what already fits",
                            RyneType.width(sample, tracking) > pixels
                                    || cut.equals(sample));
                }
            }
        }

        // Narrow characters are the reason a character count is not a width.
        want("W and i are not the same width",
                RyneType.width("WWWW") != RyneType.width("iiii"));
        want("capitals are even, which is what makes tracking work",
                RyneType.advance('A') == RyneType.advance('Z')
                        && RyneType.advance('A') == RyneType.advance('0'));

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
'''

work = tempfile.mkdtemp(prefix="mirage-type-")
try:
    harness = os.path.join(work, "TypeHarness.java")
    io.open(harness, "w", encoding="utf-8").write(HARNESS)
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, SRC, harness],
                           capture_output=True, text=True)
    check("the type layer compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "TypeHarness"],
                         capture_output=True, text=True)
    out = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    check("every width property holds", out and out[-1] == "OK")
    for line in out:
        if line.startswith("FAILED"): fails.append(line[8:])
finally:
    shutil.rmtree(work, ignore_errors=True)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("headings are capitals spaced by hand, tracking never trails, and a fitted string "
      "never overruns its column")
