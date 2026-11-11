package booking;


import booking.engine.CasBookingEngine;
import booking.engine.LockPerSeatBookingEngine;
import booking.engine.NaiveBookingEngine;
import booking.engine.SynchronizedBookingEngine;
import booking.model.BookingEngine;
import booking.model.BookingRequest;
import booking.model.BookingResult;
import booking.model.Confirmed;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Les quatre versions : une sans correction, trois avec correction.
 */
class ToutesLesVersionsTest {

    private static final int PLACES = 200;
    private static final int CLIENTS = 1_000;

    @Test
    void sansCorrection() throws Exception {
        boolean courseVue = false;
        for (int numero = 1; numero <= 5; numero++) {
            Essai essai = jouer(numero, new NaiveBookingEngine(PLACES));
            System.out.println("sans correction  " + essai);
            if (essai.course()) {
                courseVue = true;
            }
        }
        assertTrue(courseVue, "la version sans correction doit montrer une double vente");
    }

    @Test
    void avecSynchronized() throws Exception {
        verifierVingtFois("synchronized    ", () -> new SynchronizedBookingEngine(PLACES));
    }

    @Test
    void avecUnVerrouParPlace() throws Exception {
        verifierVingtFois("verrou par place", () -> new LockPerSeatBookingEngine(PLACES));
    }

    @Test
    void avecCompareAndSet() throws Exception {
        verifierVingtFois("compareAndSet   ", () -> new CasBookingEngine(PLACES));
    }

    private static void verifierVingtFois(String nom, Moteur neuf) throws Exception {
        for (int numero = 1; numero <= 20; numero++) {
            Essai essai = jouer(numero, neuf.creer());
            System.out.println(nom + " " + essai);
            assertEquals(PLACES, essai.confirmations(), essai.toString());
            assertEquals(1, essai.fois(), essai.toString());
            assertEquals(PLACES, essai.venduesDansLeMoteur(), essai.toString());
        }
    }

    private static Essai jouer(int numero, BookingEngine moteur) throws Exception {
        List<Future<BookingResult>> resultats = new ArrayList<>();

        try (ExecutorService clients = Executors.newFixedThreadPool(32)) {
            for (int i = 0; i < CLIENTS; i++) {
                int seatId = i % PLACES;
                String customerId = "client-" + i;
                resultats.add(clients.submit(() ->
                        moteur.book(new BookingRequest(customerId, seatId))));
            }
        }

        int confirmations = 0;
        int[] ventes = new int[PLACES];
        for (Future<BookingResult> resultat : resultats) {
            if (resultat.get() instanceof Confirmed confirmed) {
                confirmations++;
                ventes[confirmed.seatId()]++;
            }
        }

        int placeLaPlusVendue = 0;
        int fois = ventes[0];
        for (int id = 1; id < ventes.length; id++) {
            if (ventes[id] > fois) {
                fois = ventes[id];
                placeLaPlusVendue = id;
            }
        }
        return new Essai(numero, confirmations, placeLaPlusVendue, fois, moteur.soldCount());
    }

    private interface Moteur {
        BookingEngine creer();
    }

    private record Essai(int numero, int confirmations, int place, int fois, int venduesDansLeMoteur) {
        boolean course() {
            return confirmations > PLACES || fois > 1;
        }

        @Override
        public String toString() {
            return "essai " + numero + " : " + confirmations
                    + " confirmations, place " + place + " vendue " + fois + " fois";
        }
    }
}
