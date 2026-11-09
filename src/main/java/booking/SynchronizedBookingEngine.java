package booking;

/**
 * Même réservation que le moteur naïf, avec un seul verrou pour tout le moteur.
 * Un seul thread à la fois peut exécuter book.
 */
public final class SynchronizedBookingEngine implements BookingEngine {

    private final Seat[] seats;
    private final SeatState[] states;

    public SynchronizedBookingEngine(int seatCount) {
        seats = new Seat[seatCount];
        states = new SeatState[seatCount];
        for (int id = 0; id < seatCount; id++) {
            seats[id] = new Seat(id);
            states[id] = SeatState.FREE;
        }
    }

    @Override
    public synchronized BookingResult book(BookingRequest request) {
        int id = request.seatId();
        if (id < 0 || id >= states.length) {
            return new Rejected("place inconnue");
        }

        if (states[id] == SeatState.FREE) {
            states[id] = SeatState.SOLD;
            return new Confirmed(request.customerId(), seats[id].id());
        }
        return new Rejected("deja prise");
    }

    @Override
    public synchronized int soldCount() {
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
}
