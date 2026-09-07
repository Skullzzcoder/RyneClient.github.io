package dev.skullzz.mirage.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Set;
import java.util.List;
import java.util.Map;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * One game's worth of rigging: the items it uses, and what each dispenser fires.
 *
 * <p>A coin-flip game wants two items cycled between. A game where two dispensers each fire
 * something and the higher one wins instead wants a fixed answer per dispenser, so both are
 * supported and the per-dispenser answer wins when there is one.
 */
public final class RigProfile {
    public final String name;

    /** Items cycled through with the keybinds. */
    public final List<FakeSpec> presets = new ArrayList<>();
    /** What a particular dispenser fires, overriding the cycled item. */
    public final Map<BlockPos, FakeSpec> perDispenser = new LinkedHashMap<>();

    /**
     * What each dispenser appears to hold: position to (slot to fake).
     *
     * <p>Kept per dispenser rather than per container size, because two dispensers in the
     * same game hold different things and both have to empty separately.
     */
    public final Map<BlockPos, Map<Integer, FakeSpec>> stock = new LinkedHashMap<>();

    /** Where this game's fake arrow lands, if it uses one. */
    public Vec3d arrowTarget;

    /** The sides a paper game is set up with, in the order they were watched. */
    public static final String[] DEFAULT_SIDES = { "Player", "Host" };

    /**
     * Paper mode: two dispensers each fire a numbered slip and the higher number wins.
     *
     * <p>Unlike the other games this one has no single answer, because what comes out of one
     * machine only means anything next to what came out of the other. So the pair is drawn
     * together and each dispenser takes its half.
     */
    public boolean paper;
    /** Which dispenser stands for whom, so a slip can be named for its side. */
    public final Map<BlockPos, String> sides = new LinkedHashMap<>();
    /** Whose slip comes out higher. Empty leaves it to chance. */
    public String winner = "";
    /** What the slips are made of, and how high they go. */
    public String slipItem = "paper";
    public int numbers = 9;
    /** The side a draw belongs to. Named rather than fixed, since the sides are. */
    public String house = DEFAULT_SIDES[1];
    /** How often a round the house takes is drawn level instead, in percent. Off by default. */
    public int tieChance;

    /**
     * Race mode: three lanes of horse armour, and whichever lands all three pieces first
     * has won.
     *
     * <p>The order is drawn once for the whole race by {@link Games#race} and then handed
     * out one piece per fire, so every machine in the line is reading from the same run.
     * Drawing per fire instead would let two lanes finish in the same race.
     */
    public boolean race;
    /** Which lane is to win. Empty leaves it to chance. */
    public String raceWinner = "";
    /** The run in progress, front first. Never saved: a half-finished race is not state. */
    public final java.util.List<String> raceOrder = new ArrayList<>();

    /**
     * Odd-or-even mode: one number comes out and the call was made before it did.
     *
     * <p>Shares the paper game's slips and bounds; only what decides the round differs,
     * which is why it is a flag beside {@code paper} rather than a copy of it.
     */
    public boolean oddEven;

    /**
     * What the other side called, for the two games that have a call.
     *
     * <p>"high"/"low" or "odd"/"even". Kept because the rig cannot know it otherwise, and
     * a round drawn against the wrong call comes out backwards -- the one failure that
     * looks exactly like bad luck.
     */
    public String call = "";

    /** Whether the caller is to be right. Off means the house takes it. */
    public boolean callerWins;

    /** Drawn once per round and shared by both machines. Never saved. */
    public int highRoll = 9;
    public int lowRoll = 1;
    public String roundWinner = "";
    public long roundTick = Long.MIN_VALUE;

