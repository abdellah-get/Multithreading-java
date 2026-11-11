package booking;


import booking.engine.PipelineBookingEngine;
import booking.model.BookingRequest;
import booking.model.BookingResult;
import booking.payment.PaymentGateway;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ShutdownTest {

    @Test
    void apresShutdownPlusAucunThreadDuMoteur() throws Exception {
        PipelineBookingEngine moteur = new PipelineBookingEngine(50, new PaymentGateway(20, 40));
        List<Future<BookingResult>> resultats = new ArrayList<>();

        try (ExecutorService clients = Executors.newFixedThreadPool(20)) {
            for (int i = 0; i < 40; i++) {
                int seatId = i % 50;
                resultats.add(clients.submit(() ->
                        moteur.book(new BookingRequest("client-" + seatId, seatId))));
            }
        }

        for (Future<BookingResult> resultat : resultats) {
            resultat.get();
        }

        moteur.shutdown();
        Thread.sleep(200);

        List<String> restants = new ArrayList<>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            String nom = thread.getName();
            if (!thread.isAlive()) {
                continue;
            }
            if (nom.equals("paiement") || nom.equals("expiration") || nom.equals("rapport")) {
                restants.add(nom + " daemon=" + thread.isDaemon());
            }
        }

        assertTrue(restants.isEmpty(), "threads encore vivants : " + restants);
    }
}
