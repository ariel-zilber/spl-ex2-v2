package bguspl.set.ex;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;


    private final Dealer dealer;


    public final BlockingQueue<Integer> actions; //todo

    enum PlayerState {
        INIT, PENALIZED, SCORED
    }

    public PlayerState playerState; //todo


    public boolean isTerminate() {
        return this.terminate;
    }

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        this.actions = new ArrayBlockingQueue<>(3);
        this.playerState = PlayerState.INIT;
    }

    public int actionsToPerform() {
        return this.actions.size();
    }

    public int getId() {
        return this.id;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        synchronized (this.dealer) {
            if (!human) createArtificialIntelligence();
        }
        while (!terminate) {
            // consume from queue
            try {
                int slot = this.actions.take();
                boolean isSet = false;
                this.table.keyPressed(this.id, slot);
                isSet = this.table.getPlayerCards(id).size() == 3;
                if (isSet) {
                    this.dealer.claimSet(this);
                }
                if (isSet) {
                    synchronized (this.dealer) {
                        this.dealer.notify();
                    }
                    synchronized (this) {
                        if (this.playerState == PlayerState.INIT) {
                            this.wait();
                        }
                    }

                    switch (this.playerState) {
                        case INIT:
                            break;
                        case SCORED:
                            updateScoreTimeout(env.config.pointFreezeMillis);
                            break;
                        case PENALIZED:
                            updateScoreTimeout(env.config.penaltyFreezeMillis);
                            break;
                    }
                }
            } catch (InterruptedException ignored) {
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }

    private void updateScoreTimeout(long time) {
        long end = System.currentTimeMillis() + time;
        long diff = time;
        while (diff > 0) {
            this.env.ui.setFreeze(id, diff + 1000);
            diff = end - System.currentTimeMillis();
        }
        this.env.ui.setFreeze(id, 0);
        this.actions.clear();
        this.playerState = PlayerState.INIT;
    }

    private void randomAction() {
        int numOfKeys = env.config.playerKeys(id).length;
        int randomSlot = ThreadLocalRandom.current().nextInt(0, numOfKeys);
        keyPressed(randomSlot);
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                randomAction();
                try {
                    aiThread.sleep(500);
                } catch (InterruptedException e) {
                    break;
                }
            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;

        // stop ai thread
        if (!human) {

            aiThread.interrupt();
            while (aiThread.isAlive())
                try {
                    aiThread.join();
                } catch (InterruptedException ignored) {
                }
        }
        // stop player thread
        playerThread.interrupt();
        while (playerThread.isAlive())
            try {
                playerThread.join();
            } catch (InterruptedException ignored) {
            }
    }


    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        synchronized (this) {
            if (this.playerState != PlayerState.INIT) {
                return;
            }
            if (this.table.isEmptySlot(slot)) {
                return;
            }
            if (this.actions.size() >= 3) {
                return;
            }
            this.actions.add(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        synchronized (this) {
            int ignored = table.countCards();
            env.ui.setScore(id, ++score);
            this.notify();
            this.playerState = PlayerState.SCORED;
        }
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized (this) {
            this.notify();
            this.playerState = PlayerState.PENALIZED;
        }
    }

    /**
     * Returns the total score of the player
     */
    public int getScore() {
        return score;
    }
}
