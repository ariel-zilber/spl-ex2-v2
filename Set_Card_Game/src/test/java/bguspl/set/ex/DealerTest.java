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
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealerTest {

    @Mock
    Player player1;
    @Mock
    Player player2;
    @Mock
    Util util;
    @Mock
    private UserInterface ui;
    @Mock
    private Table table;

    private Dealer dealer;

    @Mock
    private Logger logger;

    void assertInvariants() {
        assertFalse(dealer.isTerminate());
    }

    @BeforeEach
    void setUp() {
        // purposely do not find the configuration files (use defaults here).
        Env env = new Env(logger, new Config(logger, "config2.properties"), ui, util);
        dealer=new Dealer(env,table,new Player[]{player1,player2});
        assertInvariants();
    }

    @AfterEach
    void tearDown() {
//        assertInvariants();
    }

    @Test
    void terminate() {
        Dealer spiedDealer=Mockito.spy(dealer);

        // initialy the dealer is alive
        assertEquals(spiedDealer.isTerminate(),false);

        // terminate the dealer
        spiedDealer.terminate();

        // after termination dealer is terminated
        assertEquals(spiedDealer.isTerminate(),true);
    }

    @Test
    void announceWinners() {

        //
        Dealer spiedDealer=Mockito.spy(dealer);

        // assume player have different ids
        when(player1.getId()).thenReturn(0);
        when(player2.getId()).thenReturn(1);

        // assume players have same score
        when(player1.getScore()).thenReturn(10);
        when(player2.getScore()).thenReturn(10);

        spiedDealer.run();
        verify(ui).announceWinner(new int[]{0,1});

        // assume players have different score (first has larger score)
        spiedDealer=Mockito.spy(dealer);
        when(player1.getScore()).thenReturn(1);
        when(player2.getScore()).thenReturn(0);
        spiedDealer.run();
        verify(ui).announceWinner(new int[]{0});

        // assume players have different score (second has larger score)
        spiedDealer=Mockito.spy(dealer);
        when(player1.getScore()).thenReturn(0);
        when(player2.getScore()).thenReturn(1);
        spiedDealer.run();
        verify(ui).announceWinner(new int[]{1});
    }

}