package booking;

public sealed interface BookingResult permits Confirmed, Rejected, Expired {
}

record Confirmed(String customerId, int seatId) implements BookingResult {
}

record Rejected(String reason) implements BookingResult {
}

record Expired(String customerId, int seatId) implements BookingResult {
}