    /**
     * Blackjack mode: the machine is a shoe of numbered slips and fires one card at a time.
     *
     * <p>Two of every number, one number per slot, which is what a nine-slot dispenser holds
     * and what a shoe looks like through the glass. The rigging is which card comes out
     * next: named, or left to chance, and the result keys walk the numbers.
     */
    public boolean blackjack;
    /** How many different numbers are in the shoe. */
    public int cards = 9;
    /** How many of each number it holds. */
    public int cardEach = 2;
    /**
     * Each side's cards this hand. Never saved: a hand belongs to the table it is on.
     *
     * <p>Kept as the cards rather than as a total, because an ace is worth eleven or one
     * depending on what arrives after it and a running total cannot be taken back.
     */
    public final Map<String, List<Integer>> hands = new LinkedHashMap<>();
    /** The side the next card goes to when a machine has not said, e.g. a one-box table. */
    public String dealTo = "";

    /**
     * Mix mode: one machine holding a fixed spread of items, one of which comes out.
     *
     * <p>45/45/10 is the game it was written for -- four diamonds, four emeralds and one
     * crystal in the nine slots, the player calling which they will get. The rigging is the
     * plainest of all of them: the answer is simply whichever item is selected, so the
     * result key already cycles it. What the mode adds is the shape of the machine. The
     * ordinary layout puts one of each item in, which is right for a coin flip and wrong
     * here: the whole game is the odds you can see through the glass, and three items in a
     * nine-slot box are not odds at all.
     */
    public boolean mix;
    /** How many of each item the machine holds. Runs alongside the presets. */
    public final List<Integer> mixCounts = new ArrayList<>();
    /** What each item pays, for the sake of saying so when it is picked. */
    public final List<Integer> mixPayouts = new ArrayList<>();

    /**
     * Whether a machine puts its answer down as a block rather than throwing it out.
     *
     * <p>A dispenser holding shulker boxes places them, so a game played with them shows its
     * answer standing on the ground rather than bouncing across it. Belongs to the rig
     * rather than to any one game: it once lived inside the tower's own block, which threw
     * it away along with that game.
     */
    public boolean placeOutput;

    /**
     * How long a block a machine put down takes to break, in seconds. Zero is vanilla speed.
     *
     * <p>The prize is the thing everyone is looking at, and a prize that vanishes the instant
     * it is touched does not read as having been mined. Its own time rather than the block's,
     * because the block is chosen for how it looks and a shulker box happens to give way in
     * about two ticks to any decent pickaxe.
     */
    public double breakSeconds = 1.5;

    /**
     * Roulette mode: instead of one answer, the dispenser cycles through a fixed number of
     * shots and the loaded one lands on a chosen position in that cycle.
     */
    public boolean roulette;
    /** How many shots before the cycle starts over. */
    public int chambers = 6;
    /** Which shot in the cycle is the loaded one, counting from one. */
    public int bulletAt = 1;
    public FakeSpec bullet;
    /** What the other shots fire. Null means nothing comes out at all. */
    public FakeSpec blank;
    /** How many shots have gone in the current cycle. */
    public int shot;
    /**
     * Fire the loaded item only when armed, rather than on a counted position.
     *
     * <p>For a game where turns come in an order nobody decides in advance, counting chambers
     * is the wrong shape: you want to say "this one" as it happens.
     */
    public boolean manualTrigger;
    /** Set by arming; the next shot is the loaded one, then this clears itself. */
    public boolean armed;

    private int presetIndex = -1;

    public RigProfile(String name) {
        this.name = name;
    }

    // -------------------------------------------------------------------- keys

    /**
     * Which shape of rigging the result keys drive on this game.
     *
     * <p>Every game is rigged by one pair of keys, and what that pair means changes with the
     * game: two items to pick between, two sides to hand the round to, two colours to have
     * been called, or armed and not armed. Working that out in one place rather than at each
     * of the three that need it -- the keys themselves, what they are labelled, and what the
     * status line says -- is the only way the label and the key can be trusted to agree.
     */
    public enum Keys { BLACKJACK, RACE, ODD_EVEN, PAPER, ROULETTE, CYCLED }

