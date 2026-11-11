package booking.model;

public record Rejected(String reason) implements BookingResult {
}
