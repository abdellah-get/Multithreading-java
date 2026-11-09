package booking;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Ce test doit échouer. L'échec est la preuve de la course.
 */
class CourseNaiveTest {

    private static final int PLACES = 200;
    private static final int CLIENTS = 1_000;

    @Tag("course")
    @Test
    void laMemePlacePeutEtreVenduePlusieursFois() throws Exception {
        List<Essai> essais = new ArrayList<>();
        for (int numero = 1; numero <= 5; numero++) {
            essais.add(jouer(numero));
        }

        for (Essai essai : essais) {
            System.out.println(essai);
        }

        boolean courseVue = false;
        for (Essai essai : essais) {
            if (essai.course()) {
                courseVue = true;
            }
        }
        assertFalse(courseVue, () -> "course vue :\n" + essais);
    }

    private static Essai jouer(int numero) throws Exception {
        NaiveBookingEngine moteur = new NaiveBookingEngine(PLACES);
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
        return new Essai(numero, confirmations, placeLaPlusVendue, fois);
    }

    private record Essai(int numero, int confirmations, int place, int fois) {
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
