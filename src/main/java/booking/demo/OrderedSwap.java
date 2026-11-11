package booking.demo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Même échange, sans deadlock.
 * On prend toujours d'abord le plus petit numéro de place.
 *
 * java -cp target/classes booking.demo.OrderedSwap
 */
public final class OrderedSwap {

    private final ReentrantLock[] locks = {
            new ReentrantLock(),
            new ReentrantLock()
    };
    private final String[] occupants = {"Alice", "Bob"};

    public static void main(String[] args) throws InterruptedException {
        OrderedSwap demo = new OrderedSwap();

        Thread alice = new Thread(() -> demo.echangerOrdonne(0, 1), "alice");
        Thread bob = new Thread(() -> demo.echangerOrdonne(1, 0), "bob");
        alice.start();
        bob.start();
        alice.join();
        bob.join();

        System.out.println("occupants = [" + demo.occupants[0] + ", " + demo.occupants[1] + "]");
        System.out.println("Pas de deadlock : les deux echanges sont termines.");
    }

    /**
     * first = min(from, to), second = max(from, to).
     * Alice (0,1) et Bob (1,0) prennent tous les deux 0 puis 1.
     * L'attente circulaire devient impossible.
     */
    public void echangerOrdonne(int from, int to) {
        int first = Math.min(from, to);
        int second = Math.max(from, to);

        locks[first].lock();
        try {
            System.out.println(Thread.currentThread().getName()
                    + " tient d'abord la place " + first);
            locks[second].lock();
            try {
                String temp = occupants[from];
                occupants[from] = occupants[to];
                occupants[to] = temp;
                System.out.println(Thread.currentThread().getName()
                        + " echange " + from + " <-> " + to);
            } finally {
                locks[second].unlock();
            }
        } finally {
            locks[first].unlock();
        }
    }

    /**
     * Variante avec tryLock : si le second verrou manque, on lâche le premier et on réessaie.
     */
    public boolean echangerTryLock(int from, int to) throws InterruptedException {
        int first = Math.min(from, to);
        int second = Math.max(from, to);
        while (!Thread.currentThread().isInterrupted()) {
            if (!locks[first].tryLock(100, TimeUnit.MILLISECONDS)) {
                continue;
            }
            try {
                if (locks[second].tryLock(100, TimeUnit.MILLISECONDS)) {
                    try {
                        String temp = occupants[from];
                        occupants[from] = occupants[to];
                        occupants[to] = temp;
                        return true;
                    } finally {
                        locks[second].unlock();
                    }
                }
            } finally {
                locks[first].unlock();
            }
        }
        return false;
    }
}
