"""The browser side: the key on the door, and reading a request to change a setting.

Two things are being checked, and they fail in opposite directions.

The key is a security boundary. The dashboard listens on 127.0.0.1, which sounds private
and is not: any page open in your browser can reach your own machine, so an <img> tag on
any site you visit could drive the client. Every way of getting past the key has to fail --
a missing one, an empty one, a prefix of the real one, one of the wrong length -- and the
one that is right has to work.

Reading a change is the other direction. It is reached over a socket by a page that may
have been left open since before the modules were rebuilt, so a request naming something
that no longer exists must do nothing rather than something to whatever is at that
position now. Addresses are names for that reason, not indices.

Run for real: neither class has Minecraft in it."""
import io, os, shutil, subprocess, sys, tempfile

AUTH = "src/main/java/dev/skullzz/mirage/client/WebAuth.java"
SET = "src/main/java/dev/skullzz/mirage/client/WebSettings.java"
DASH = "src/main/java/dev/skullzz/mirage/client/WebDashboard.java"

fails = []
def check(name, cond):
    if not cond: fails.append(name)

auth = io.open(AUTH, encoding="utf-8").read()
settings = io.open(SET, encoding="utf-8").read()
dash = io.open(DASH, encoding="utf-8").read()

check("the key has no Minecraft in it", "net.minecraft" not in auth)
check("nor the settings paperwork", "net.minecraft" not in settings)
check("the key is not guessable", "SecureRandom" in auth)
check("and never written to a file", "renew" in auth and "save" not in auth)

# Every endpoint that changes something must ask for the key. Reads may stay open.
WRITES = ["/select", "/rig", "/shot", "/reset", "/arm", "/fire", "/refill", "/winner",
          "/power", "/rigs", "/set"]
for path in WRITES:
    handler = 'server.createContext("%s", WebDashboard::' % path
    check("the dashboard still serves %s" % path, handler in dash)
guarded = dash.count("WebAuth.allows(")
check("every changing endpoint checks the key (%d of %d)" % (guarded, len(WRITES)),
      guarded >= len(WRITES))

if shutil.which("javac") is None or shutil.which("java") is None:
    print("SKIPPED: no JDK to run it with")
    sys.exit(0)

