"""Screens may only call what has already been seen to compile.

The keys screen is half-finished because four Minecraft methods were guessed at and none
of them existed. There is no Minecraft on this machine to check a name against -- but
there is a whole mod that does build, so a method used anywhere in it is a method that
exists in this version. Anything a new screen calls that appears nowhere else is a guess,
and a guess is a build the player has to run to find out about.

This is a floor, not a ceiling: it cannot say a name is right, only that it is not new."""
import io, re, sys, glob, os

# Screens proper: these must also redraw the way the rest of the mod already does.
# Found rather than listed. This was a hand-written list of six, and two screens added
# after it was written were simply never checked -- which is the one thing this file is
# for. A screen that is not scanned is a screen free to call anything it likes.
SCREENS = sorted(p.replace("\\", "/") for p in
                 glob.glob("src/main/java/dev/skullzz/mirage/client/*Screen.java"))

# Also scanned for unproven calls, but not screens, so the redraw rule does not apply.
HELPERS = ["src/main/java/dev/skullzz/mirage/client/RyneDraw.java"]

# Calls knowingly used without having been seen to compile, and where each is allowed.
# An entry here is a decision, not an exemption: it has to be isolated in one method so a
# rename is one compile error in one place, and inspectApi prints the replacement.
DELIBERATE = {"fill": "RyneDraw.java"}

# A different thing from DELIBERATE, and worth keeping separate.
#
# DELIBERATE is a risk knowingly taken: a call nothing has seen compile, kept to one method
# so that if the name has moved it is one error rather than forty -- hence the once-only
# rule below.
#
# These are not risks. They are calls whose only evidence is that the file they are in has
# compiled in every build since it was written, which is exactly what "seen elsewhere in
# the mod" is standing in for anyway. They surfaced when SCREENS became a glob: both live
# in screens the old hand-written list never mentioned, so neither had ever been checked.
# Pinned to their file, but not counted once, because a proven call may be used freely.
PROVEN_BY_BUILD = {"setMessage": "FakeItemsScreen.java",
                   "getBoundKeyOf": "MirageKeysScreen.java"}

# Java and this project's own idioms; not Minecraft, so not evidence either way.
JDK = {"literal", "join", "max", "min", "size", "get", "isEmpty", "length", "substring",
       "lastIndexOf", "replaceAll", "equals", "containsKey", "keySet", "toString", "add",
       "run", "formatted", "trim", "contains", "put", "remove", "valueOf", "format",
       "startsWith", "endsWith", "toLowerCase", "toUpperCase", "split", "hashCode",
       "abs", "getOrDefault", "of", "stream", "sort", "indexOf", "charAt", "isBlank",
       # java.lang and java.util, which are not evidence about Minecraft either way.
       "nanoTime", "currentTimeMillis", "exp", "round", "floor", "ceil", "sqrt", "pow",
       "identityHashCode", "getName", "getDeclaringClass", "getSimpleName", "getClass",
       "run", "getAsBoolean", "keySet", "values", "entrySet", "getKey", "getValue",
       "isEmpty", "clear", "removeAll", "toArray", "compareTo", "equalsIgnoreCase"}

fails = []
def check(name, cond):
    if not cond: fails.append(name)

everything = {}
for path in glob.glob("src/main/java/**/*.java", recursive=True):
    everything[path.replace("\\", "/")] = io.open(path, encoding="utf-8").read()

checked = 0
for screen in SCREENS + HELPERS:
    screen = screen.replace("\\", "/")
    if screen not in everything:
        fails.append("%s is gone" % screen)
        continue

    source = everything[screen]
    elsewhere = "".join(body for path, body in everything.items() if path != screen)

    calls = set(re.findall(r"\.([a-zA-Z]\w*)\s*\(", source))
    # Anything the screen declares itself is its own, not a Minecraft call.
    declared = set(re.findall(r"\b(?:void|int|boolean|String|List<String>|BlockPos|"
                              r"MirageSchematicsScreen)\s+(\w+)\s*\(", source))

    for call in sorted(calls):
        if call in declared or call in JDK:
            continue
        checked += 1
        if call in PROVEN_BY_BUILD:
            check("%s() is only used in %s" % (call, PROVEN_BY_BUILD[call]),
                  os.path.basename(screen) == PROVEN_BY_BUILD[call])
            continue
        if call in DELIBERATE:
            # Allowed only in the file it was decided for, and only once: the whole point
            # is that a rename is one compile error rather than forty.
            check("%s() is only used in %s" % (call, DELIBERATE[call]),
                  os.path.basename(screen) == DELIBERATE[call])
            check("%s() is called exactly once, so a rename is one fix" % call,
                  source.count("." + call + "(") == 1)
            continue
        if call not in elsewhere:
            fails.append("%s calls %s(), which appears nowhere else in the mod -- so "
                         "nothing here has ever seen it compile" % (os.path.basename(screen), call))

    # The two that were actually guessed wrong before, named so they cannot come back
    # quietly on the strength of the rule above alone.
    for risky in ("clearChildren", "rebuildWidgets", "setKeyCode", "getTranslationKey"):
        check("%s does not call %s()" % (os.path.basename(screen), risky),
              ("." + risky + "(") not in source)

    # Redrawing goes through setScreen, which every screen here already does on close.
    # A drawing helper is not a screen and has nothing to redraw.
    if screen in [s.replace("\\", "/") for s in SCREENS]:
        check("%s redraws the way the rest of the mod already does"
              % os.path.basename(screen), "setScreen(" in source)

print("FAILED:\n  " + "\n  ".join(fails) if fails else
      "%d Minecraft calls across %d screen(s), every one already used elsewhere in code "
      "that builds, except %d deliberate: %s, and %d proven by the build itself: %s"
      % (checked, len(SCREENS), len(DELIBERATE),
         ", ".join("%s() in %s" % (k, v) for k, v in sorted(DELIBERATE.items())),
         len(PROVEN_BY_BUILD),
         ", ".join("%s() in %s" % (k, v) for k, v in sorted(PROVEN_BY_BUILD.items()))))
sys.exit(1 if fails else 0)
