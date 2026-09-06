"""Reading money out of chat.

The tracker adds up what came in and what went out. It reads lines already on your screen
and does arithmetic on them -- so the only way it can be wrong is quietly: a suffix read
as the wrong scale, a decimal lost, or somebody else's chat message counted as yours.

Chat is written by other people. That is the case worth being strict about: without the
line being anchored, anyone typing "you paid Bob $10000000" in public chat lands in your
tally, and a tally strangers can write to is worse than no tally at all.

Run for real: the parser has no Minecraft in it, so it compiles and runs here."""
import io, json, os, re, shutil, subprocess, sys, tempfile

SRC = "src/main/java/dev/skullzz/mirage/client/Tracker.java"
HERE = os.path.dirname(os.path.abspath(__file__))

fails = []
def check(name, cond):
    if not cond: fails.append(name)

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the parser with")
    sys.exit(0)

source = io.open(SRC, encoding="utf-8").read()
check("the parser has no Minecraft in it", "net.minecraft" not in source)
check("nor Fabric", "net.fabricmc" not in source)
# Money in a double loses cents on the way past a million.
check("money is not held in floating point",
      "double cents" not in source and "float " not in source)
check("amounts are cents", "cents" in source)

SECTION = "§"
CASES = [
    # line,                                              expected ("-" for no match)
    ("Skullzz paid you $1,500",                          "IN Skullzz 150000"),
    ("You paid Skullzz $2.5M",                           "OUT Skullzz 250000000"),
    ("You have paid Alex $3b",                           "OUT Alex 300000000000"),
    (SECTION + "aNotch has paid you " + SECTION + "e$750K", "IN Notch 75000000"),
    ("[Trade] Bob paid you $10",                         "IN Bob 1000"),
    ("Skullzz paid you $1.50",                           "IN Skullzz 150"),
    ("Skullzz paid you $0.01",                           "IN Skullzz 1"),

    # Other wordings of the same thing, including the two that put the amount before the
    # name. Read the wrong way round, those hand back the amount as the player.
    ("Notch sent you $1k",                               "IN Notch 100000"),
    ("You received $750 from Alex",                      "IN Alex 75000"),
    ("You sent $300 to Bob",                             "OUT Bob 30000"),
    ("You have sent Bob $2.5M",                          "OUT Bob 250000000"),

    # DonutSMP writes a space between the dollar sign and the number. Seen in game as
    # "You paid earthlion965 $ 1" -- without the space allowed, none of these are payments.
    ("You paid earthlion965 $ 1",                        "OUT earthlion965 100"),
    ("earthlion965 paid you $ 1",                        "IN earthlion965 100"),
    ("You received $ 2,500 from Alex",                   "IN Alex 250000"),
    ("You sent $ 750K to Bob",                           "OUT Bob 75000000"),
    ("Notch sent you $ 1.5m",                            "IN Notch 150000000"),

    # A chat line is not the plain text it looks like. These are the same payment written
    # the ways a server with a custom font and a styled currency actually writes it -- and
    # every one of them failed while looking identical on screen and in a log file.
    ("You paid 6208 $\u00a01",                            "OUT 6208 100"),   # no-break space
    ("You paid 6208 $\u200b1",                            "OUT 6208 100"),   # zero-width space
    ("You paid 6208 \ue000 1",                            "OUT 6208 100"),   # custom-font glyph
    ("You paid 6208 \u20ac 1",                            "OUT 6208 100"),   # not a dollar sign
    ("You\u00a0paid\u00a06208\u00a0$\u00a01",              "OUT 6208 100"),   # no plain space at all
    ("\u200fYou paid 6208 $ 1",                           "OUT 6208 100"),   # leading bidi mark
    ("You  paid   6208   $   1",                         "OUT 6208 100"),   # collapsed runs

    # Flattening must not manufacture a payment out of somebody else's chat message.
    ("<Griefer>\u00a0you paid Bob $ 999",                 "-"),
    ("\ue000 you paid Bob $ 999",                         "-"),

    # Written by other people. None of these may land in the tally.
    ("Someone whispered: you paid Bob $10 for it",       "-"),
    ("<Griefer> you paid Bob $999999999",                "-"),
    ("Griefer: Skullzz paid you $50000000",              "-"),
    ("lol you paid Bob $10",                             "-"),

    # Not payments, or not numbers.
    ("hello there",                                      "-"),
    ("Bob paid you $0",                                  "-"),
    ("Bob paid you $1.2.3",                              "-"),
    ("Bob paid you $",                                   "-"),
    ("You paid Bob",                                     "-"),
]

