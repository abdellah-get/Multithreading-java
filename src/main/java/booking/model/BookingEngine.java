package booking.model;

public interface BookingEngine {
    BookingResult book(BookingRequest request);

    int soldCount();

    void shutdown();
}
