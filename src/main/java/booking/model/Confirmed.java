package booking.model;

public record Confirmed(String customerId, int seatId) implements BookingResult {
}