work = tempfile.mkdtemp(prefix="mirage-tracker-")
try:
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes, SRC,
                            os.path.join(HERE, "tracker", "Harness.java")],
                           capture_output=True, text=True)
    check("the parser compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "Harness"] + [c[0] for c in CASES],
                         capture_output=True, text=True)
    got = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    check("every line got an answer", len(got) == len(CASES))

    for (line, want), answer in zip(CASES, got):
        check("%r -> %s (got %s)" % (line, want, answer), answer == want)
finally:
    shutil.rmtree(work, ignore_errors=True)

# The direction has to be decided before the amount is: a line containing both shapes read
# the wrong way turns money out into money in, which is worse than missing it.
check("money out is tested for first",
      source.index("match(clean, out,") < source.index("match(clean, in,"))

# Every wording, default or configured, must be anchored: chat is written by other people,
# and without the anchor anyone typing "you paid Bob $10000000" lands in the tally.
for name in ("DEFAULT_IN", "DEFAULT_OUT"):
    block = re.search(r"List<String> %s = List\.of\((.*?)\);" % name, source, re.S)
    check("%s exists" % name, block is not None)
    if block:
        wordings = re.findall(r'"(.*?)"', block.group(1))
        check("%s has more than one wording" % name, len(wordings) >= 2)
        check("every %s wording is anchored" % name,
              all(w.startswith("^") for w in wordings))

# The order of player and amount differs between wordings, so it is read off the pattern
# rather than assumed -- assuming one order reads the amount as the player's name.
check("the order is taken from the pattern", "playerFirst(" in source)
check("a bad pattern costs one wording, not all",
      "catch (RuntimeException bad)" in source and "lastBad" in source)
check("the wordings can be replaced without a rebuild", "setPatterns(" in source)

# ------------------------------------------------------- the rest of the chain
sess = io.open("src/main/java/dev/skullzz/mirage/client/Sessions.java",
               encoding="utf-8").read()
hook = io.open("src/main/java/dev/skullzz/mirage/client/ChatHook.java",
               encoding="utf-8").read()
client = io.open("src/main/java/dev/skullzz/mirage/client/MirageClient.java",
                 encoding="utf-8").read()

def body(source, signature):
    import re as _re
    found = _re.search(_re.escape(signature) + r"(.*?)\n    \}", source, _re.S)
    return found.group(1) if found else ""

# Nothing counts by accident. A tally that starts itself is a tally you cannot trust the
# start of, and this one reads chat, which should be a decision.
check("tracking starts off", "boolean tracking = false;" in sess)
check("the HUD starts off", "boolean hud = false;" in sess)
offer = body(sess, "public static Tracker.Payment offer(String line) {")
check("nothing is read while tracking is off", "if (!tracking) return null;" in offer)
# This check used to read `"if (current != null)" in offer` -- it asserted the bug. A
# payment that parsed with no session running was dropped on the floor: the tally read
# zero and nothing was said, which is indistinguishable from a payment that never parsed.
# Money moving is what a session is, so the first payment starts one.
check("a parsed payment is always counted",
      "current.payments.add(payment);" in offer and "if (current != null) {" not in offer)
check("a payment with no session running starts one",
      "if (current == null) start();" in offer
      and offer.index("if (current == null) start();")
          < offer.index("current.payments.add(payment);"))

# The alert is once per run. One that repeats is one that gets ignored, and it is still
# the same run.
streak = body(sess, "private static void checkStreak() {")
check("the alert fires once per run", "run > alertedAt" in streak)
check("and resets when the run breaks", "if (run == 0)" in streak)

# The hook is looked up, never named at compile time -- a wrong guess there would be a
# build that does not compile, which is the mistake this project has made most.
# The subscribing itself moved into Events, which is where both of its bugs were fixed;
# check-events.py runs that against a mock. What matters here is that the chat hook still
# goes through it rather than growing its own copy.
events = io.open("src/main/java/dev/skullzz/mirage/client/Events.java",
                 encoding="utf-8").read()
check("the chat hook subscribes through Events", "Events.subscribe(" in hook)
check("and does not roll its own", "Proxy.newProxyInstance" not in hook)
check("the chat class is named as data, not imported", "EVENT_CLASSES" in hook
      and "net.fabricmc" not in hook.split("EVENT_CLASSES")[0])
