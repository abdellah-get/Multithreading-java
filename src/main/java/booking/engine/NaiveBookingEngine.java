package booking.engine;


import booking.model.*;
/**
 * Première version, sans protection.
 * book() lit l'état de la place, puis l'écrit plus tard.
 * Entre les deux, un autre thread peut lire la même place encore libre.
 */
public final class NaiveBookingEngine implements BookingEngine {

    private final Seat[] seats;
    private final SeatState[] states;

    public NaiveBookingEngine(int seatCount) {
        seats = new Seat[seatCount];
        states = new SeatState[seatCount];
        for (int id = 0; id < seatCount; id++) {
            seats[id] = new Seat(id);
            states[id] = SeatState.FREE;
        }
    }

    @Override
    public BookingResult book(BookingRequest request) {
        int id = request.seatId();
        if (id < 0 || id >= states.length) {
            return new Rejected("place inconnue");
        }

        if (states[id] == SeatState.FREE) {
            laisserUnAutreThreadPasser();
            states[id] = SeatState.SOLD;
            return new Confirmed(request.customerId(), seats[id].id());
        }
        return new Rejected("deja prise");
    }

    @Override
    public int soldCount() {
        int count = 0;
        for (SeatState state : states) {
            if (state == SeatState.SOLD) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void shutdown() {
    }

    /**
     * Ouvre une petite fenêtre entre la lecture et l'écriture.
     * La course existe aussi sans cette pause, mais elle est alors
     * parfois trop rapide pour apparaître à chaque lancement.
     */
    private static void laisserUnAutreThreadPasser() {
        try {
            Thread.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
