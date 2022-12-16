package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    public List<Integer> getTableCards() {
        List<Integer> tableCards = new ArrayList<>();
        for (Integer integer : this.slotToCard) {
            if (integer != null) {
                tableCards.add(integer);
            }
        }
        return tableCards;
    }

    private final List<List<Integer>> playerCards;
    private final Boolean[][] selectedSlotsByPlayer;
    private final ReentrantLock lock;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        lock = new ReentrantLock(true);

        // generate empty array list
        playerCards = new ArrayList<>();
        selectedSlotsByPlayer = new Boolean[env.config.players][env.config.tableSize];

        //
        for (int player = 0; player < env.config.players; player++) {
            playerCards.add(new ArrayList<>());
            Arrays.fill(selectedSlotsByPlayer[player], Boolean.FALSE);
        }
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     *
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        this.env.ui.placeCard(card, slot);
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

    }

    /**
     * Checks whenever a given slot is empty
     * @param slot
     * @return
     */
    public boolean isEmptySlot(int slot) {
        return this.slotToCard[slot] == null;
    }

    /**
     *  Remove mapping for a card to a slot
     * @param slot
     */
    private void removeSlotMapping(int slot) {
        int card = this.slotToCard[slot];
        this.slotToCard[slot] = null;
        this.cardToSlot[card] = null;
    }

    private void removeCardFromPlayerSet(int player, int card) {
        int index = this.playerCards.get(player).indexOf(card);
        if (index != -1) {
            this.playerCards.get(player).remove(index);
        }
    }

    public List<Integer> getPlayerCards(int player) {
        return playerCards.get(player);
    }

    ;

    /**
     * Removes a card from a grid slot on the table.
     *
     * @param slot - the slot from which to remove the card.
     */

    public void removeCard(int slot) {
        this.lock.lock();
        this.env.ui.removeCard(slot);
        for (int player = 0; player < env.config.players; player++) {
            removeToken(player, slot);
        }

        removeSlotMapping(slot);
        this.lock.unlock();

        try {
            Thread.sleep(env.config.tableDelayMillis);

        } catch (InterruptedException ignored) {
        }

    }

    /***
     * Counts total tokens placed on a given slot
     * @param slot
     * @return
     */
    public int countTokens(int slot) {
        int slotCount = 0;
        for (int player = 0; player < env.config.players; player++) {
            for (int i = 0; i < playerCards.get(player).size(); i++) {
                if (playerCards.get(player).get(i) == slot) {
                    slotCount += 1;
                }
            }
        }
        return slotCount;
    }

    /**
     * Places a player token on a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if (this.slotToCard[slot] == null) {
            return;
        }
        if (!selectedSlotsByPlayer[player][slot]) {
            this.playerCards.get(player).add(this.slotToCard[slot]);
            this.env.ui.placeToken(player, slot);
            selectedSlotsByPlayer[player][slot] = true;
        }
    }

    /**
     * Presses a key and places or removes a slot on the table
     * @param player
     * @param slot
     */
    public void keyPressed(int player, int slot) {
        lock.lock();
        if (!removeToken(player, slot)) {
            placeToken(player, slot);
        }
        lock.unlock();
    }


    /**
     * Removes a token of a player from a grid slot.
     *
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (selectedSlotsByPlayer[player][slot]) {
            this.env.ui.removeToken(player, slot);
            if (this.slotToCard[slot] == null) {
                return false;
            }
            removeCardFromPlayerSet(player, this.slotToCard[slot]);
            selectedSlotsByPlayer[player][slot] = false;
            return true;
        }

        return false;
    }
}
