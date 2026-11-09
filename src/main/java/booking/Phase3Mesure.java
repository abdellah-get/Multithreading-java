package booking;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compare 50 threads plateforme et 10 000 virtual threads.
 *
 * java -cp target/classes booking.Phase3Mesure
 */
public final class Phase3Mesure {

    private static final int PLACES = 200;
    private static final int CLIENTS = 10_000;

    public static void main(String[] args) throws Exception {
        System.out.println("Mesure phase 3 : " + CLIENTS + " clients, " + PLACES + " places");
        System.out.println("Paiement court (5-20 ms) pour garder la mesure raisonnable.");
        System.out.println();

        Resultat plateforme = lancer("50 threads plateforme",
                Executors.newFixedThreadPool(50));
        Resultat virtual = lancer("virtual threads",
                Executors.newVirtualThreadPerTaskExecutor());

        System.out.println();
        System.out.println("resume");
        System.out.println("plateforme : " + plateforme);
        System.out.println("virtual    : " + virtual);
        System.out.println();
        System.out.println("Les virtual threads aident ici parce que charge() dort.");
        System.out.println("Si charge() calculait sans dormir, le gain disparaîtrait.");
    }

    private static Resultat lancer(String nom, ExecutorService clients) throws Exception {
        PaymentGateway payments = new PaymentGateway(5, 21);
        PipelineBookingEngine moteur = new PipelineBookingEngine(PLACES, payments);
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        AtomicInteger pic = new AtomicInteger(threads.getThreadCount());
        Thread observateur = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                pic.updateAndGet(actuel -> Math.max(actuel, threads.getThreadCount()));
                try {
                    Thread.sleep(5);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "observateur-threads");
        observateur.setDaemon(true);
        observateur.start();

        long debut = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(CLIENTS);
        try {
            for (int i = 0; i < CLIENTS; i++) {
                int seatId = i % PLACES;
                String customerId = "client-" + i;
                futures.add(clients.submit(() ->
                        moteur.book(new BookingRequest(customerId, seatId))));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            clients.shutdownNow();
            moteur.shutdown();
            observateur.interrupt();
        }
        long ms = (System.nanoTime() - debut) / 1_000_000;
        Resultat resultat = new Resultat(nom, ms, pic.get(), moteur.stats().confirmed(),
                moteur.stats().rejected(), moteur.stats().expired());
        System.out.println(resultat);
        return resultat;
    }

    private record Resultat(String nom, long ms, int picThreads, long confirmed, long rejected, long expired) {
        @Override
        public String toString() {
            return nom + " : " + ms + " ms, picThreads=" + picThreads
                    + ", confirmed=" + confirmed
                    + ", rejected=" + rejected
                    + ", expired=" + expired;
        }
    }
}
