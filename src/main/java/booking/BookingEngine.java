package booking;

public interface BookingEngine {
    BookingResult book(BookingRequest request);

    int soldCount();

    void shutdown();
}
