package booking;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Pas de verrou.
 * compareAndSet change FREE en SOLD seulement si la case est encore FREE.
 */
public final class CasBookingEngine implements BookingEngine {

    private final Seat[] seats;
    private final AtomicReferenceArray<SeatState> states;

    public CasBookingEngine(int seatCount) {
        seats = new Seat[seatCount];
        states = new AtomicReferenceArray<>(seatCount);
        for (int id = 0; id < seatCount; id++) {
            seats[id] = new Seat(id);
            states.set(id, SeatState.FREE);
        }
    }

    @Override
    public BookingResult book(BookingRequest request) {
        int id = request.seatId();
        if (id < 0 || id >= states.length()) {
            return new Rejected("place inconnue");
        }

        if (states.compareAndSet(id, SeatState.FREE, SeatState.SOLD)) {
            return new Confirmed(request.customerId(), seats[id].id());
        }
        return new Rejected("deja prise");
    }

    @Override
    public int soldCount() {
        int count = 0;
        for (int id = 0; id < states.length(); id++) {
            if (states.get(id) == SeatState.SOLD) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void shutdown() {
    }
}
