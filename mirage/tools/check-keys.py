"""One pair of keys rigs every game, and what they mean follows the rig.

That only holds if four things agree: which game a rig counts as, what the forward key does,
what the back key does, and what they are labelled. A label that says one thing while the key
does another is worse than no label, because it is believed."""
import io, re, sys
rig = io.open("src/main/java/dev/skullzz/mirage/client/RigProfile.java", encoding="utf-8").read()
mc  = io.open("src/main/java/dev/skullzz/mirage/client/MirageClient.java", encoding="utf-8").read()
disp = io.open("src/main/java/dev/skullzz/mirage/client/ClientDispensers.java", encoding="utf-8").read()

fails = []
def check(name, cond):
    if not cond: fails.append(name)

def body(src, sig):
    m = re.search(re.escape(sig) + r"(.*?)\n    \}", src, re.S)
    return m.group(1) if m else ""

# ------------------------------------------------------------------ every game
MODES = re.search(r"public enum Keys \{(.*?)\}", rig).group(1)
MODES = [m.strip() for m in MODES.split(",") if m.strip()]
check("there is a name for each shape of rigging", sorted(MODES)
      == ["BLACKJACK", "CYCLED", "ODD_EVEN", "PAPER", "RACE", "ROULETTE"])

keys = body(rig, "public Keys keys() {")
for mode in MODES:
    if mode == "CYCLED": continue
    check("keys() knows about %s" % mode, "Keys." + mode in keys)
check("anything else cycles", "return Keys.CYCLED;" in keys)

# Each of the three that answer "what does this key do" must have a branch per game, or a
# game silently falls through to another game's answer.
for name, sig in (("mode", "public String mode() {"),
                  ("the forward label", "public String forwardLabel() {"),
                  ("the back label", "public String backLabel() {")):
    text = body(rig, sig)
    for mode in MODES:
        if mode == "CYCLED":
            check("%s has a fallback" % name, "default:" in text)
        else:
            check("%s covers %s" % (name, mode), "case " + mode + ":" in text)

# ---------------------------------------------------------------- the dispatch
result = body(mc, "private static void rigResult(MinecraftClient client, int delta) {")
check("the keys switch on the same thing the labels do",
      "ClientDispensers.active().keys()" in result)
for mode in MODES:
    if mode == "CYCLED":
        check("the dispatch has a fallback", "default:" in result)
    else:
        check("the dispatch covers %s" % mode, "case " + mode + ":" in result)

# Both result keys go through the dispatch. Either one left wired straight to the presets
# would keep working on the coin flip and do nothing on the other four.
press = re.search(r"while \(nextResult\.wasPressed\(\)\).*?\n.*?previousResult.*?\n", mc, re.S).group(0)
check("the forward key dispatches", "rigResult(client, 1)" in press)
check("the back key dispatches", "rigResult(client, -1)" in press)
check("neither key still calls the presets straight", "selectPreset" not in press)

# --------------------------------------------------- the label matches the act
# Tower: forward is the first colour, and the first colour is what the forward key calls.
fwd, back = body(rig, "public String forwardLabel() {"), body(rig, "public String backLabel() {")
check("forward is labelled the next winner", 'case BLACKJACK: return "next winner";' in fwd)
check("back is labelled the previous one", 'case BLACKJACK: return "previous winner";' in back)
check("forward steps the winner", "case BLACKJACK:\n                stepWinner(client, delta);" in result)
# Both games with named sides walk the same ring, so the direction has to reach it.
winner = body(mc, "private static void stepWinner(MinecraftClient client, int delta) {")
check("the direction is carried through", "ClientDispensers.cycleWinner(delta)" in winner)
check("and both games are let in", "profile.hasSides()" in winner)

# Roulette: forward arms, back takes the arming back off.
check("forward is labelled as arming", 'case ROULETTE: return "arm the loaded shot";' in fwd)
check("back is labelled as cancelling", 'case ROULETTE: return "cancel the arm";' in back)
check("forward arms", "case ROULETTE:\n                setArmed(client, delta > 0);" in result)
armed = body(mc, "private static void setArmed(MinecraftClient client, boolean on) {")
check("arming arms and not-arming disarms",
      "ClientDispensers.armNext();" in armed and "ClientDispensers.disarm();" in armed)

# Paper steps both ways rather than only forward.
check("paper steps by the direction given",
      "case PAPER:\n                stepWinner(client, delta);" in result)
check("the winner step takes a direction", "cycleWinner(delta)" in mc)
check("and carries it through", "active().cycleWinner(delta)" in disp)

# --------------------------------------------------------- stepping both ways
# Run the real ring: the sides, with chance one past the end.
step = body(rig, "public String cycleWinner(int delta) {")
check("chance sits past the last side", "int chance = names.size();" in step)
check("the ring wraps both ways", "Math.floorMod(index + delta, chance + 1)" in step)

