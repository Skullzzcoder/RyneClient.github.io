"""The result queue: a run set up in advance, and spent exactly once.

The mistake this guards is quiet and total. Looking at what comes next happens constantly
-- the preview, the rig menu, the doctor and the dashboard all ask, several times a second
between them. If looking consumed an entry, the queue would empty itself without a single
machine firing, and the only symptom would be a rig that did not do what you told it.

So peeking and taking are different calls, and only a machine actually firing may take.

The other half is per game: a paper round is decided once however many machines fire in
it, so an entry is spent at the start of a round rather than per fire, or a two-sided
table burns two entries a round."""
import io, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = "src/main/java/dev/skullzz/mirage/client/"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

def body(source, signature):
    found = re.search(re.escape(signature) + r"(.*?)\n    \}", source, re.S)
    return found.group(1) if found else ""

queue = io.open(SRC + "RigQueue.java", encoding="utf-8").read()
rig = io.open(SRC + "RigProfile.java", encoding="utf-8").read()
disp = io.open(SRC + "ClientDispensers.java", encoding="utf-8").read()
client = io.open(SRC + "MirageClient.java", encoding="utf-8").read()

check("the queue has no Minecraft in it", "net.minecraft" not in queue)

# ------------------------------------------------------------------- run it
if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run the queue with")
    sys.exit(0)

work = tempfile.mkdtemp(prefix="mirage-queue-")
try:
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes,
                            SRC + "RigQueue.java",
                            os.path.join(HERE, "rig", "Harness.java")],
                           capture_output=True, text=True)
    check("it compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1200])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "Harness"], capture_output=True, text=True)
    lines = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    for line in lines:
        if line.startswith("FAIL "):
            fails.append("queue: " + line[5:])
    check("the queue harness ran", run.returncode == 0 and lines and lines[-1] == "OK")
finally:
    shutil.rmtree(work, ignore_errors=True)

# --------------------------------------------------- looking is not taking
look = body(rig, "public FakeSpec resultFor(BlockPos pos) {")
take = body(rig, "public FakeSpec takeResultFor(BlockPos pos) {")
check("there are two calls, not one", look != "" and take != "")
check("looking only peeks", "queue.peek()" in look and "queue.take()" not in look)
check("taking takes", "queue.take()" in take)

# Everything that merely displays must be on the looking side.
check("the preview only looks", "profile.resultFor(pos)" in disp)
fire = disp[disp.index("if (profile.roulette) {"):disp.index("if (result == null) {")]
check("only the fire path takes", "takeResultFor" in fire)
check("and it is the only place that does", disp.count("takeResultFor") == 1)

# A paper round is decided once, however many machines fire in it.
start = body(rig, "public void startRound(Random random, long tick) {")
check("a queued winner is spent at the start of a round", "this.queue.take()" in start)
# Counted by where rather than how many: a magic number says something changed, not what,
# and I got the number wrong the first time I wrote it down.
inside = start.count("this.queue.take()") + take.count("this.queue.take()")
check("nothing outside those two methods takes from the queue (%d of %d)"
      % (inside, rig.count("this.queue.take()")),
      inside == rig.count("this.queue.take()"))
# An entry nothing answers to must not jam the queue behind it forever.
check("an entry this game cannot use is dropped", "hasSide(queued)" in start
      and "else if (queued != null" in start)
check("and on a cycled rig too", "if (next != null) this.queue.take();" in take)

# --------------------------------------------------------- said, not hidden
check("the doctor says the queue overrides the answer",
      "these come first" in client)
check("adding is checked against what the game can produce",
      "queueOptions()" in client and "is not one of" in client)
check("a full queue says how many it took", "the queue holds" in client)
check("the preview runs past the end", "once the queue is spent" in client)
# Both halves, named: "queue" appears on the reading side too, so asking whether the word
# is in the file passed with the writing half deleted.
check("the queue is written", 'json.add("queue", queued)' in disp)
check("and read back", 'json.has("queue")' in disp and "profile.queue.copyFrom" in disp)

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "peeking never consumes, only a firing machine takes, a round spends one entry, and "
      "an entry the game cannot use is dropped rather than jamming it")
sys.exit(1 if fails else 0)