    public Keys keys() {
        // Ordered, because a rig may carry more than one mode flag: an older file can hold
        // a paper rig that was once a roulette one. First match wins, everywhere.
        //
        // The race and odd-even come above paper deliberately. Odd-even sets the paper flag
        // too, because it borrows the slips and the bounds -- so were paper tested first,
        // an odd-even rig would quietly deal a plain high-low round instead and the call
        // would never be read at all.
        if (this.blackjack) return Keys.BLACKJACK;
        if (this.race) return Keys.RACE;
        if (this.oddEven) return Keys.ODD_EVEN;
        if (this.paper) return Keys.PAPER;
        if (this.roulette) return Keys.ROULETTE;
        return Keys.CYCLED;
    }

    /** What game this is, in a word, for saying which one you have just switched to. */
    public String mode() {
        switch (keys()) {
            case BLACKJACK: return "blackjack";
            case RACE: return "horse race";
            case ODD_EVEN: return "odd or even";
            case PAPER: return "high-low";
            case ROULETTE: return "roulette";
            default: return this.mix ? "45/45/10" : "cycled";
        }
    }

    /** What the forward result key does right now. */
    public String forwardLabel() {
        switch (keys()) {
            case BLACKJACK: return "next winner";
            case RACE: return "next lane to win";
            case ODD_EVEN: return "the call is right";
            case PAPER: return "next winner";
            case ROULETTE: return "arm the loaded shot";
            default: return "next item";
        }
    }

    /** What the back result key does right now. */
    public String backLabel() {
        switch (keys()) {
            case BLACKJACK: return "previous winner";
            case RACE: return "previous lane";
            case ODD_EVEN: return "the call is wrong";
            case PAPER: return "previous winner";
            case ROULETTE: return "cancel the arm";
            default: return "previous item";
        }
    }

    public int presetIndex() {
        return this.presetIndex;
    }

    public void setPresetIndex(int index) {
        this.presetIndex = index;
    }

    public FakeSpec selected() {
        if (this.presetIndex < 0 || this.presetIndex >= this.presets.size()) return null;
        return this.presets.get(this.presetIndex);
    }

    /** Steps through the presets and returns the new one, or null if there are none. */
    public FakeSpec cycle(int delta) {
        if (this.presets.isEmpty()) return null;

        this.presetIndex = Math.floorMod(this.presetIndex + delta, this.presets.size());
        return this.presets.get(this.presetIndex);
    }

    /**
     * The next few results, set up in advance.
     *
     * <p>A run laid out once instead of a key press between every game. Empty by default,
     * and once it is spent the rig goes back to whatever it was doing.
     */
    public final RigQueue queue = new RigQueue();

    /**
     * What this dispenser should appear to fire: its own answer, then the queue, then the
     * cycled one.
     *
     * <p>Looking only. This is what the preview, the rig menu and the doctor all call,
     * several times a second between them -- if it consumed a queued entry the queue would
     * empty itself without a machine ever firing.
     */
    public FakeSpec resultFor(BlockPos pos) {
        FakeSpec fixed = this.perDispenser.get(pos);
        if (fixed != null) return fixed;

        FakeSpec queued = presetNamed(this.queue.peek());
        return queued != null ? queued : selected();
    }

    /**
     * The same answer, and the queue moves on.
     *
     * <p>Only a machine actually firing may call this. Everything else asks
     * {@link #resultFor}.
     */
    public FakeSpec takeResultFor(BlockPos pos) {
        FakeSpec fixed = this.perDispenser.get(pos);
        if (fixed != null) return fixed;

        String next = this.queue.peek();
        FakeSpec queued = presetNamed(next);
        if (queued != null) {
            this.queue.take();
            return queued;
        }
        // An entry that matches nothing is dropped rather than left to jam the queue
        // forever behind a name that no longer exists.
        if (next != null) this.queue.take();
        return selected();
    }

    /** A preset by its label, or null. What a queue entry means on a cycled rig. */
    public FakeSpec presetNamed(String label) {
        if (label == null) return null;
        for (FakeSpec preset : this.presets) {
            if (preset.label().equalsIgnoreCase(label)) return preset;
        }
        return null;
    }

