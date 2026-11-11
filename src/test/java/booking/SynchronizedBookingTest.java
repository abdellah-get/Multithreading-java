package booking;


import booking.engine.SynchronizedBookingEngine;
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

/**
 * Le même test que le moteur naïf, répété 20 fois.
 * Il doit passer : 200 ventes, aucune place vendue deux fois.
 */
class SynchronizedBookingTest {

    private static final int PLACES = 200;
    private static final int CLIENTS = 1_000;

    @Test
    void vingtEssaisSansDoubleVente() throws Exception {
        for (int numero = 1; numero <= 20; numero++) {
            Essai essai = jouer(numero);
            System.out.println(essai);
            assertEquals(PLACES, essai.confirmations(), essai.toString());
            assertEquals(1, essai.fois(), essai.toString());
            assertEquals(PLACES, essai.venduesDansLeMoteur(), essai.toString());
        }
    }

    private static Essai jouer(int numero) throws Exception {
        SynchronizedBookingEngine moteur = new SynchronizedBookingEngine(PLACES);
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

    private record Essai(int numero, int confirmations, int place, int fois, int venduesDansLeMoteur) {
        @Override
        public String toString() {
            return "essai " + numero + " : " + confirmations
                    + " confirmations, place " + place + " vendue " + fois + " fois";
        }
    }
}
