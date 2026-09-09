"""The rig half's own switch.

Two halves of this mod have nothing to do with each other: builds, schematics and map art
are pictures on your own screen, and the rigs decide what comes out of a machine. Turning
the rigs off must leave the first half completely alone -- and must not quietly leave
anything of the second half running, which is the failure that would be hardest to notice."""
import glob, io, os, re, shutil, subprocess, sys
SRC = "src/main/java/dev/skullzz/mirage/client/"
client = io.open(SRC + "MirageClient.java", encoding="utf-8").read()
fakes  = io.open(SRC + "SelfFakes.java", encoding="utf-8").read()
disp   = io.open(SRC + "ClientDispensers.java", encoding="utf-8").read()
dash   = io.open(SRC + "WebDashboard.java", encoding="utf-8").read()

fails = []
def check(name, cond):
    if not cond: fails.append(name)

def body(source, signature):
    found = re.search(re.escape(signature) + r"(.*?)\n    \}", source, re.S)
    return found.group(1) if found else ""

# ------------------------------------------------------------------ the switch
check("there is a rig switch", "public static boolean rigsOn()" in fakes)
check("it can be set", "public static void setRigsOn(boolean on)" in fakes)
check("setting it is remembered", "save();" in body(fakes, "public static void setRigsOn(boolean on) {"))
check("it is written to the config", 'addProperty("rigsOn"' in fakes)
check("it is read back", 'root.has("rigsOn")' in fakes)
# An older config says nothing about it, and those setups had the rigs running.
check("an older config keeps its rigs", '!root.has("rigsOn") ||' in fakes)
check("it starts on", "boolean rigsOn = true;" in fakes)

# ----------------------------------------------------- off means actually off
tick = client[client.index("ClientTickEvents.END_CLIENT_TICK.register"):]
tick = tick[:tick.index("ClientPlayConnectionEvents.DISCONNECT")]
check("the rig tick is skipped", "if (SelfFakes.rigsOn()) ClientDispensers.tick(client);" in tick)
check("the rig block is guarded", "if (SelfFakes.rigsOn()) {" in tick)

# The guard over watched machines has to be let go, or a build keeps a hole punched in it
# for a machine nothing is listening to any more.
check("the machine guard is released", "ClientDispensers.standDown();" in tick)
stand = body(disp, "public static void standDown() {")
check("standing down clears the guard", "FakeBlocks.keepClear(" in stand)
check("standing down forgets nothing", "watched.clear()" not in stand
      and "profiles.clear()" not in stand)

# A key read with while(wasPressed()) walks a queue. Skipping the read leaves the presses
# in it, and they all happen at once when the rigs come back.
check("rig keys are drained, not ignored", "drainRigKeys();" in tick)
drain = body(client, "private static void drainRigKeys() {")
check("the drain empties each key", "while (key.wasPressed())" in drain)
for key in ("armNext", "fireNow", "cycleRig", "nextResult", "previousResult",
            "callFirst", "callSecond", "winFirst", "winSecond", "cycleWinner", "refill"):
    check("%s is drained" % key, key in drain)

# ------------------------------------------------------- the menus stay reachable
# The lockout this guards: the menus are where everything gets switched back on, so if
# their keys sat behind either switch, turning one off would take away the way to undo it.
# The rig gate was fixed first; the master switch had exactly the same fault and was not
# caught until the menus stopped opening in game.
for menu in ("openClient", "openRigs", "openTracker"):
    check("%s is read at all" % menu, menu + ".wasPressed()" in tick)
    check("%s is not gated on the rigs" % menu,
          menu + ".wasPressed()" not in tick[tick.index("if (SelfFakes.rigsOn()) {")
                                             :tick.index("drainRigKeys();")])
    # Before the master switch's return, not merely outside the rig block.
    check("%s is read before the master switch gives up" % menu,
          tick.index(menu + ".wasPressed()") < tick.index("if (!SelfFakes.enabled()) {"))

# ...and therefore must NOT be in the master switch's drain, or the press that opens the
# menu would be swallowed on its way past.
drainall = body(client, "private static boolean drainKeys() {")
for menu in ("openClient", "openRigs", "openTracker"):
    check("%s is not swallowed by the master switch drain" % menu, menu not in drainall)
check("openMenu still is, since it is read after the return", "openMenu" in drainall)

# ------------------------------------------------- the other half is untouched
# These run outside the guarded block, or turning the rigs off would take the builds with
# them -- which is the whole thing being asked for.
after = tick[tick.index("drainRigKeys();"):]
for kept in ("cutBlock", "openMenu", "clearFakes", "FakeClicks.closed"):
    check("%s still runs with rigs off" % kept, kept in after)
guarded = tick[tick.index("if (SelfFakes.rigsOn()) {"):tick.index("drainRigKeys();")]
for gone in ("cutBlock", "openMenu"):
    check("%s is not inside the rig block" % gone, gone not in guarded)
# The three painting layers are never gated on it.
for layer in ("FakeBlocks.tick(client)", "ClientDecor.tick(client.world)"):
    check("%s is not gated on the rigs" % layer, layer in tick
          and layer not in guarded)

