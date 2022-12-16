package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import bguspl.set.UserInterface;
import bguspl.set.Util;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import org.mockito.internal.util.reflection.*;
@ExtendWith(MockitoExtension.class)
class PlayerTest {

    Player player;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;
    @Mock
    private Dealer dealer;
    @Mock
    private Logger logger;

    void assertInvariants() {
        assertTrue(player.id >= 0);
        assertTrue(player.getScore() >= 0);
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, "config1.properties"), ui, util);
        player = new Player(env, dealer, table, 0, true);
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
        assertInvariants();
    }

    @Test
    void point() {

        // force table.countCards to return 3
        when(table.countCards()).thenReturn(3); // this part is just for demonstration

        // calculate the expected score for later
        int expectedScore = player.getScore() + 1;

        // call the method we are testing
        player.point();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());

        // check that ui.setScore was called with the player's id and the correct score
        verify(ui).setScore(eq(player.id), eq(expectedScore));
    }
    @Test
    void penalty() {
        // calculate the expected score for later
        int expectedScore = player.getScore() ;

        // call the method we are testing
        player.penalty();

        // check that the score was increased correctly
        assertEquals(expectedScore, player.getScore());
    }


    @Test
    void keyPressed() {

        // assume player has not performed anyh actions yet
        assertEquals(player.actionsToPerform(),0);

        // assume that the first slot is emprt
        when(table.isEmptySlot(0)).thenReturn(true);

        // the player pressed the empty slot
        player.keyPressed(0);

        //  the player cannot press a slot if the slot is empty
        assertEquals(player.actionsToPerform(),0);

        // assume that the slot is not empty
        when(table.isEmptySlot(0)).thenReturn(false);

        // the player press the slot
        player.keyPressed(0);

        // the number of actions to be performed has increased
        assertEquals(player.actionsToPerform(),1);

        // assume that the second slot is empty
        when(table.isEmptySlot(1)).thenReturn(false);

        // the player presses the second slot
        player.keyPressed(1);

        // the slot can be pressed
        assertEquals(player.actionsToPerform(),2);

        // assume that the third slot is empty
        when(table.isEmptySlot(2)).thenReturn(false);

        // the player presses the third slot
        player.keyPressed(2);

        // the slot can be pressed
        assertEquals(player.actionsToPerform(),3);

        // assume that the fourth slot is empty
        when(table.isEmptySlot(3)).thenReturn(false);

        // the player presses the fourth slot
        player.keyPressed(3);

        // the player wont press the fourth button
        assertEquals(player.actionsToPerform(),3);

        // reset the action queue
        player.actions.clear();

        // penalty to player
        player.penalty();

        // attempt to press a button when player is in penalty timeout
        player.keyPressed(0);

        // the key should not be pressed
        assertEquals(player.actionsToPerform(),0);

        // Add point to player
        player.point();

        // attempt to press button when in point timeout
        player.keyPressed(0);

        // should not be able to add point
        assertEquals(player.actionsToPerform(),0);


    }

}