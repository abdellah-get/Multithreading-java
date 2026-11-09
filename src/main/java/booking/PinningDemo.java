package booking;

import java.util.concurrent.locks.ReentrantLock;

/**
 * En Java 21, dormir dans un synchronized colle la virtual thread à son carrier.
 * Avec ReentrantLock, on relâche avant le paiement : pas de pinning pendant charge().
 *
 * java -cp target/classes booking.PinningDemo
 */
public final class PinningDemo {

    private final Object moniteur = new Object();
    private final ReentrantLock verrou = new ReentrantLock();
    private final PaymentGateway payments = new PaymentGateway(50, 80);

    public static void main(String[] args) throws Exception {
        PinningDemo demo = new PinningDemo();
        System.out.println("1) paiement dans synchronized : pinning possible");
        demo.paiementDansSynchronized();
        System.out.println("2) paiement apres ReentrantLock : pas de pinning pendant charge()");
        demo.paiementApresLock();
    }

    void paiementDansSynchronized() throws InterruptedException {
        synchronized (moniteur) {
            // Pendant ces 50-80 ms, la virtual thread reste collée au carrier.
            payments.charge("client-sync");
        }
        System.out.println("   synchronized + charge : termine");
    }

    void paiementApresLock() throws InterruptedException {
        verrou.lock();
        try {
            // On ne fait ici que le travail court sous verrou.
        } finally {
            verrou.unlock();
        }
        // Le paiement est dehors : le carrier peut servir une autre virtual thread.
        payments.charge("client-lock");
        System.out.println("   ReentrantLock puis charge : termine");
    }
}
