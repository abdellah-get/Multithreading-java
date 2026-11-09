package booking;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simule un paiement lent.
 * Par défaut : attend entre 50 et 300 ms, échoue environ 10 % du temps.
 */
public class PaymentGateway {

    private final int minSleepMs;
    private final int maxSleepExclusive;
    private final int echecSurDix;

    public PaymentGateway() {
        this(50, 301, 1);
    }

    public PaymentGateway(int minSleepMs, int maxSleepExclusive) {
        this(minSleepMs, maxSleepExclusive, 1);
    }

    public PaymentGateway(int minSleepMs, int maxSleepExclusive, int echecSurDix) {
        this.minSleepMs = minSleepMs;
        this.maxSleepExclusive = maxSleepExclusive;
        this.echecSurDix = echecSurDix;
    }

    public boolean charge(String customerId) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Thread.sleep(random.nextInt(minSleepMs, maxSleepExclusive));
        return random.nextInt(10) >= echecSurDix;
    }
}
