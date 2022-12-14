package bguspl.set.ex;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        System.out.println("[debug] Player.run start:" + this.id);
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            // consume form queue
            try {
                int slot = this.actions.take();
                boolean isSet = false;
                this.table.keyPressed(this.id, slot);
                isSet = this.table.getPlayerCards(id).size() == 3;
                if (isSet) {
                    this.dealer.claimSet(this);
                }
                if (isSet) {
                    System.out.println("[debug] run 0:" + playerThread.getName() + " state:" + this.playerState);
                    synchronized (this.dealer) {
                        //
                        this.dealer.notify();
                    }
                    System.out.println("[debug] run 1:" + playerThread.getName() + " state:" + this.playerState);
                    synchronized (this) {
                        System.out.println("[debug] run 2:" + playerThread.getName() + " " + this.playerState);
                        if (this.playerState == PlayerState.INIT) {
                            //
                            System.out.println("[debug] run 2.5:" + playerThread.getName() + " " + this.playerState + " " + playerThread.getState() + " " + this.table.getPlayerCards(id).size());
                            this.wait();
                        }
                        System.out.println("[debug] run 2.6:" + playerThread.getName() + " " + this.playerState + " " + playerThread.getState());
                    }
                    //
                    System.out.println("[debug] run 3:" + playerThread.getName() + " state:" + this.playerState);

                    switch (this.playerState) {
                        case INIT:
                            System.out.println("[debug] run 8:" + playerThread.getName() + " wtf!!!!!!!!");
                            break;
                        case SCORED:
                            System.out.println("[debug] run 4:" + playerThread.getName() + " state:" + this.playerState);
                            updateScoreTimeout(env.config.pointFreezeMillis);
                            break;
                        case PENALIZED:
                            System.out.println("[debug] run 5:" + playerThread.getName() + " state:" + this.playerState);
                            updateScoreTimeout(env.config.penaltyFreezeMillis);
                            break;
                    }
                    System.out.println("[debug] run 6:" + playerThread.getName() + " state:" + this.playerState);
                }
            } catch (InterruptedException ignored) {
            }
        }
        if (!human) try {
            aiThread.join();
        } catch (InterruptedException ignored) {
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        System.out.println("[debug] Player.run end:" + this.id);

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
        // TODO implement player key press simulator
        int numOfKeys = env.config.playerKeys(id).length;
        int randomSlot = ThreadLocalRandom.current().nextInt(0, numOfKeys);
        keyPressed(randomSlot);
        System.out.println("[debug] Player.createArtificialIntelligence running");

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        System.out.println("[debug] Player.createArtificialIntelligence");
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                randomAction();
                try {
                    synchronized (this) {
                        wait(env.config.tableDelayMillis * 10);
                    }
                } catch (InterruptedException ignored) {
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
        // TODO implement
        System.out.println("[debug] Player.terminate:" + this.id);
        terminate = true;
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
        System.out.println("[debug] Current keyPressed thread - " + playerThread.getName());
        System.out.println("[debug] Current keyPressed getState:" + this.id + " " + playerThread.getState() + " " + playerState);
        System.out.println("[debug] Player.keyPressed:" + slot + " player id:" + this.id + " card:" + this.table.slotToCard[slot]);
        System.out.println("[debug] Player.q size:" + this.actions.size() + " thread:" + playerThread.getName());

        if (this.playerState != PlayerState.INIT) {
            return;
        }
        if (this.table.slotToCard[slot] == null) {
            return;
        }
        //
        if (this.actions.size() >= 3) {
            return;
        }
        // TODO implement
        //
        this.actions.add(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {

        synchronized (this) {
            // TODO implement
            System.out.println("[debug] Player.point for player:" + playerThread.getName() + " state:" + playerThread.getState());
            int ignored = table.countCards(); // this part is just for demonstration in the unit tests
            env.ui.setScore(id, ++score);
            this.notify();
            this.playerState = PlayerState.SCORED;
            System.out.println("[debug] Player.point for player end:" + playerThread.getName() + " state:" + playerThread.getState());
        }
        System.out.println("[debug] Player.point for player end:" + playerThread.getName() + " state:" + playerThread.getState());

    }

    public void retry() {
        synchronized (this) {
            this.notify();
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
        System.out.println("[debug] Current penalty thread - " + Thread.currentThread().getName());
//        // TODO implement
    }

    public int getScore() {
        System.out.println("[debug] Player.getScore:");

        return score;
    }
}
