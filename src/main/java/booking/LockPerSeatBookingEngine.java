package booking;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Un verrou par place.
 * Deux clients de la même place s'attendent.
 * Deux clients de deux places différentes ne s'attendent pas.
 */
public final class LockPerSeatBookingEngine implements BookingEngine {

    private final Seat[] seats;
    private final SeatState[] states;
    private final ReentrantLock[] locks;

    public LockPerSeatBookingEngine(int seatCount) {
        seats = new Seat[seatCount];
        states = new SeatState[seatCount];
        locks = new ReentrantLock[seatCount];
        for (int id = 0; id < seatCount; id++) {
            seats[id] = new Seat(id);
            states[id] = SeatState.FREE;
            locks[id] = new ReentrantLock();
        }
    }

    @Override
    public BookingResult book(BookingRequest request) {
        int id = request.seatId();
        if (id < 0 || id >= states.length) {
            return new Rejected("place inconnue");
        }

        locks[id].lock();
        try {
            if (states[id] == SeatState.FREE) {
                states[id] = SeatState.SOLD;
                return new Confirmed(request.customerId(), seats[id].id());
            }
            return new Rejected("deja prise");
        } finally {
            locks[id].unlock();
        }
    }

    @Override
    public int soldCount() {
        int count = 0;
        for (int id = 0; id < states.length; id++) {
            locks[id].lock();
            try {
                if (states[id] == SeatState.SOLD) {
                    count++;
                }
            } finally {
                locks[id].unlock();
            }
        }
        return count;
    }

    @Override
    public void shutdown() {
    }
}
