package booking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedSwapTest {

    @Test
    void lechangeOrdonneSeTermine() throws Exception {
        OrderedSwap demo = new OrderedSwap();

        Thread alice = new Thread(() -> demo.echangerOrdonne(0, 1), "alice");
        Thread bob = new Thread(() -> demo.echangerOrdonne(1, 0), "bob");
        alice.start();
        bob.start();
        alice.join(3_000);
        bob.join(3_000);

        assertFalse(alice.isAlive(), "alice est encore bloque");
        assertFalse(bob.isAlive(), "bob est encore bloque");
        assertTrue(true);
    }
}
