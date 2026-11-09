package booking;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;

/**
 * Réservation en deux temps : retenir la place, puis payer.
 * FREE -> HELD -> SOLD, ou retour à FREE si le paiement échoue ou si 2 secondes passent.
 */
public final class PipelineBookingEngine implements BookingEngine {

    private static final int FILE_MAX = 64;
    private static final int ATTENTE_FILE_MS = 150;
    private static final int WORKERS = 8;
    private static final long RETENUE_MS = 2_000;
    private static final long SHUTDOWN_TIMEOUT_MS = 5_000;

    private final Seat[] seats;
    private final AtomicReferenceArray<Place> places;
    private final PaymentGateway payments;
    private final BlockingQueue<PaymentTask> file = new ArrayBlockingQueue<>(FILE_MAX);
    private final AtomicLong tokens = new AtomicLong();
    private final AtomicInteger paiementsEnCours = new AtomicInteger();
    private final Stats stats = new Stats();
    private final ExecutorService workers = Executors.newFixedThreadPool(WORKERS, runnable -> {
        Thread thread = new Thread(runnable, "paiement");
        return thread;
    });
    private final ScheduledExecutorService expirations = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "expiration");
        return thread;
    });
    private final Thread rapport;
    private volatile boolean ouvert = true;

    public PipelineBookingEngine(int seatCount) {
        this(seatCount, new PaymentGateway());
    }

    public PipelineBookingEngine(int seatCount, PaymentGateway payments) {
        this.payments = payments;
        seats = new Seat[seatCount];
        places = new AtomicReferenceArray<>(seatCount);
        for (int id = 0; id < seatCount; id++) {
            seats[id] = new Seat(id);
            places.set(id, Place.FREE);
        }
        for (int i = 0; i < WORKERS; i++) {
            workers.submit(() -> bouclePaiement());
        }
        rapport = new Thread(() -> afficher(), "rapport");
        rapport.start();
    }

    @Override
    public BookingResult book(BookingRequest request) {
        if (!ouvert) {
            stats.rejected.increment();
            return new Rejected("ferme");
        }
        int id = request.seatId();
        if (id < 0 || id >= places.length()) {
            stats.rejected.increment();
            return new Rejected("place inconnue");
        }

        Place held = Place.held(request.customerId(), tokens.incrementAndGet());
        if (!places.compareAndSet(id, Place.FREE, held)) {
            stats.rejected.increment();
            return new Rejected("deja prise");
        }

        PaymentTask task = new PaymentTask(id, held, new CompletableFuture<>());
        boolean prise;
        try {
            prise = file.offer(task, ATTENTE_FILE_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            prise = false;
        }
        if (!prise) {
            if (places.compareAndSet(id, held, Place.FREE)) {
                stats.rejected.increment();
            }
            return new Rejected("busy");
        }

        expirations.schedule(() -> expirer(id, held, task.resultat()), RETENUE_MS, TimeUnit.MILLISECONDS);
        try {
            return task.resultat().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            stats.rejected.increment();
            return new Rejected("interrompu");
        } catch (Exception exception) {
            stats.rejected.increment();
            return new Rejected("erreur");
        }
    }

    private void bouclePaiement() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PaymentTask task = file.take();
                paiementsEnCours.incrementAndGet();
                try {
                    payerUneTache(task);
                } finally {
                    paiementsEnCours.decrementAndGet();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void payerUneTache(PaymentTask task) {
        boolean paye;
        try {
            paye = payments.charge(task.held().customerId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            paye = false;
        }

        Place held = task.held();
        int id = task.seatId();
        if (paye && !held.tropVieux() && places.compareAndSet(id, held, held.vendue())) {
            stats.confirmed.increment();
            task.resultat().complete(new Confirmed(held.customerId(), id));
            return;
        }
        if (!paye) {
            stats.paymentFailures.increment();
        }
        if (places.compareAndSet(id, held, Place.FREE)) {
            if (paye) {
                stats.expired.increment();
                task.resultat().complete(new Expired(held.customerId(), id));
            } else {
                stats.rejected.increment();
                task.resultat().complete(new Rejected("paiement refuse"));
            }
        }
    }

    private void expirer(int id, Place held, CompletableFuture<BookingResult> resultat) {
        if (places.compareAndSet(id, held, Place.FREE)) {
            stats.expired.increment();
            resultat.complete(new Expired(held.customerId(), id));
        }
    }

    private void afficher() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            System.out.println(stats);
        }
    }

    @Override
    public int soldCount() {
        int count = 0;
        for (int id = 0; id < places.length(); id++) {
            if (places.get(id).state() == SeatState.SOLD) {
                count++;
            }
        }
        return count;
    }

    public Stats stats() {
        return stats;
    }

    /**
     * 1. refuse les nouvelles demandes
     * 2. laisse finir les paiements déjà dans la file
     * 3. arrête les workers, le scheduler et le rapport
     * 4. attend la fin avec un délai
     */
    @Override
    public void shutdown() {
        ouvert = false;
        viderLaFile();
        workers.shutdownNow();
        expirations.shutdownNow();
        rapport.interrupt();
        attendreFin(workers);
        attendreFin(expirations);
        try {
            rapport.join(SHUTDOWN_TIMEOUT_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void viderLaFile() {
        long limite = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SHUTDOWN_TIMEOUT_MS);
        while (System.nanoTime() < limite) {
            if (file.isEmpty() && paiementsEnCours.get() == 0) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void attendreFin(ExecutorService service) {
        try {
            if (!service.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                service.shutdownNow();
                service.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            service.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public static final class Stats {
        final LongAdder confirmed = new LongAdder();
        final LongAdder rejected = new LongAdder();
        final LongAdder expired = new LongAdder();
        final LongAdder paymentFailures = new LongAdder();

        public long confirmed() {
            return confirmed.sum();
        }

        public long rejected() {
            return rejected.sum();
        }

        public long expired() {
            return expired.sum();
        }

        public long paymentFailures() {
            return paymentFailures.sum();
        }

        @Override
        public String toString() {
            return "confirmed=" + confirmed()
                    + " rejected=" + rejected()
                    + " expired=" + expired()
                    + " paymentFailures=" + paymentFailures();
        }
    }

    private record PaymentTask(int seatId, Place held, CompletableFuture<BookingResult> resultat) {
    }

    private static final class Place {
        static final Place FREE = new Place(SeatState.FREE, "", 0, 0);

        private final SeatState state;
        private final String customerId;
        private final long token;
        private final long heldAt;

        private Place(SeatState state, String customerId, long token, long heldAt) {
            this.state = state;
            this.customerId = customerId;
            this.token = token;
            this.heldAt = heldAt;
        }

        static Place held(String customerId, long token) {
            return new Place(SeatState.HELD, customerId, token, System.nanoTime());
        }

        Place vendue() {
            return new Place(SeatState.SOLD, customerId, token, heldAt);
        }

        boolean tropVieux() {
            return System.nanoTime() - heldAt >= TimeUnit.MILLISECONDS.toNanos(RETENUE_MS);
        }

        SeatState state() {
            return state;
        }

        String customerId() {
            return customerId;
        }
    }
}
