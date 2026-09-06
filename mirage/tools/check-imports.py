"""Every type a file names has to be one it can see.

This is the gap that let three errors reach a real build. A missing import shows up as
either "package Mirage does not exist" -- because javac reads Mirage.LOGGER as a package
reference -- or as "cannot find symbol: variable JsonParser". check-symbols.py filters for
"symbol: variable" with a lowercase name, so an uppercase type slips past it, and the
package form is not a "cannot find symbol" at all.

No Minecraft needed: this is a question about the source, and the answer is in the source.
Only the two shapes that actually bite are checked -- Name.member and new Name(...) --
because those are where a missing import turns into a compile error rather than a warning."""
import io, os, re, sys, glob

fails = []
def check(name, cond):
    if not cond: fails.append(name)

# java.lang is imported for you. Not exhaustive, but everything this project touches.
JAVA_LANG = {
    "Object", "String", "StringBuilder", "Integer", "Long", "Double", "Float", "Boolean",
    "Byte", "Short", "Character", "Math", "System", "Thread", "Class", "Enum", "Number",
    "Exception", "RuntimeException", "Error", "Throwable", "IllegalArgumentException",
    "IllegalStateException", "NumberFormatException", "ArithmeticException",
    "NullPointerException", "IndexOutOfBoundsException", "ClassCastException",
    "UnsupportedOperationException", "ReflectiveOperationException", "ClassNotFoundException",
    "NoSuchMethodException", "NoSuchFieldException", "IllegalAccessException",
    "InterruptedException", "Iterable", "Comparable", "Runnable", "CharSequence", "Void",
    "SuppressWarnings", "Override", "Deprecated", "FunctionalInterface", "SafeVarargs",
}

files = sorted(glob.glob("src/main/java/**/*.java", recursive=True))
check("there are sources to check", len(files) > 20)

# Every type this project declares, by package, so a same-package reference is fine.
by_package = {}
for path in files:
    text = io.open(path, encoding="utf-8").read()
    package = re.search(r"^package\s+([\w.]+)\s*;", text, re.M)
    if not package:
        fails.append("%s has no package" % path)
        continue
    by_package.setdefault(package.group(1), set()).add(
        os.path.basename(path)[:-len(".java")])

checked = 0
for path in files:
    text = io.open(path, encoding="utf-8").read()
    package = re.search(r"^package\s+([\w.]+)\s*;", text, re.M).group(1)

    # What this file can see without qualifying it.
    visible = set(JAVA_LANG)
    visible |= by_package.get(package, set())
    for line in re.findall(r"^import\s+(?:static\s+)?([\w.]+)\s*;", text, re.M):
        visible.add(line.rsplit(".", 1)[-1])
    wildcards = bool(re.search(r"^import\s+[\w.]+\.\*\s*;", text, re.M))
    # Anything declared inside this file, including nested types and enums.
    visible |= set(re.findall(r"\b(?:class|interface|enum|record)\s+(\w+)", text))

    if wildcards:
        continue  # A wildcard import can supply anything; nothing to conclude.

    body = re.sub(r"^import[^\n]*\n", "", text, flags=re.M)
    # Comments and strings say nothing about what compiles.
    body = re.sub(r"//[^\n]*", "", body)
    body = re.sub(r"/\*.*?\*/", "", body, flags=re.S)
    body = re.sub(r'"(?:\\.|[^"\\])*"', '""', body)

    used = set()
    # Name.member -- a static access or a nested type. Not preceded by a dot or a word
    # character, so a.B.C and fooBar.Baz do not count.
    used |= set(re.findall(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)\s*\.", body))
    # new Name(
    used |= set(re.findall(r"\bnew\s+([A-Z][A-Za-z0-9_]*)\s*[(<]", body))

    for name in sorted(used):
        # SCREAMING_SNAKE is a constant, not a type: GSON.toJson and LOGGER.warn are
        # fields on this class, and every one of them looked like a missing import on the
        # first run. Java's own convention is the discriminator -- a type name has a
        # lowercase letter in it.
        if name.upper() == name:
            continue
        checked += 1
        if name in visible:
            continue
        fails.append("%s uses %s, which it does not import" % (os.path.basename(path), name))

print("FAILED:\n  " + "\n  ".join(sorted(set(fails))) if fails else
      "%d type references across %d files, every one imported, declared or in java.lang "
      "(ALL_CAPS names are constants, not types, and are not counted)"
      % (checked, len(files)))
sys.exit(1 if fails else 0)
