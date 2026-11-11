package booking.model;

public record Expired(String customerId, int seatId) implements BookingResult {
}
