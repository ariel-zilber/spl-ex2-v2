package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    private ConcurrentLinkedQueue<Integer> setClaims;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;


    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        setClaims = new ConcurrentLinkedQueue<>();
    }

    public void claimSet(Player player) {
        this.setClaims.add(player.id);
    }

    private void startPlayers() {
        for (int i = 0; i < players.length; i++) {
            System.out.println("[debug] Dealer run() player-size:" + this.players.length);
            new Thread(players[i]).start();
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.println("[debug] Dealer run() player-size:" + this.players.length);
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        startPlayers();
        System.out.println("[debug] Dealer run() player-size:" + this.players.length);

        while (!shouldFinish()) {
            //   Collections.shuffle(deck);
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(false);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
    }


    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        updateTimerDisplay(true);
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable(); //
            placeCardsOnTable(); //
            terminate = shouldFinish();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        System.out.println("[debug] Dealer.terminate:");

        terminate = true;
        for (Player p : players) {
            p.terminate();
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }


    private void removeCardsFromTableForPlayer(int player) {
        // TODO implement

        List<Integer> playerCards = new ArrayList<>(this.table.getPlayerCards(player));
        // case card was remove
        if (playerCards.size() < 3) {
            this.players[player].notify();
            return;
        }
//
//        //
//        System.out.println("[debug] Dealer.removeCardsFromTable playerCards:" + playerCards.size());
//        for (int i = 0; i < this.deck.size(); i++) {
//            System.out.println("[debug] Dealer.removeCardsFromTable i:" + this.deck.get(i) + " for player:" + player);
//        }
        List<int[]> setSearch = this.env.util.findSets(playerCards, 1);

        if (setSearch.size() > 0) {
            for (int card : setSearch.get(0)) {
//                System.out.println("[debug] Dealer.removeCardsFromTable card:" + card + "|slot" + this.table.cardToSlot[card]);

                // remove the card
                this.table.removeCard(this.table.cardToSlot[card]); // removeCardFromPlayerSet call
            }
            this.players[player].point();

        } else {
            // remove token
            for (int card : playerCards) {
//                System.out.println("[debug] Dealer.removeCardsFromTable card:" + card);
//                System.out.println("[debug] Dealer.removeCardsFromTable slot:" + this.table.cardToSlot[card]);
//                System.out.println("[debug] Dealer.removeCardsFromTable myThread:" + Thread.currentThread().getName());
                int slot = this.table.cardToSlot[card];
                this.table.removeToken(player, slot);// removeCardFromPlayerSet call
            }

            //
            this.players[player].penalty();
            //
        }

    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        synchronized (this.table) {

            while (this.setClaims.size() > 0) {
                Integer player = this.setClaims.poll();
                updateTimerDisplay(false);

                removeCardsFromTableForPlayer(player);

            }
        }
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        for (int i = 0; i < this.table.slotToCard.length; i++) {

            // case deck is empty
            if (this.deck.size() == 0) {
                return;
            }

            // place case empty
            if (this.table.slotToCard[i] == null) {
                // remove first
                int card = this.deck.get(0);
                this.deck.remove(0);
                this.table.placeCard(card, i);
            }
        }

    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        // todo change to busy wait
        try {
            synchronized (this) {
                this.wait(env.config.tableDelayMillis);
            }
        } catch (InterruptedException ignored) {
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if (reset) {
            this.reshuffleTime = System.currentTimeMillis() + this.env.config.turnTimeoutMillis;
        }
        long diff = this.reshuffleTime - System.currentTimeMillis();
        this.env.ui.setCountdown(Math.max(diff, 0), diff < this.env.config.turnTimeoutWarningMillis);
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for (int i = 0; i < this.table.slotToCard.length; i++) {
            //
            Integer currCard = this.table.slotToCard[i];
            if (currCard == null) {
                continue;
            }
            this.deck.add(currCard);
            System.out.println("[debug] removeAllCardsFromTable currCard:" + currCard);
            this.table.removeCard(i);
        }
    }


    private int[] getWinnersIds() {
        int maxScore = players[0].getScore();
        ArrayList<Integer> winners = new ArrayList<>();
        for (Player player : players) {
            maxScore = Math.max(maxScore, player.getScore());
        }
        for (Player player : players) {
            if (player.getScore() == maxScore) {
                winners.add(player.id);
            }
        }
        return winners.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {


        this.env.ui.announceWinner(getWinnersIds());
        // TODO implement
    }
    public List<Integer> getDeck()
    {
        return this.deck;
    }
}
