"""Notices that appear, sit, and stop existing.

Two failures here are invisible until they are permanent. A toast that never expires is a
smudge on the screen for the rest of the session. A fade tied to the frame rate looks
right on the machine it was written on and crawls on a slower one. Both are timing, so the
timing has no Minecraft in it and is run."""
import io, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = "src/main/java/dev/skullzz/mirage/client/"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

toasts = io.open(SRC + "Toasts.java", encoding="utf-8").read()
hud = io.open(SRC + "Hud.java", encoding="utf-8").read()
sess = io.open(SRC + "Sessions.java", encoding="utf-8").read()
disp = io.open(SRC + "ClientDispensers.java", encoding="utf-8").read()

check("the timing has no Minecraft in it", "net.minecraft" not in toasts)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the timing with")
    sys.exit(0)

work = tempfile.mkdtemp(prefix="mirage-toast-")
try:
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes,
                            SRC + "Toasts.java",
                            os.path.join(HERE, "toast", "Harness.java")],
                           capture_output=True, text=True)
    check("it compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1200])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "Harness"], capture_output=True, text=True)
    lines = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    for line in lines:
        if line.startswith("FAIL "):
            fails.append("toasts: " + line[5:])
    check("the timing harness ran", run.returncode == 0 and lines and lines[-1] == "OK")
finally:
    shutil.rmtree(work, ignore_errors=True)

# Aged in real seconds, from the one place that runs once a frame.
check("aged in seconds, not ticks", "Toasts.tick(seconds)" in hud
      and "System.nanoTime()" in hud)
check("a long frame cannot skip a whole toast", "Math.min(0.1f" in hud)
check("and the editor does not age them", "if (!editing) Toasts.tick" in hud)

# One place feeds them, so a notice worth logging is a notice worth seeing.
check("every dispenser notice becomes one", "Toasts.add(" in disp)
check("payments become one", "Toasts.add(" in sess)
check("in and out look different", "Toasts.Kind.GOOD" in sess and "Toasts.Kind.BAD" in sess)
check("a losing run is a warning", "Toasts.Kind.WARN" in sess)

# The stack must not reorder itself as it grows.
draw = re.search(r"private static void toasts\(DrawContext context, MinecraftClient client, "
                 r"Element element,\n\s*boolean editing\) \{(.*?)\n    \}", hud, re.S)
check("the stack is drawn in order", draw is not None
      and "for (Toasts.Toast toast : live)" in draw.group(1))
check("a spent toast is not drawn", draw is not None
      and "if (presence <= 0f) continue;" in draw.group(1))

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "toasts slide in, sit, fade and stop existing; a repeat restarts rather than stacks, "
      "a full screen drops the oldest, and no time passing ages nothing")
sys.exit(1 if fails else 0)
