"""A class may not name a type nested inside itself as its own supertype.

    public class RyneClickScreen extends Screen implements RyneClickScreen.Clicks

Java cannot resolve that. It has to know the supertypes to know what is nested, and it has
to resolve what is nested to know the supertypes, so it gives up and calls it cyclic
inheritance. The fix is always the same -- lift the nested type out to its own file.

This has a check of its own because the usual safety nets cannot see it:

  * The offline `javac -proc:none` pass this project leans on never reaches the cyclic
    check. With no Minecraft on the classpath, `extends Screen` fails first as an
    unresolved symbol, and the analysis that would find the cycle never runs. Verified by
    reintroducing the bug and compiling: the word "cyclic" appears zero times.

  * check-screen only looks at method calls, not at what a class extends.

And the cost of missing it is out of all proportion to the mistake. One bad line made
RyneClickScreen unresolvable, which made `Screen` unresolvable, which broke every other
class extending Screen: 34 errors across 7 files, 6 of which nothing had touched. Anyone
reading that output would start with the wrong file.
"""
import glob, io, os, re, sys

fails = []
def check(name, cond):
    if not cond: fails.append(name)

# The header of a type declaration, and whatever it extends or implements before the brace.
DECLARATION = re.compile(
    r"^[ \t]*(?:public\s+|private\s+|protected\s+|static\s+|final\s+|abstract\s+|sealed\s+"
    r"|non-sealed\s+)*(class|interface|enum|record)\s+(\w+)([^{;]*)",
    re.M)

scanned = 0
declarations = 0

for path in sorted(glob.glob("src/main/java/**/*.java", recursive=True)):
    source = io.open(path, encoding="utf-8").read()
    # Comments carry the explanation of this very bug, so they would match it.
    stripped = re.sub(r"/\*.*?\*/", "", source, flags=re.S)
    stripped = re.sub(r"//[^\n]*", "", stripped)
    scanned += 1

    for found in DECLARATION.finditer(stripped):
        name = found.group(2)
        clause = found.group(3)
        declarations += 1
        if "extends" not in clause and "implements" not in clause:
            continue

        # Its own name followed by a dot, in its own extends/implements clause.
        cycle = re.search(r"\b" + re.escape(name) + r"\s*\.\s*\w+", clause)
        check("%s names %s as its own supertype, which is cyclic -- lift it out to its own "
              "file" % (os.path.basename(path), cycle.group(0) if cycle else name),
              cycle is None)

check("there were type declarations to look at", declarations > 0)
check("and files to look in", scanned > 0)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("%d declarations across %d files, none naming a type nested inside itself as its "
      "own supertype" % (declarations, scanned))
