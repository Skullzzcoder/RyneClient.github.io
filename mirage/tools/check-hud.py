"""Waypoints, the HUD, and the payment export.

The compass is the part worth running: a bearing with one sign wrong points at the mirror
image of where you meant, which looks entirely plausible until you have walked the wrong
way for two minutes. Minecraft's yaw is its own convention -- 0 is south, and it increases
toward west -- so the maths is kept free of Minecraft and run against known answers.
The first run of it had the whole compass rotated by 180 degrees."""
import io, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = "src/main/java/dev/skullzz/mirage/client/"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

def body(source, signature):
    found = re.search(re.escape(signature) + r"(.*?)\n    \}", source, re.S)
    return found.group(1) if found else ""

compass = io.open(SRC + "Compass.java", encoding="utf-8").read()
hud = io.open(SRC + "Hud.java", encoding="utf-8").read()
way = io.open(SRC + "Waypoints.java", encoding="utf-8").read()
sess = io.open(SRC + "Sessions.java", encoding="utf-8").read()
client = io.open(SRC + "MirageClient.java", encoding="utf-8").read()
editor = io.open(SRC + "RyneHudScreen.java", encoding="utf-8").read()

check("the compass has no Minecraft in it", "net.minecraft" not in compass)
check("Minecraft's yaw convention is written down, not assumed",
      "0 faces positive Z" in compass or "0 faces positive Z (south)" in compass)

# ------------------------------------------------------------------ run the maths
if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the compass with")
    sys.exit(0)

work = tempfile.mkdtemp(prefix="mirage-hud-")
try:
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes,
                            SRC + "Compass.java",
                            os.path.join(HERE, "compass", "Harness.java")],
                           capture_output=True, text=True)
    check("the compass compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1200])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "Harness"], capture_output=True, text=True)
    lines = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    for line in lines:
        if line.startswith("FAIL "):
            fails.append("compass: " + line[5:])
    check("the compass harness ran", run.returncode == 0 and lines and lines[-1] == "OK")
finally:
    shutil.rmtree(work, ignore_errors=True)

# ------------------------------------------------------------------- the waypoints
check("a waypoint remembers its world", "public final String world;" in way)
# Kept the way builds are: a shop on one server is not a bearing on another.
# Scoped to the method: "this.world == null" is still present in a version that has been
# short-circuited to true, so asking whether the words appear passed on a broken one.
belongs = body(way, "public boolean here(String currentWorld) {")
check("and only shows there", belongs != ""
      and "this.world.equals(currentWorld)" in belongs)
check("nothing short-circuits it open", "true ||" not in belongs)
check("one saved before worlds existed still shows",
      "this.world == null" in belongs and "this.world.isEmpty()" in belongs)
check("the compass asks for this world only", "Waypoints.here(world)" in hud)
check("a name means one place", "remove(name);" in body(way,
      "public static Mark add(String name, int x, int y, int z, String colour, String world) {"))

# --------------------------------------------------------------------- the HUD
check("the HUD subscribes through Events", "Events.subscribe(" in hud)
check("and never throws into the render loop",
      "catch (RuntimeException failure)" in hud and "stumbled drawing the HUD" in hud)
check("it hides behind a menu", "client.currentScreen != null" in hud)
check("except while being arranged", "!editing &&" in hud)
check("the master switch reaches it", "!SelfFakes.enabled()" in hud)
# Behind you does not belong on a strip that reads left to right as what you can see.
check("only what is ahead is on the compass",
      "Integer.MIN_VALUE" in hud and "continue;" in body(hud,
      "private static void compass(DrawContext context, MinecraftClient client, Element element,"))

# Two switches for one bar is two ways to be wrong.
check("the bar's two switches are kept in step",
      'Hud.byId("tracker")' in sess and "bar.on = on;" in sess)
check("and from the other side too", 'id.equals("tracker")' in editor
      or 'id.equals("tracker")' in io.open(SRC + "RyneClickScreen.java", encoding="utf-8").read())

# An element you cannot see is one you cannot switch on.
check("the editor draws switched-off elements", "Hud.paint(context, true)" in editor)
check("and says which they are", '" (off)"' in editor or "(off)" in editor)
check("the editor tells a click from a drag", "RyneGui.SLOP" in editor)
check("the editor uses the shared clamp", "RyneGui.clampX(" in editor)

# --------------------------------------------------------------------- the export
export = body(sess, "public static Path export() {")
check("there is an export", export != "")
# A player name is not guaranteed to be free of the one character that splits a row.
check("fields are quoted", "quote(payment.player)" in export)
JAVA_QUOTE = 'replace(' + chr(34) + chr(92) + chr(34) + chr(34) + ', ' \
             + chr(34) + chr(92) + chr(34) + chr(92) + chr(34) + chr(34) + ')'
check("quoting doubles a quote", JAVA_QUOTE in sess)
# A spreadsheet should be handed a number, not a piece of text.
check("amounts are plain numbers", "payment.cents / 100" in export)
check("nothing to write is said, not written", "No sessions to write" in export)
check("a failure keeps its reason", "lastExport = failure.toString();" in sess)

for command in ("hud", "wp", "export"):
    check("there is a %s command" % command, 'literal("%s")' % command in client)

# ------------------------------------------------- switched on means visible
# Turning tracking on with the bar switched off produced no visible change whatever,
# which from the outside is indistinguishable from the tracker not working.
check("the tracker bar is on by default",
      'new Element("tracker", "Tracker bar", 0, 4, true)' in hud)
turn_on = body(sess, "public static void setTracking(boolean on) {")
check("turning tracking on turns the bar on", 'Hud.byId("tracker")' in turn_on
      and "bar.on = true;" in turn_on)
# ...but only when switching on: this must not undo a bar deliberately switched off.
check("and only when switching on", "if (on) {" in turn_on)

# Nothing on screen about a feature that is off.
line = body(hud, "static String trackerLine() {")
check("the bar says nothing while the tracker is off",
      line.index("Sessions.tracking()") < line.index("ChatHook.attached()"))

# A tracker showing nothing and a HUD that cannot draw look identical and need opposite
# fixes, so there has to be a way to tell them apart from inside the game.
check("there is a way to prove the HUD can draw", "Hud.test(" in client
      and 'literal("test")' in client)
check("the test refuses when the hook never attached", "cannot draw at all" in client)
check("the test says what is switched on", "then click one" in client)
check("the tracker status leads with both hooks",
      "CANNOT DRAW" in client and "NOT READING" in client)

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "the compass agrees with Minecraft's yaw on every cardinal and both wrap cases; "
      "waypoints are per world, the HUD shares one clamp, and the export is quoted")
sys.exit(1 if fails else 0)
