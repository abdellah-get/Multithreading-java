package booking;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mesure seulement les appels à book, pas la création des threads.
 * Lancer : java -cp target/classes booking.Vitesse
 */
public final class Vitesse {

    private static final int THREADS = 32;
    private static final int PAR_THREAD = 20_000;
    private static final int TOURS = 10;

    @FunctionalInterface
    interface Fabrique {
        BookingEngine creer(int places);
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("places differentes (ms)");
        ligne("synchronized   ", places -> new SynchronizedBookingEngine(places), true);
        ligne("verrou par place", places -> new LockPerSeatBookingEngine(places), true);
        ligne("compareAndSet  ", places -> new CasBookingEngine(places), true);
        System.out.println("memes places (ms)");
        ligne("synchronized   ", places -> new SynchronizedBookingEngine(places), false);
        ligne("verrou par place", places -> new LockPerSeatBookingEngine(places), false);
        ligne("compareAndSet  ", places -> new CasBookingEngine(places), false);
    }

    private static void ligne(String nom, Fabrique fabrique, boolean placesDifferentes) throws InterruptedException {
        long ns = 0;
        for (int tour = 0; tour < TOURS; tour++) {
            ns += unTour(fabrique, placesDifferentes);
        }
        System.out.println(nom + " : " + (ns / 1_000_000));
    }

    private static long unTour(Fabrique fabrique, boolean placesDifferentes) throws InterruptedException {
        int places = placesDifferentes ? THREADS * PAR_THREAD : 8;
        BookingEngine moteur = fabrique.creer(places);
        CountDownLatch depart = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(THREADS);

        try (ExecutorService clients = Executors.newFixedThreadPool(THREADS)) {
            for (int thread = 0; thread < THREADS; thread++) {
                int debut = thread * PAR_THREAD;
                clients.submit(() -> {
                    try {
                        depart.await();
                        for (int i = 0; i < PAR_THREAD; i++) {
                            int seatId = placesDifferentes ? debut + i : i % places;
                            moteur.book(new BookingRequest("client", seatId));
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    } finally {
                        fin.countDown();
                    }
                });
            }
            long debut = System.nanoTime();
            depart.countDown();
            fin.await();
            return System.nanoTime() - debut;
        }
    }
}
