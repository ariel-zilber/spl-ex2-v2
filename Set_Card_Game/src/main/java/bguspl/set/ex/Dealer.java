package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.List;
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
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.println("[debug] Dealer run() player-size:"+this.players.length);

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        for(int i=0;i< players.length;i++){
            System.out.println("[debug] Dealer run() player-size:"+this.players.length);
            new Thread(players[i]).start();
        }

        System.out.println("[debug] Dealer run() player-size:"+this.players.length);

        while (!shouldFinish()) {
         //   Collections.shuffle(deck);
            updateTimerDisplay(true);
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
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable(); //
            placeCardsOnTable(); //
            terminate=shouldFinish();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
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
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        for(int player=0;player<players.length;player++){
            List<Integer> playerCards=new ArrayList<>(this.table.getPlayerCards(player));
            if(playerCards.size()<3){
                continue;
            }
            System.out.println("[debug] Dealer.removeCardsFromTable playerCards:"+playerCards.size());
            for(int i=0;i<this.deck.size();i++){
                System.out.println("[debug] Dealer.removeCardsFromTable i:"+this.deck.get(i)+ " for player:"+player);
            }
            List<int[]> setSearch=  this.env.util.findSets(playerCards,1);

            if(setSearch.size()>0){
                for(int card:setSearch.get(0)){
                    System.out.println("[debug] Dealer.removeCardsFromTable card:"+card+"|slot"+this.table.cardToSlot[card]);
                    this.table.removeCard(this.table.cardToSlot[card]);
                }
                this.players[player].point();

            }else{
                // remove token
                for(int card:playerCards){
                    System.out.println("[debug] Dealer.removeCardsFromTable card:"+card);
                    System.out.println("[debug] Dealer.removeCardsFromTable slot:"+this.table.cardToSlot[card]);
                    System.out.println("[debug] Dealer.removeCardsFromTable myThread:"+ Thread.currentThread().getName());
                    int slot=this.table.cardToSlot[card];
                    this.table.removeToken(player,slot);
                }

                //
                this.players[player].penalty();
                //
            }

        }
//
//        for (playerToken:playersTokens) {
//
//            //
//            if(playerToken.size()<3){
//                continue;
//            }
//
//            List<int[]> foundSet=env.util.findIfSetExist(playerToken,1);
//            env.util.findIfSetExist(playerToken,1);
//
//        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        for(int i=0;i<this.table.slotToCard.length;i++){

            // case deck is empty
            if(this.deck.size()==0){return;}

            // place case empty
            if(this.table.slotToCard[i]==null){
                // remove first
                int card=this.deck.get(0);
                this.deck.remove(0);
                this.table.placeCard(card,i);
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
            synchronized (this){
                this.wait(10);
            }
//            Thread.sleep(this.env.config.tableDelayMillis*10000);
        }catch (InterruptedException ignored){}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset){
            this.reshuffleTime=System.currentTimeMillis()+this.env.config.turnTimeoutMillis;
        }
        long diff=this.reshuffleTime-System.currentTimeMillis();
        this.env.ui.setCountdown(Math.max(diff,0),diff<this.env.config.turnTimeoutWarningMillis);
    }


    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        for(int i=0;i<this.table.slotToCard.length;i++){
            //
            Integer currCard=this.table.slotToCard[i];
            if (currCard==null){
                continue;
            }
            this.deck.add(currCard);
            this.table.slotToCard[i]=null;
            System.out.println("[debug] removeAllCardsFromTable currCard:"+currCard );
            this.env.ui.removeCard(i);
            this.env.ui.removeTokens(i);
        }
    }


    private int[] getWinnersIds(){
        int maxScore=players[0].getScore();
        ArrayList<Integer> winners=new ArrayList<>();
        for(Player player:players){
            maxScore=Math.max(maxScore, player.getScore());
        }
        for(Player player:players){
            if(player.getScore()==maxScore){
                winners.add(player.id);
            }
        }
        return winners.stream().mapToInt(i->i).toArray();
    }
    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {


        this.env.ui.announceWinner(getWinnersIds());
        // TODO implement
    }
}