    /** The names a queue entry may take on this rig, for suggesting and for checking. */
    public java.util.List<String> queueOptions() {
        java.util.List<String> out = new ArrayList<>();
        if (hasSides()) {
            out.addAll(sideNames());
        } else {
            for (FakeSpec preset : this.presets) out.add(preset.label());
        }
        return out;
    }

    /**
     * Advances the chamber and says what this shot fires.
     *
     * <p>Counts from one so that "the third shot" means the third, and wraps once the cycle is
     * spent.
     */
    public FakeSpec advanceRoulette() {
        this.shot++;
        if (this.shot > this.chambers) this.shot = 1;

        // Arming wins over both counting and manual mode, and is spent by this shot.
        if (this.armed) {
            this.armed = false;
            return this.bullet;
        }
        if (this.manualTrigger) return this.blank;

        return this.shot == this.bulletAt ? this.bullet : this.blank;
    }

    public void resetShots() {
        this.shot = 0;
    }

    /** Clamps the chamber settings to something coherent after an edit. */
    public void tidyRoulette() {
        this.chambers = Math.max(1, Math.min(this.chambers, 64));
        this.bulletAt = Math.max(1, Math.min(this.bulletAt, this.chambers));
        if (this.shot > this.chambers) this.shot = 0;
    }

    // --------------------------------------------------------------------- mix

    /** How many of the item at an index the machine holds. */
    public int mixCount(int index) {
        if (index < 0 || index >= this.mixCounts.size()) return 1;
        return Math.max(0, this.mixCounts.get(index));
    }

    /** What the item at an index pays, as a multiplier. */
    public int mixPayout(int index) {
        if (index < 0 || index >= this.mixPayouts.size()) return 1;
        return Math.max(1, this.mixPayouts.get(index));
    }

    /** How many items a full machine holds, which is what the odds are out of. */
    public int mixTotal() {
        int total = 0;
        for (int i = 0; i < this.presets.size(); i++) total += mixCount(i);
        return total;
    }

    /**
     * The one item there is least of, or -1 if nothing stands out.
     *
     * <p>It gets the middle slot, the way the roulette rig puts its loaded chamber there:
     * the prize sitting in the centre of the glass is how a house would build it, and it
     * makes the odds readable at a glance instead of having to count. A spread with no
     * single rarest item -- three of each, say -- has no centre to give, so it is laid out
     * in order and the question does not arise.
     */
    public int rarestPreset() {
        int rarest = -1;
        int fewest = Integer.MAX_VALUE;
        int ties = 0;

        for (int i = 0; i < this.presets.size(); i++) {
            int held = mixCount(i);
            if (held < fewest) {
                fewest = held;
                rarest = i;
                ties = 1;
            } else if (held == fewest) {
                ties++;
            }
        }
        return ties == 1 ? rarest : -1;
    }

    /** The chance of the item at an index coming out of an honest machine, in percent. */
    public int mixChance(int index) {
        int total = mixTotal();
        return total <= 0 ? 0 : Math.round(mixCount(index) * 100.0F / total);
    }

    // --------------------------------------------------------------- blackjack

    /** The card that counts as eleven where it can, and one where it cannot. */
    public static final int ACE = 1;
    /** Over this is a bust. */
    public static final int TARGET = 21;

    /** Keeps the shoe's shape sane after an edit. */
    public void tidyCards() {
        this.cards = Math.max(1, Math.min(this.cards, 9));
        this.cardEach = Math.max(1, Math.min(this.cardEach, 64));
    }

    /** What a card is called on its slip. The one is an ace and reads like one. */
    public static String cardName(int card) {
        return card == ACE ? "A" : String.valueOf(card);
    }

    /**
     * What a hand is worth, counting every ace as eleven until that would bust it.
     *
     * <p>The ordinary rule, and the reason a hand cannot be scored by adding it up once: an
     * ace is worth eleven or one depending on what else arrives afterwards, so the whole
     * hand is re-read every time rather than kept as a running total.
     */
    public static int handValue(List<Integer> hand) {
        int total = 0;
        int aces = 0;

        for (int card : hand) {
            if (card == ACE) {
                aces++;
                total += 11;
            } else {
                total += card;
            }
        }
        // Each ace drops from eleven to one, one at a time, only as far as it has to.
        while (total > TARGET && aces > 0) {
            total -= 10;
            aces--;
        }
        return total;
    }

