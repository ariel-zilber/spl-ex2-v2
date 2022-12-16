package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TableTest {

    Table table;
    private Integer[] slotToCard;
    private Integer[] cardToSlot;

    @BeforeEach
    void setUp() {

        Properties properties = new Properties();
        properties.put("Rows", "2");
        properties.put("Columns", "2");
        properties.put("FeatureSize", "3");
        properties.put("FeatureCount", "4");
        properties.put("TableDelaySeconds", "0");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("PlayerKeys1", "81,87,69,82");
        properties.put("HumanPlayers", "2");

        MockLogger logger = new MockLogger();
        Config config = new Config(logger, properties);
        slotToCard = new Integer[config.tableSize];
        cardToSlot = new Integer[config.deckSize];

        Env env = new Env(logger, config, new MockUserInterface(), new MockUtil());
        table = new Table(env, slotToCard, cardToSlot);
    }

    private int fillSomeSlots() {
        slotToCard[1] = 3;
        slotToCard[2] = 5;
        cardToSlot[3] = 1;
        cardToSlot[5] = 2;

        return 2;
    }

    private void fillAllSlots() {
        for (int i = 0; i < slotToCard.length; ++i) {
            slotToCard[i] = i;
            cardToSlot[i] = i;
        }
    }

    private void placeSomeCardsAndAssert() {
        table.placeCard(8, 2);

        assertEquals(8, (int) slotToCard[2]);
        assertEquals(2, (int) cardToSlot[8]);
    }

    @Test
    void countCards_NoSlotsAreFilled() {

        assertEquals(0, table.countCards());
    }

    @Test
    void countCards_SomeSlotsAreFilled() {

        int slotsFilled = fillSomeSlots();
        assertEquals(slotsFilled, table.countCards());
    }

    @Test
    void countCards_AllSlotsAreFilled() {

        fillAllSlots();
        assertEquals(slotToCard.length, table.countCards());
    }

    @Test
    void placeCard_SomeSlotsAreFilled() {

        fillSomeSlots();
        placeSomeCardsAndAssert();
    }

    @Test
    void placeCard_AllSlotsAreFilled() {
        fillAllSlots();
        placeSomeCardsAndAssert();
    }

    // place token related tests:
    @Test
    void placeToken_emptySlot() {

        // make all slots empty
        Arrays.fill(slotToCard, null);
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.countTokens(slot), 0);
        }

        // attempt to place token on empty slot
        for(int slot=0;slot<table.slotToCard.length;slot++){
            table.placeToken(0,slot);
            assertEquals(table.countTokens(slot), 0);
        }

    }

    @Test
    void placeToken_notEmptySlot() {
        fillAllSlots();

        // slots should not have placed tokens
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.countTokens(slot), 0);
        }

        // place tokens for a single player
        for(int slot=0;slot<table.slotToCard.length;slot++){
            table.placeToken(0,slot);
            assertEquals(table.countTokens(slot), 1);
        }

        // repeat for second player
        for(int slot=0;slot<table.slotToCard.length;slot++){
            table.placeToken(1,slot);
            assertEquals(table.countTokens(slot), 2);
        }
    }

    @Test
    void placeToken_duplicated() {
        fillAllSlots();

        // slots should not have placed tokens
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.countTokens(slot), 0);
        }

        // should not be able to place token more then once
        for(int slot=0;slot<table.slotToCard.length;slot++){
            table.placeToken(0,slot);
            table.placeToken(0,slot);
            assertEquals(table.countTokens(slot), 1);
        }

    }
    // remove token related tests:

    @Test
    void removeToken_emptySlot() {
        //
        Arrays.fill(slotToCard, null);
        // slots should not have placed tokens
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.countTokens(slot), 0);
        }

        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.removeToken(0,slot),false);
        }

    }

    @Test
    void removeToken_notEmptySlotWithPlacedToken() {

        fillAllSlots();

        // retry with every slot
        for(int slot=0;slot<table.slotToCard.length;slot++){

            // slot should not have any placed token
            assertEquals(table.countTokens(slot), 0);

            // place token on table
            table.placeToken(0,slot);

            // token should have been placed
            assertEquals(table.countTokens(slot), 1);

            // remove token
            assertEquals(table.removeToken(0,slot),true);

            // token should have been removed
            assertEquals(table.countTokens(slot), 0);

        }
    }

    @Test
    void removeToken_notEmptySlotWithoutPlacedToken() {
        fillAllSlots();

        // slots should not have placed tokens
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.countTokens(slot), 0);
        }

        // place tokens for a single player
        for(int slot=0;slot<table.slotToCard.length;slot++){
            table.placeToken(0,slot);
            assertEquals(table.countTokens(slot), 1);
        }

        // attempt to remove tokens for second player
        for(int slot=0;slot<table.slotToCard.length;slot++){
            assertEquals(table.removeToken(1,slot),false);
        }

    }

    static class MockUserInterface implements UserInterface {
        @Override
        public void placeCard(int card, int slot) {}
        @Override
        public void removeCard(int slot) {}
        @Override
        public void setCountdown(long millies, boolean warn) {}
        @Override
        public void setElapsed(long millies) {}
        @Override
        public void setScore(int player, int score) {}
        @Override
        public void setFreeze(int player, long millies) {}
        @Override
        public void placeToken(int player, int slot) {}
        @Override
        public void removeTokens() {}
        @Override
        public void removeTokens(int slot) {}
        @Override
        public void removeToken(int player, int slot) {}
        @Override
        public void announceWinner(int[] players) {}
    };

    static class MockUtil implements Util {
        @Override
        public int[] cardToFeatures(int card) {
            return new int[0];
        }

        @Override
        public int[][] cardsToFeatures(int[] cards) {
            return new int[0][];
        }

        @Override
        public boolean testSet(int[] cards) {
            return false;
        }

        @Override
        public List<int[]> findSets(List<Integer> deck, int count) {
            return null;
        }
    }

    static class MockLogger extends Logger {
        protected MockLogger() {
            super("", null);
        }
    }
}
