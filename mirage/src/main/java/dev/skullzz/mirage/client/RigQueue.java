package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The next few results, in order, before the rig goes back to whatever it was doing.
 *
 * <p>Set up a run once instead of pressing a key between every game: three of one, then
 * one of another, then back to normal. What an entry means depends on the game -- a side
 * name where there are sides, a preset name where there are not -- but the queue itself
 * does not care, which is why it can live here with no Minecraft in it and be run.
 *
 * <p>The distinction that matters and is easy to get wrong: looking at the queue must not
 * change it. The preview shown on the dashboard, in the rig menu and by the doctor all
 * ask what is next, several times a second. If asking consumed an entry the queue would
 * empty itself without a single machine firing.
 */
public final class RigQueue {

    /** As many as anyone would line up by hand; past this it is a script, not a queue. */
    public static final int MOST = 64;

    private final List<String> entries = new ArrayList<>();

    public List<String> entries() {
        return this.entries;
    }

    public boolean isEmpty() {
        return this.entries.isEmpty();
    }

    public int size() {
        return this.entries.size();
    }

    public void clear() {
        this.entries.clear();
    }

    /**
     * Adds an entry, or several of it.
     *
     * @return how many were actually added, which is fewer than asked for once it is full
     */
    public int add(String entry, int times) {
        if (entry == null) return 0;

        String clean = entry.trim();
        if (clean.isEmpty()) return 0;

        int added = 0;
        for (int i = 0; i < Math.max(1, times) && this.entries.size() < MOST; i++) {
            this.entries.add(clean);
            added++;
        }
        return added;
    }

    /** What is next, without taking it. Safe to ask constantly, and everything does. */
    public String peek() {
        return this.entries.isEmpty() ? null : this.entries.get(0);
    }

    /** What is next, taking it. Only a machine actually firing may call this. */
    public String take() {
        return this.entries.isEmpty() ? null : this.entries.remove(0);
    }

    /** Puts one back at the front, for a fire that was refused after taking its answer. */
    public void putBack(String entry) {
        if (entry != null && this.entries.size() < MOST) this.entries.add(0, entry);
    }

    /**
     * The next few, written out, without changing anything.
     *
     * <p>Runs past the end honestly: once the queue is spent the rig goes back to what it
     * was doing, and a plan that quietly repeated the queue forever would be a lie about
     * the fourth game.
     */
    public List<String> plan(int count, String afterwards) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(i < this.entries.size() ? this.entries.get(i) : afterwards);
        }
        return out;
    }

    /** The queue in one line, with runs of the same thing counted rather than repeated. */
    public String describe() {
        if (this.entries.isEmpty()) return "empty";

        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < this.entries.size()) {
            String entry = this.entries.get(i);
            int run = 1;
            while (i + run < this.entries.size() && this.entries.get(i + run).equals(entry)) {
                run++;
            }
            if (out.length() > 0) out.append(", ");
            out.append(run > 1 ? run + "x " + entry : entry);
            i += run;
        }
        return out.toString();
    }

    public void copyFrom(List<String> saved) {
        this.entries.clear();
        if (saved == null) return;
        for (String entry : saved) add(entry, 1);
    }
}
