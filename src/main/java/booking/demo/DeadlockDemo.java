package booking.demo;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Échange de places avec des verrous croisés : deadlock volontaire.
 *
 * java -cp target/classes booking.demo.DeadlockDemo
 *
 * Option : dans un autre terminal pendant le blocage :
 * jcmd <pid> Thread.print
 */
public final class DeadlockDemo {

    private final ReentrantLock[] locks = {
            new ReentrantLock(),
            new ReentrantLock()
    };
    private final String[] occupants = {"Alice", "Bob"};

    public static void main(String[] args) throws InterruptedException {
        long pid = ProcessHandle.current().pid();
        System.out.println("pid=" + pid);
        System.out.println("Deadlock volontaire : Alice 0->1 et Bob 1->0");
        System.out.println("Option dump : jcmd " + pid + " Thread.print");
        System.out.println();

        DeadlockDemo demo = new DeadlockDemo();
        CountDownLatch pret = new CountDownLatch(2);

        Thread alice = new Thread(() -> demo.echangerNaive(0, 1, pret), "alice");
        Thread bob = new Thread(() -> demo.echangerNaive(1, 0, pret), "bob");
        alice.start();
        bob.start();

        Thread.sleep(1_000);
        afficherDeadlock();

        System.out.println();
        System.out.println("Les threads restent bloques. Ctrl+C pour quitter.");
        alice.join();
        bob.join();
    }

    /**
     * Prend le verrou de from, puis le verrou de to.
     * Alice prend 0 puis veut 1.
     * Bob prend 1 puis veut 0.
     * Chacun tient ce que l'autre attend.
     */
    void echangerNaive(int from, int to, CountDownLatch pret) {
        locks[from].lock();
        try {
            System.out.println(Thread.currentThread().getName()
                    + " tient la place " + from);
            pret.countDown();
            pret.await();
            locks[to].lock();
            try {
                String temp = occupants[from];
                occupants[from] = occupants[to];
                occupants[to] = temp;
                System.out.println(Thread.currentThread().getName() + " a echange");
            } finally {
                locks[to].unlock();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            locks[from].unlock();
        }
    }

    private static void afficherDeadlock() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] ids = bean.findDeadlockedThreads();
        if (ids == null) {
            System.out.println("Aucun deadlock detecte pour l'instant.");
            return;
        }
        System.out.println("=== Deadlock detecte ===");
        ThreadInfo[] infos = bean.getThreadInfo(ids, true, true);
        for (ThreadInfo info : infos) {
            System.out.println(info.getThreadName()
                    + " etat=" + info.getThreadState()
                    + " attend=" + info.getLockName()
                    + " possedePar=" + info.getLockOwnerName());
            System.out.println(info);
        }
    }
}