    public List<Integer> handFor(String side) {
        return this.hands.computeIfAbsent(side == null ? "" : side, ignored -> new ArrayList<>());
    }

    public int totalFor(String side) {
        return handValue(handFor(side));
    }

    /** Forgets both hands, so the next card starts a new one. */
    public void newHand() {
        this.hands.clear();
    }

    /**
     * The card to deal to a side, chosen so that whoever is meant to win does.
     *
     * <p>This is the whole rigging. Naming a card meant knowing every total at the table and
     * working out in your head which number produced the result you wanted, while somebody
     * waited -- so instead the outcome is named and the card is worked back from it.
     *
     * <p>For the side that is meant to win: never a card that busts them, and otherwise the
     * one that leaves them closest to twenty-one. For the side that is meant to lose: the
     * card that busts them if any card can, and otherwise the one that leaves them lowest.
     * Ties among equally good cards are broken at random, so a table played twice does not
     * deal the same shoe twice.
     */
    public int chooseCard(String side, Random random) {
        // Nothing named is an honest deal.
        if (this.winner.isEmpty()) return 1 + random.nextInt(Math.max(1, this.cards));

        List<Integer> hand = new ArrayList<>(handFor(side));
        boolean toWin = this.winner.equalsIgnoreCase(side);

        List<Integer> best = new ArrayList<>();
        int bestScore = Integer.MIN_VALUE;

        for (int card = 1; card <= this.cards; card++) {
            hand.add(card);
            int total = handValue(hand);
            hand.remove(hand.size() - 1);

            int score;
            if (toWin) {
                // A bust is the one thing that cannot happen to them, so it is scored below
                // every hand that does not bust rather than merely worse than them.
                score = total > TARGET ? Integer.MIN_VALUE + 1 + total : total;
            } else {
                score = total > TARGET ? Integer.MAX_VALUE - 1 : -total;
            }

            if (score > bestScore) {
                bestScore = score;
                best.clear();
                best.add(card);
            } else if (score == bestScore) {
                best.add(card);
            }
        }
        return best.get(random.nextInt(best.size()));
    }

    // ------------------------------------------------------------------- paper

    /**
     * Which side this dispenser plays for, giving it one if it has none yet.
     *
     * <p>Watched dispensers are shared by every rig, so switching to the paper game reaches
     * machines belonging to the other games too. Only the two sides are ever handed out, and
     * only against machines still in play: a third one is not a third player, it is the
     * roulette dropper standing nearby, and it is left out rather than called "Side 3".
     *
     * @param live the dispensers currently being watched.
     * @return the side, or empty for a machine that is not part of this game.
     */
    public String sideAt(BlockPos pos, Set<BlockPos> live) {
        String side = sideOf(pos);
        if (!side.isEmpty()) return side;

        Set<String> taken = new LinkedHashSet<>();
        for (Map.Entry<BlockPos, String> entry : this.sides.entrySet()) {
            if (live.contains(entry.getKey())) taken.add(entry.getValue());
        }

        for (String candidate : DEFAULT_SIDES) {
            if (taken.contains(candidate)) continue;
            this.sides.put(pos.toImmutable(), candidate);
            return candidate;
        }
        return "";
    }

    /** The side a machine already plays, without giving it one. */
    public String sideOf(BlockPos pos) {
        String side = this.sides.get(pos);
        return side == null ? "" : side;
    }

    /**
     * Forgets which machine plays what, so the parts are handed out again.
     *
     * <p>Sides go to whichever machines get there first and then stay put, which
     * is right while a game is being played and wrong for ever afterwards: a dispenser that
     * took a side months ago, for another game entirely, holds it against the machine that
     * needs it now, and nothing short of unwatching it lets go.
     *
     * @return how many assignments were dropped.
     */
    public int clearParts() {
        int held = this.sides.size();
        this.sides.clear();
        return held;
    }

