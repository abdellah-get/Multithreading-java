package booking.model;

public sealed interface BookingResult permits Confirmed, Rejected, Expired {
}
