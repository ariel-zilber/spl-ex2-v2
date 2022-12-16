package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    private final ConcurrentLinkedQueue<Integer> setClaims;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;
    private long startTime = Long.MAX_VALUE;


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

    public boolean isTerminate() {
        return this.terminate;
    }

    private void startPlayers() {
        for (Player player : players) {
            new Thread(player).start();
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        startPlayers();

        while (!shouldFinish()) {
            do {
                Collections.shuffle(deck);

            } while (this.env.util.findSets(deck, 1).size() <= 0);

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
            if (this.env.config.turnTimeoutMillis <= 0) {
                terminate = shouldFinish() || noMoreMovesOnTable();
            } else {
                terminate = shouldFinish();
            }
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        terminate = true;
        for (int i = players.length - 1; i >= 0; i--) {
            players[i].terminate();
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


    /**
     * Checks whenever the table has available moves remaining
     * @return
     */
    private boolean noMoreMovesOnTable() {
        return env.util.findSets(this.table.getTableCards(), 1).size() == 0;
    }

    private void removeCardsFromTableForPlayer(int player) {
        List<Integer> playerCards = new ArrayList<>(this.table.getPlayerCards(player));

        // case card was remove
        if (playerCards.size() < 3) {
            synchronized (this.players[player]){
                this.players[player].notify();
            }
            return;
        }

        List<int[]> setSearch = this.env.util.findSets(playerCards, 1);

        if (setSearch.size() > 0) {
            for (int card : setSearch.get(0)) {
                this.table.removeCard(this.table.cardToSlot[card]);
            }
            this.players[player].point();
            this.startTime = System.currentTimeMillis();

        } else {
            // remove token
            for (int card : playerCards) {
                int slot = this.table.cardToSlot[card];
                this.table.removeToken(player, slot);
            }
            this.players[player].penalty();
            //
        }

    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
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
        if (this.env.config.turnTimeoutMillis > 0) {
            if (reset) {
                this.reshuffleTime = System.currentTimeMillis() + this.env.config.turnTimeoutMillis;
            }
            long diff = this.reshuffleTime - System.currentTimeMillis();
            this.env.ui.setCountdown(Math.max(diff, 0), diff < this.env.config.turnTimeoutWarningMillis);

        } else if (this.env.config.turnTimeoutMillis == 0) {
            if (reset) {
                this.startTime = System.currentTimeMillis();
            }
            long diff = System.currentTimeMillis() - this.startTime;
            this.env.ui.setCountdown(diff, false);

        }
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0; i < this.table.slotToCard.length; i++) {
            Integer currCard = this.table.slotToCard[i];
            if (currCard == null) {
                continue;
            }
            this.deck.add(currCard);
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
                winners.add(player.getId());
            }
        }

        return winners.stream().mapToInt(i -> i).toArray();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        this.env.ui.announceWinner(getWinnersIds());
        terminate();
    }

}