    /** Forgets sides belonging to machines that are no longer watched. */
    public void pruneSides(Set<BlockPos> live) {
        this.sides.keySet().retainAll(live);
    }

    /**
     * Gives a machine a side, taking it off whoever had it.
     *
     * <p>Only one machine can play for a side. Without the taking-away, naming a second one
     * left both claiming it: the pair then drew the same number as each other every round,
     * which looks like the game being broken rather than like two machines on one side. And
     * it is the fix people are told to use when the sides went to the wrong machines, so it
     * had to be the one thing that could not go wrong.
     */
    public void setSide(BlockPos pos, String side) {
        this.sides.values().removeIf(held -> held.equalsIgnoreCase(side));
        this.sides.put(pos.toImmutable(), side);
    }

    /**
     * The sides in play, without repeats.
     *
     * <p>Ordered by the built-in sides rather than by which machine was watched first, so
     * that a key meaning "the player wins" keeps meaning that however the game was set up.
     */
    public List<String> sideNames() {
        Set<String> unique = new LinkedHashSet<>(this.sides.values());
        List<String> ordered = new ArrayList<>();

        for (String name : DEFAULT_SIDES) {
            if (unique.remove(name)) ordered.add(name);
        }
        ordered.addAll(unique);
        return ordered;
    }

    /** What a slip in a given position reads, e.g. {@code 3 (Player)}. */
    public String slipName(int number, String side) {
        return side == null || side.isEmpty() ? String.valueOf(number)
                : number + " (" + side + ")";
    }

    /**
     * Whether this game is played by two named sides.
     *
     * <p>The paper game and blackjack both are, and everything about naming a winner, giving
     * a machine a side and handing those out again is shared by them because of it.
     */
    public boolean hasSides() {
        return this.paper || this.blackjack;
    }

    /** Whether a side is the one a draw goes to. */
    public boolean isHouse(String side) {
        return !this.house.isEmpty() && this.house.equalsIgnoreCase(side);
    }