HARNESS = r'''
import dev.skullzz.mirage.client.WebAuth;
import dev.skullzz.mirage.client.WebSettings;
import java.util.*;

public class WebHarness {
    static int bad = 0;

    static void want(String what, boolean ok) {
        if (!ok && bad < 12) System.out.println("FAILED: " + what);
        if (!ok) bad++;
    }

    public static void main(String[] args) {
        // ---- the key ---------------------------------------------------------------
        WebAuth.clear();
        want("no key means nothing is allowed, not everything",
                !WebAuth.matches("anything") && !WebAuth.allows("k=anything"));
        want("and an empty offering is refused too", !WebAuth.matches(""));

        String key = WebAuth.renew();
        want("a key is long enough to be worth having", key.length() >= 20);
        want("the right key is allowed", WebAuth.matches(key));
        want("carried in a query", WebAuth.allows("k=" + key));
        want("and among other fields", WebAuth.allows("p=a&k=" + key + "&v=1"));

        want("no key in the query is refused", !WebAuth.allows("p=a&v=1"));
        want("an empty key in the query is refused", !WebAuth.allows("k=&v=1"));
        want("a prefix of the key is refused",
                !WebAuth.matches(key.substring(0, key.length() - 1)));
        want("the key with something on the end is refused", !WebAuth.matches(key + "x"));
        want("a different key of the same length is refused",
                !WebAuth.matches("z".repeat(key.length())));
        want("null is refused", !WebAuth.matches(null));

        String second = WebAuth.renew();
        want("renewing gives a different key", !second.equals(key));
        want("and the old one stops working", !WebAuth.matches(key));

        // No look-alike characters: this gets read off a screen.
        for (char c : second.toCharArray()) {
            want("the key avoids characters that read alike",
                    c != 'l' && c != 'o' && c != '0' && c != '1');
        }

        // ---- reading a change -------------------------------------------------------
        List<WebSettings.Module> known = new ArrayList<>();
        WebSettings.Module tracker = new WebSettings.Module("tracker", "Tracking",
                "Tracking", true, false);
        tracker.knobs.add(new WebSettings.Knob("tracker", "Tracking", 0, "alert after",
                "slider", 5, 2, 12, 1, null, "5"));
        tracker.knobs.add(new WebSettings.Knob("tracker", "Tracking", 1, "rakeback",
                "slider", 0, 0, 50, 1, null, "0"));
        known.add(tracker);
        WebSettings.Module odd = new WebSettings.Module("rigs", "45/45/10", "45/45/10",
                false, false);
        known.add(odd);

        WebSettings.Change change = WebSettings.read("p=tracker&r=Tracking&i=0&v=7", known);
        want("a good request is read", change != null);
        want("with its address", change != null && change.panel.equals("tracker")
                && change.row.equals("Tracking") && change.index == 0);
        want("and its value", change != null && change.value == 7);
        want("a knob is not the module", change != null && !change.isModule());

        WebSettings.Change module = WebSettings.read("p=tracker&r=Tracking&v=1", known);
        want("a module with no knob named is the module itself",
                module != null && module.isModule() && module.value == 1);

        // A page left open since before the modules changed must do nothing.
        want("a module that does not exist is refused",
                WebSettings.read("p=ghost&r=Tracking&v=1", known) == null);
        want("a row that does not exist is refused",
                WebSettings.read("p=tracker&r=Ghost&v=1", known) == null);
        want("a knob past the end is refused",
                WebSettings.read("p=tracker&r=Tracking&i=9&v=1", known) == null);
        want("a negative knob is refused",
                WebSettings.read("p=tracker&r=Tracking&i=-2&v=1", known) == null);
        want("a knob on a module with none is refused",
                WebSettings.read("p=rigs&r=45/45/10&i=0&v=1", known) == null);
        want("nonsense for a knob is refused",
                WebSettings.read("p=tracker&r=Tracking&i=abc&v=1", known) == null);
        want("nonsense for a value is refused",
                WebSettings.read("p=tracker&r=Tracking&i=0&v=abc", known) == null);
        want("no value at all is refused",
                WebSettings.read("p=tracker&r=Tracking&i=0", known) == null);
        want("nothing at all is refused", WebSettings.read("", known) == null);
        want("and null is refused", WebSettings.read(null, known) == null);
        want("NaN is refused",
                WebSettings.read("p=tracker&r=Tracking&i=0&v=NaN", known) == null);
        want("infinity is refused",
                WebSettings.read("p=tracker&r=Tracking&i=0&v=Infinity", known) == null);

        // A stale page cannot push a slider past the end it was shown.
        WebSettings.Change high = WebSettings.read("p=tracker&r=Tracking&i=0&v=9999", known);
        want("a value over the top is held to it", high != null && high.value == 12);
        WebSettings.Change low = WebSettings.read("p=tracker&r=Tracking&i=0&v=-9999", known);
        want("and under the bottom", low != null && low.value == 2);

        // A module name with a slash in it -- "45/45/10" is a real rig mode here.
        WebSettings.Change slashed = WebSettings.read("p=rigs&r=45%2F45%2F10&v=1", known);
        want("a name with a slash survives the round trip",
                slashed != null && slashed.row.equals("45/45/10"));

        // The key and the knob index must not share a field name. They did, for about a
        // minute: a request carrying the key would have had it read as a knob index.
        WebSettings.Change withKey = WebSettings.read(
                "p=tracker&r=Tracking&i=1&v=3&k=" + WebAuth.key(), known);
        want("the key does not get read as a knob index",
                withKey != null && withKey.index == 1);

        // A plus is a plus. URLDecoder would make it a space.
        want("a plus is not a space", WebSettings.field("r=a+b", "r").equals("a+b"));
        want("a field that is not there is empty",
                WebSettings.field("a=1&b=2", "zzz").isEmpty());
        want("a field name is matched whole, not by prefix",
                WebSettings.field("kk=1&k=2", "k").equals("2"));

        // ---- the JSON the page reads ------------------------------------------------
        String json = WebSettings.toJson(known);
        want("the JSON names the module", json.contains("\"row\":\"Tracking\""));
        want("and carries its knobs", json.contains("\"label\":\"alert after\""));
        want("whole numbers are not printed with a point",
                WebSettings.number(5).equals("5"));
        want("and a fraction is kept", WebSettings.number(2.5).equals("2.5"));
        want("NaN cannot break the page", WebSettings.number(Double.NaN).equals("0"));
        want("nor infinity",
                WebSettings.number(Double.POSITIVE_INFINITY).equals("0"));

        WebSettings.Module quoted = new WebSettings.Module("p", "say \"hi\"\n",
                "say \"hi\"\n", true, true);
        String risky = WebSettings.toJson(List.of(quoted));
        want("a quote in a name is escaped", risky.contains("say \\\"hi\\\""));
        want("and a newline", risky.contains("\\n"));
        want("a control character is escaped rather than written raw",
                WebSettings.escape("ab").equals("a\\u0001b"));

        System.out.println(bad == 0 ? "OK" : bad + " failed");
    }
}
'''

work = tempfile.mkdtemp(prefix="mirage-web-")
try:
    harness = os.path.join(work, "WebHarness.java")
    io.open(harness, "w", encoding="utf-8").write(HARNESS)
    classes = os.path.join(work, "classes")
    build = subprocess.run(["javac", "-proc:none", "-nowarn", "-d", classes,
                            AUTH, SET, harness], capture_output=True, text=True)
    check("it compiles with no game on the classpath", build.returncode == 0)
    if build.returncode != 0:
        print("FAILED: " + "; ".join(fails) + "\n" + build.stderr[:1500])
        sys.exit(1)

    run = subprocess.run(["java", "-cp", classes, "WebHarness"],
                         capture_output=True, text=True)
    out = [line for line in run.stdout.splitlines() if not line.startswith("Picked up")]
    check("the key holds and a stale request does nothing", out and out[-1] == "OK")
    for line in out:
        if line.startswith("FAILED"): fails.append(line[8:])
finally:
    shutil.rmtree(work, ignore_errors=True)

if fails:
    print("FAILED:\n  " + "\n  ".join(fails))
    sys.exit(1)
print("the key refuses everything but itself, every changing endpoint asks for it, and a "
      "request naming something that no longer exists does nothing")