def walk(sides, winner, delta):
    chance = len(sides)
    index = chance if not winner else (sides.index(winner) if winner in sides else chance)
    index = (index + delta) % (chance + 1)
    return "" if index >= chance else sides[index]

SIDES = ["Player", "Host"]
seen, at = [], ""
for _ in range(3):
    at = walk(SIDES, at, 1)
    seen.append(at)
check("forward walks Player, Host, chance", seen == ["Player", "Host", ""])

seen, at = [], ""
for _ in range(3):
    at = walk(SIDES, at, -1)
    seen.append(at)
check("back walks the other way", seen == ["Host", "Player", ""])

# Overshooting by one press has to be one press back, which is the whole point of a back key.
at = ""
for _ in range(5): at = walk(SIDES, at, 1)
there = at
check("one back undoes one forward", walk(SIDES, walk(SIDES, there, 1), -1) == there)

# A rig with no sides yet must not throw or invent one.
check("no sides means no winner", walk([], "", 1) == "")
check("an unknown winner counts from chance", walk(SIDES, "Nobody", 1) == "Player")

# ------------------------------------------------------------- it has to say so
# Switching rig with the key changes what F and R mean. Doing that silently is the whole
# problem this was meant to fix.
cycle = re.search(r"while \(cycleRig\.wasPressed\(\)\) \{(.*?)\n            \}", mc, re.S).group(1)
check("switching rig says what the keys now do", "announceRig(client)" in cycle)
say = body(mc, "private static void announceRig(MinecraftClient client) {")
check("and names the game and both keys", "profile.mode()" in say
      and "profile.forwardLabel()" in say and "profile.backLabel()" in say)
check("the nag is not written over", "if (!nagIfUnset(client)) announceRig(client);" in cycle)

# The same answer has to be reachable without pressing anything.
check("the status line carries it",
      'lines.add("  F " + profile.forwardLabel() + ", R " + profile.backLabel());' in disp)
check("there is a key list", 'literal("keys")' in mc and "listKeys" in mc)

# ------------------------------------------------------- one game at a time
#
# The mode flags were independent, and turning one on left the last one on underneath.
# keys() picks a winner by priority so it looked like it worked -- the rig reported the
# new game and dealt it -- but the old game was still set, so its rows stayed in the menu
# and turning the new one off dropped back into a game you thought you had left.
GAME_FLAGS = ["paper", "blackjack", "roulette", "race", "oddEven"]
setGame = body(rig, "public void setGame(Keys game) {")
for flag in GAME_FLAGS:
    check("setGame turns %s on for its own game and off for every other" % flag,
          "this.%s = game == Keys." % flag in setGame)
check("and drops whatever the last game had half finished",
      "resetRace();" in setGame and "roundTick = Long.MIN_VALUE;" in setGame)

# setGame is the only place allowed to set one, so there is one answer to what a rig is.
# The exception is loading: a file may hold two flags, and restoring it is not switching.
# Any assignment at all, however the profile was reached -- profile.paper,
# ClientDispensers.active().paper, a local. The first version of this check listed the
# prefixes it expected and so missed the one the command actually used.
for flag in GAME_FLAGS:
    # A dot in front, so this is a field being set rather than a local being
    # declared -- there is a RigProfile local actually named "paper".
    outside = re.findall(r"\.\s*" + flag + r"\s*=\s*(?!=)", mc)
    check("nothing in the commands sets %s directly, they go through setGame" % flag,
          not outside)

# The menu has to offer every game, or one exists that can only be reached by command --
# which is how a rig ends up stuck in a game with no way out of it on screen.
screen = io.open("src/main/java/dev/skullzz/mirage/client/RyneRigScreen.java",
                 encoding="utf-8").read()
for mode in MODES:
    check("the rig menu offers %s" % mode, "Keys." + mode in screen)
check("and picking one switches through setGame", "setGame(pick)" in screen)
# The rows below the picker belong to whichever game was on, so switching has to rebuild
# the screen. say() is what does it -- it reopens -- so the picker must go through it.
picker = re.search(r"for \(RigProfile\.Keys game : GAMES\) \{(.*?)\n            \}",
                   screen, re.S)
# A call, with its message -- the comment above it in the picker says the word "say()"
# and the first version of this check happily matched that instead.
check("and rebuilds the menu, since the rows below belong to the old game",
      picker is not None and 'say("' in picker.group(1))
check("reopening is what a rebuild is here",
      "setScreen(new RyneRigScreen(" in screen)

print("FAILED: " + "; ".join(fails) if fails else
      "F and R follow the rig across %s; labels, dispatch and status all read from keys()"
      % ", ".join(m.lower() for m in MODES))
sys.exit(1 if fails else 0)