check("the shared subscriber proxies the callback", "Proxy.newProxyInstance" in events)
check("the message is found by asking, not by position", 'getMethod("getString")' in hook)
check("a filter callback is answered harmlessly", "return true;" in hook)
check("failing to hook is remembered", "reason =" in hook)
check("every field tried is reported", "tried.append" in hook)
check("and never throws", "catch (ReflectiveOperationException | RuntimeException" in hook)

# Silence is the enemy: a zero that means "nothing happened" looks exactly like a zero
# that means "nothing was heard".
status = body(client, "private static int trackStatus(CommandContext<FabricClientCommandSource> context) {")
# "Chat" also appears inside ChatHook.attached(), so ordering by that word alone passed
# even with the whole line deleted. The marker is what has to be there.
check("status says loudly when chat is not being read",
      "NOT READING" in status and "ChatHook.reason()" in status)
check("and says it before anything about the tally",
      "NOT READING" in status
      and status.index("NOT READING") < status.index("Session"))
# The config used to be written with the defaults baked into it, and read back at startup
# over whatever the running jar knew. That froze the wordings at whatever build first wrote
# the file: four fixes in a row shipped, installed, and were silently overwritten on load.
save = body(sess, "public static void save() {")
check("the defaults are not written into the config",
      'root.add("paymentIn", array(patternsIn));' in save
      and "if (!patternsIn.equals(Tracker.DEFAULT_IN))" in save)
check("nor the outgoing ones",
      "if (!patternsOut.equals(Tracker.DEFAULT_OUT))" in save)
check("but the file still says the keys exist", '"_help"' in save)

# Every config written before that change still has a frozen copy in it, so loading has to
# undo it -- by asking whether the wordings work, not by listing every set ever shipped.
heal = body(sess, "private static void healPatterns() {")
check("wordings from the file are checked against the parser", "Tracker.selfTest()" in heal)
check("and replaced by the mod's own when they cannot read a payment",
      "Tracker.setPatterns(patternsIn, patternsOut);" in heal
      and "patternsIn = new ArrayList<>(Tracker.DEFAULT_IN);" in heal)
check("a set that works is left alone", '"OK".equals(verdict)' in heal and "return;" in heal)
check("and a replaced set is kept, not deleted",
      "rejectedIn = patternsIn;" in heal and "rejectedOut = patternsOut;" in heal)
check("replacing them is said out loud", "patternNotice =" in heal)
check("healing runs on load", "healPatterns();" in sess)
check("and the status shows the notice", "Sessions.patternNotice()" in client)

# A jar that was never rebuilt and a fix that did not work look identical from inside the
# game. Three rounds of this went by without a way to tell them apart, so the status runs
# the parser over lines whose answers are known and says so before anything else.
check("status proves the parser in this build works", "Tracker.selfTest()" in status)
check("and says it before anything about the tally",
      status.index("selfTest") < status.index("Session"))
check("the self-test covers each wording fix that has caught us out",
      all(w in source for w in ("$ 1", "\\u00a0", "\\ue000")))
check("and that somebody else's chat still does not count",
      "<Griefer> you paid Bob $999" in source)

check("status says how to switch tracking on",
      "/fake track on" in status)
check("and does not send you after a session switch that no longer exists",
      "/fake track start" not in status)
check("turning it on says if chat cannot be read", "ChatHook.attached()" in client)

# Counting nothing and a quiet night look identical from the outside, so there has to be a
# way to see the lines as they really arrived rather than guessing the wording a third time.
offer = body(sess, "public static Tracker.Payment offer(String line) {")
check("chat can be captured", "setCapturing(" in sess)
check("captured before the tracking switch, not after",
      "capturing" in offer and offer.index("capturing") < offer.index("if (!tracking)"))
check("the lines are written out", "writeRaw()" in sess)
check("and marked with what was made of them", "[+]" in client and "[ ]" in client)
check("the status says where the wordings live", "paymentIn / paymentOut" in client)
check("and points at the capture when nothing counts", "/fake track raw" in client)
check("an empty list in the file falls back to the defaults",
      "out.isEmpty() ? new ArrayList<>(fallback)" in sess)

for sub in ("on", "off", "start", "end", "rake", "alert", "raw", "lines"):
    check("there is a track %s command" % sub, 'literal("%s")' % sub in client)

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "%d chat lines parsed exactly, including %d written by other people that must not "
      "count; nothing counts until tracking is on, a parsed payment is never dropped for want of a session, and a chat hook that fails says so"
      % (len(CASES), sum(1 for _, want in CASES if want == "-")))
sys.exit(1 if fails else 0)

