package booking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PipelineBookingTest {

    @Test
    void sousChargeLesVentesCorrespondentAuxConfirmations() throws Exception {
        int places = 200;
        int demandes = 250;
        PipelineBookingEngine moteur = new PipelineBookingEngine(places);
        List<Future<BookingResult>> resultats = new ArrayList<>();

        try (ExecutorService clients = Executors.newFixedThreadPool(40)) {
            for (int i = 0; i < demandes; i++) {
                int seatId = i % places;
                String customerId = "client-" + i;
                resultats.add(clients.submit(() ->
                        moteur.book(new BookingRequest(customerId, seatId))));
            }
        } finally {
            moteur.shutdown();
        }

        int confirmed = 0;
        int rejected = 0;
        int expired = 0;
        for (Future<BookingResult> resultat : resultats) {
            BookingResult reponse = resultat.get();
            if (reponse instanceof Confirmed) {
                confirmed++;
            } else if (reponse instanceof Expired) {
                expired++;
            } else if (reponse instanceof Rejected) {
                rejected++;
            }
        }

        System.out.println("resultats confirmed=" + confirmed
                + " rejected=" + rejected
                + " expired=" + expired
                + " sold=" + moteur.soldCount());

        assertEquals(demandes, confirmed + rejected + expired);
        assertEquals(confirmed, moteur.soldCount());
        assertEquals(confirmed, moteur.stats().confirmed());
        assertEquals(expired, moteur.stats().expired());
        assertEquals(rejected, moteur.stats().rejected());
    }

    @Test
    void unPaiementTropTardNeVendPas() throws Exception {
        PaymentGateway lent = new PaymentGateway() {
            @Override
            public boolean charge(String customerId) throws InterruptedException {
                Thread.sleep(2_500);
                return true;
            }
        };
        PipelineBookingEngine moteur = new PipelineBookingEngine(1, lent);
        try {
            BookingResult resultat = moteur.book(new BookingRequest("client-1", 0));
            assertInstanceOf(Expired.class, resultat);
            assertEquals(0, moteur.soldCount());
        } finally {
            moteur.shutdown();
        }
    }
}