    /** Whether a side name still belongs to a machine in the game. */
    public boolean hasSide(String name) {
        for (String side : sideNames()) {
            if (side.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    /**
     * Draws the pair of numbers for a round, and settles who the high one goes to.
     *
     * <p>The high number is kept above the middle so the winning slip always looks like a
     * good draw rather than a two beating a one.
     *
     * <p>A draw belongs to the house, so a round the house is meant to take may come out
     * level: the machines agreeing now and then is what a fair pair of them would do, and
     * it costs nothing. A round the player is meant to take never can, because a draw
     * would hand them the loss the rigging is there to avoid.
     */
    /**
     * The next piece of armour in the race, drawing a fresh run when the last one is out.
     *
     * <p>The queue gets first say, exactly as it does for a paper round: an entry naming a
     * lane sets the winner of the run about to be drawn and is spent doing it. Spending it
     * per piece instead would burn nine entries on one race.
     */
    public String nextRacePiece(Random random) {
        if (this.raceOrder.isEmpty()) {
            String queued = this.queue.peek();
            if (queued != null) {
                if (Games.isLane(queued)) this.raceWinner = queued;
                // Names something no lane answers to. Dropped rather than left to jam the
                // queue behind an entry this game can never satisfy.
                this.queue.take();
            }
            this.raceOrder.addAll(Games.race(this.raceWinner, random));
        }
        return this.raceOrder.isEmpty() ? "" : this.raceOrder.remove(0);
    }

    /** Throws away a part-run race, so the next fire starts a clean one. */
    public void resetRace() {
        this.raceOrder.clear();
    }

    /** How far through the run the race is, for saying so on screen. */
    public String racePosition() {
        int all = Games.LANES.size() * Games.RACE_LENGTH;
        return this.raceOrder.isEmpty() ? "ready"
                : (all - this.raceOrder.size()) + "/" + all;
    }

    /**
     * The number an odd-or-even round deals.
     *
     * <p>The queue names whether the caller is right, so a run can be lined up the same way
     * every other game here lines one up.
     */
    public int nextOddEven(Random random) {
        String queued = this.queue.peek();
        if (queued != null) {
            String clean = queued.trim();
            if (clean.equalsIgnoreCase("win") || clean.equalsIgnoreCase("lose")) {
                this.callerWins = clean.equalsIgnoreCase("win");
                this.queue.take();
            } else {
                this.queue.take();
            }
        }
        return Games.oddEven(this.call, this.callerWins, this.numbers, random);
    }

    public void startRound(Random random, long tick) {
        this.roundTick = tick;

        List<String> names = sideNames();

        // A name can go stale: a machine renamed, unwatched, or carried over from a file
        // written before the sides were worked out. A winner nobody answers to is the worst
        // possible outcome, because then no machine draws the high number and both take the
        // low one -- the same slip on both sides, every single round.
        //
        // Only ever for this round, and only against sides that are actually known. Nothing
        // is known before the machines have been laid out, and taking that for a stale name
        // threw away a winner that had just been set by hand.
        // The queue gets first say, and only here: a round is decided once however many
        // machines fire in it, so this is the one place an entry may be spent. Consuming
        // it per fire would burn two entries a round on a two-sided table.
        String queued = this.queue.peek();
        if (queued != null && hasSide(queued)) {
            this.winner = queued;
            this.queue.take();
        } else if (queued != null && !names.isEmpty()) {
            // Names an outcome this game does not have. Dropped rather than left to jam
            // the queue behind something no side answers to.
            this.queue.take();
        }

        String wanted = this.winner;
        if (!wanted.isEmpty() && !names.isEmpty() && !hasSide(wanted)) wanted = "";

        this.roundWinner = !wanted.isEmpty() ? wanted
                : names.isEmpty() ? "" : names.get(random.nextInt(names.size()));

        int span = Math.max(1, this.numbers / 2);
        this.highRoll = this.numbers - random.nextInt(span);

        // Level only when the house is taking the round and only when asked for. Anything
        // else must come out apart, or the machines agree and the rigging means nothing.
        boolean level = this.tieChance > 0
                && isHouse(this.roundWinner)
                && random.nextInt(100) < this.tieChance;
        this.lowRoll = level ? this.highRoll
                : 1 + random.nextInt(Math.max(1, this.highRoll - 1));
    }

    /** Steps the rigged winner on to the next side, then to chance, then round again. */
    public String cycleWinner() {
        return cycleWinner(1);
    }

    /**
     * Steps the rigged winner either way.
     *
     * <p>The ring it walks is the sides with chance on the end, so stepping back from the
     * first side lands on chance rather than falling off. Both directions matter once the
     * two result keys drive this: overshooting by one press and having to go all the way
     * round again is the thing a back key is for.
     */
    public String cycleWinner(int delta) {
        List<String> names = sideNames();
        if (names.isEmpty()) return "";

        // Chance sits one past the last side, and is where an unknown winner counts from.
        int chance = names.size();
        int index = this.winner.isEmpty() ? chance : names.indexOf(this.winner);
        if (index < 0) index = chance;

        index = Math.floorMod(index + delta, chance + 1);
        this.winner = index >= chance ? "" : names.get(index);
        // Whatever was drawn belongs to the old setting.
        this.roundTick = Long.MIN_VALUE;
        return this.winner;
    }

    /** What a dispenser looks like it is holding, or null if nothing has been laid out. */
    public Map<Integer, FakeSpec> stockAt(BlockPos pos) {
        return this.stock.get(pos);
    }

    public boolean isEmpty() {
        return this.presets.isEmpty() && this.perDispenser.isEmpty()
                && this.arrowTarget == null && !this.roulette && !this.paper && !this.blackjack
                && !this.race && !this.oddEven
                && !this.mix && this.stock.isEmpty();
    }
}
