package bguspl.set.ex;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
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


    private Dealer dealer;


    private final BlockingQueue<Integer> actions;
    private final Object lock;

    enum PlayerState{
        INIT,PENALIZED,SCORED
    }


    private PlayerState timeToWait;
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
        this.dealer=dealer;
        this.actions= new LinkedBlockingDeque<>(3);
        this.lock=new Object();
        this.timeToWait=PlayerState.INIT;
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
            // consume form queue
            try {
                int slot=this.actions.take();
                this.table.keyPressed(this.id,slot);

                if(this.table.getPlayerCards(id).size()==3){

                    // playerThread.wait();
                    synchronized (this.dealer){
                        this.dealer.notify();
                    }

                    //
                    synchronized (this.lock){
                        this.lock.wait();
                    // env.config.penaltyFreezeMillis
                    }

                    switch (this.timeToWait){
                        case INIT:
                            break;
                        case SCORED:
                            updateScoreTimeout(env.config.pointFreezeMillis);
                            this.timeToWait=PlayerState.INIT;
                            break;
                        case PENALIZED:
                            updateScoreTimeout(env.config.penaltyFreezeMillis);
                            this.timeToWait=PlayerState.INIT;
                            break;
                    }

                }


            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }


            // place

            // if()
            //
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }
    private  void  updateScoreTimeout(long time){
        long end=System.currentTimeMillis()+time;
        long diff=time;
        while(diff>0){
            this.env.ui.setFreeze(id,diff+1000);
            diff= end-System.currentTimeMillis();
        }
        this.env.ui.setFreeze(id,0);
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
                // TODO implement player key press simulator
                int numOfKeys=env.config.playerKeys(id).length;
                int randomSlot = ThreadLocalRandom.current().nextInt(0, numOfKeys);
                keyPressed(randomSlot);

                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
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
        System.out.println("[debug] Player.terminate");
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {

        if(this.timeToWait!=PlayerState.INIT){
            return;
        }
        if(this.table.slotToCard[slot]==null){
            return;
        }

        // TODO implement
        System.out.println("[debug] Current keyPressed thread - " + Thread.currentThread().getName());
        System.out.println("[debug] Current keyPressed getState:" + playerThread.getState());
        System.out.println("[debug] Player.keyPressed:"+slot+" player id:"+this.id);
        System.out.println("[debug] Player.q size:"+this.actions.size());
        if(this.actions.size()<3){
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
        // TODO implement
        System.out.println("[debug] Player.point for player:"+playerThread.getName());
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
       // notifyAll();
        synchronized (this.lock){
            this.lock.notify();
            this.actions.clear();
            this.timeToWait=PlayerState.SCORED;
        }

    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        synchronized (this.lock){
            this.lock.notify();
            this.actions.clear();
            this.timeToWait=PlayerState.PENALIZED;
        }
//        System.out.println("[debug] Current penalty thread - " + Thread.currentThread().getName());
//
//        System.out.println("[debug] Player.penalty  start id:"+this.id);
//        // TODO implement
//        try {
//            Thread.sleep(env.config.penaltyFreezeMillis);
//        }catch (InterruptedException ignored) {}
//        System.out.println("[debug] Player.penalty  stop id:"+this.id);

    }

    public int getScore() {
        System.out.println("[debug] Player.getScore:");

        return score;
    }
}