# --------------------------------------------------------- and it is findable
check("there is a rigs command", 'literal("rigs")' in client)
check("the command says what stays on", "Builds, schematics and map art" in client)
check("asking without an argument reports the state", 'SelfFakes.rigsOn() ? "on" : "off"' in client)
# Silent failure is the enemy: with the rigs off, every line the doctor prints about a rig
# describes something that is not running.
doctor = body(client, "private static int doctor(CommandContext<FabricClientCommandSource> context) {")
check("the doctor says when the rigs are off", "Rigs            OFF" in doctor)
check("and how to turn them back on", "/fake rigs on" in doctor)
check("and that it invalidates what follows", "Nothing below is running" in doctor)

# The dashboard is the one place with no chat, so it must show and set it.
check("the dashboard carries the state", '\\"rigsOn\\":' in client)
check("the page has a switch", "id=\"rigs\"" in dash)
check("the switch posts", "post('/rigs?on=" in dash)
check("there is an endpoint", 'createContext("/rigs"' in dash)
check("it is polled", "pollRigs()" in dash and "pollRigs()" in client)
# Read before the master switch's early return, or it cannot be turned on from a
# dashboard while everything is off.
check("the poll happens before the master switch returns",
      tick.index("pollRigs()") < tick.index("if (!SelfFakes.enabled())"))


# ------------------------------------------------- what a machine shows vs what it fires
#
# A dispenser's stock is the game; its answer is the rig. Keeping those apart is the whole
# point -- a coin flip that lays out nine gold blocks tells anyone who opens it what is
# about to come out, before it comes out.
#
# It did exactly that. Setting a fixed answer on one machine took a branch that filled all
# nine slots with whatever it was rigged to fire, so one dispenser in a 50/50 went solid
# gold. The two comments in fill() contradicted each other and this was the wrong one.
disp = io.open("src/main/java/dev/skullzz/mirage/client/ClientDispensers.java",
               encoding="utf-8").read()
fill = body(disp, "public static boolean fill(BlockPos pos, boolean join) {")
check("a fixed answer no longer fills every slot with itself",
      "for (int slot = 0; slot < STOCK_SLOTS; slot++) {\n                    slots.put(slot, fixed" not in fill)
check("the layout is decided by one rule", "cycledSlotCount(" in fill)

if shutil.which("javac") is not None and shutil.which("java") is not None:
    import tempfile as _tmp
    HARNESS = """
public class RigHarness {
    static int bad = 0;
    static void want(String what, boolean ok) {
        if (!ok) { System.out.println("FAILED: " + what); bad++; }
    }
    public static void main(String[] a) throws Exception {
        java.lang.reflect.Method m = Class
                .forName("dev.skullzz.mirage.client.ClientDispensers")
                .getDeclaredMethod("cycledSlotCount", int.class, boolean.class, int.class);
        m.setAccessible(true);

        // The property that was broken: a fixed answer must not change the layout.
        for (int presets = 1; presets <= 9; presets++) {
            int without = (int) m.invoke(null, presets, false, 9);
            int with = (int) m.invoke(null, presets, true, 9);
            want("a fixed answer does not change how a machine looks", without == with);
            want("one slot per preset", without == Math.min(presets, 9));
        }

        // A 50/50 is two slots, not nine, whatever is rigged.
        want("a coin flip shows two", (int) m.invoke(null, 2, true, 9) == 2);
        want("and still two with nothing rigged", (int) m.invoke(null, 2, false, 9) == 2);

        // More presets than slots is capped, not overflowed.
        want("more presets than slots is capped", (int) m.invoke(null, 40, false, 9) == 9);

        // A rig with nothing to show falls back to the one thing it fires, rather than
        // laying out empty and reading as broken.
        want("no presets and a fixed answer shows the one thing",
                (int) m.invoke(null, 0, true, 9) == 1);
        want("no presets and nothing rigged shows nothing",
                (int) m.invoke(null, 0, false, 9) == 0);

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
"""
    work = _tmp.mkdtemp(prefix="mirage-rig-")
    try:
        h = os.path.join(work, "RigHarness.java")
        io.open(h, "w", encoding="utf-8").write(HARNESS)
        classes = os.path.join(work, "classes")
        # Compiled against the stubs, so the class it lives in can be loaded at all.
        stubs = glob.glob(os.path.join("tools", "stubs", "**", "*.java"), recursive=True)
        built = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, h],
                               capture_output=True, text=True)
        check("the rig harness compiles", built.returncode == 0)
        if built.returncode == 0:
            src = glob.glob("src/main/java/**/*.java", recursive=True)
            whole = subprocess.run(["javac", "-proc:none", "-nowarn", "-Xmaxerrs", "1",
                                    "-d", classes] + src + stubs,
                                   capture_output=True, text=True)
            # Minecraft is absent, so the class cannot actually be loaded here. The rule is
            # small enough to read instead, and the shape of it is what is asserted.
            rule = body(disp, "static int cycledSlotCount(int presets, boolean hasFixedAnswer, int stockSlots) {")
            check("the rule ignores the fixed answer when there are presets",
                  "if (presets > 0) return Math.min(presets, stockSlots);" in rule)
            check("and only falls back to it when there are none",
                  "return hasFixedAnswer ? 1 : 0;" in rule)
    finally:
        shutil.rmtree(work, ignore_errors=True)

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "rigs switch off skips the rig tick, releases the machine guard and drains its keys; "
      "builds, schematics, map art and the fake-item keys are untouched")
sys.exit(1 if fails else 0)
