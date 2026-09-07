"""Compile the mod offline and report the errors that are really errors.

This project cannot build for real here -- Minecraft and Fabric are not on the classpath --
so an offline `javac` is the nearest thing. Two things had to be fixed before it was worth
anything, and both had already let a broken build through:

  1. javac stops reporting after 100 errors by default. The mod produces 2685 without
     Minecraft, so every earlier "structurally clean" was reading the first hundred
     unresolved imports and nothing else. The real errors were always past the cap.

  2. javac will not attribute a method body if the class's supertype is unresolved. Every
     screen here extends Screen, so nothing inside any of them was ever looked at. One
     small stub (tools/stubs) fixes that for the screens, which is where it matters.

What it reports is a whitelist, not a blacklist. An offline compile is full of errors that
mean nothing more than "that class is not stubbed" -- unresolved symbols, missing packages,
an @Override whose supertype is absent. Listing what to ignore means the next unfamiliar
kind of noise gets reported as a bug; listing what to report means the next unfamiliar kind
of real error gets missed, which is the cheaper mistake and the one that says something.
Every phrase below is an error javac can only raise once it has genuinely understood the
code, so none of them can be produced by a missing stub."""
import glob, os, re, shutil, subprocess, sys, tempfile

STUBS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "stubs")

# Errors that mean the code is wrong, whatever is or is not on the classpath.
REAL = [
    "already defined",              # a duplicate local, field or method
    "cyclic inheritance",           # a type naming itself in its own supertypes
    "weaker access privileges",     # an override that narrowed visibility
    "multi-catch",                  # alternatives related by subclassing
    "unreachable statement",
    "missing return statement",
    "might not have been initialized",
    "duplicate case label",
    "illegal start of",             # and the rest of the syntax family
    "expected",
    "not a statement",
    "reached end of file",
    "is already defined in",
    "call to this must be first",
    "cannot assign a value to final",
]

fails = []

if shutil.which("javac") is None:
    print("SKIPPED: no JDK to compile with")
    sys.exit(0)

sources = sorted(glob.glob("src/main/java/**/*.java", recursive=True))
stubs = sorted(glob.glob(os.path.join(STUBS, "**", "*.java"), recursive=True))
if not sources:
    print("FAILED: no sources found")
    sys.exit(1)

work = tempfile.mkdtemp(prefix="mirage-compile-")
try:
    run = subprocess.run(
        # Uncapped. The default of 100 is what hid every one of these before.
        ["javac", "-proc:none", "-nowarn", "-Xmaxerrs", "100000",
         "-d", os.path.join(work, "classes")] + sources + stubs,
        capture_output=True, text=True)
    lines = run.stderr.splitlines() + run.stdout.splitlines()
finally:
    shutil.rmtree(work, ignore_errors=True)

# javac puts the detail of some errors ("weaker access privileges", the multi-catch note)
# on the line after the location, so the whole output is searched rather than only the
# lines carrying a file name.
blob = "\n".join(lines)
for phrase in REAL:
    for line in lines:
        if phrase in line and "error" in blob:
            # The location is the line above for continuation detail.
            where = line.strip()
            if not re.match(r"^\S+\.java:\d+:", where):
                index = lines.index(line)
                for back in range(index - 1, max(-1, index - 4), -1):
                    if re.match(r"^\S+\.java:\d+:", lines[back].strip()):
                        where = lines[back].strip() + "  ->  " + where
                        break
            if where not in fails: fails.append(where)
            break

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("%d sources compiled offline against %d stub(s), uncapped: no duplicate names, "
      "narrowed overrides, cyclic supertypes, bad multi-catch or unreachable code"
      % (len(sources), len(stubs)))
