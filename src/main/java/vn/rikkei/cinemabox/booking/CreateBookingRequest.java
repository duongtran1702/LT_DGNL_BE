package vn.rikkei.cinemabox.booking;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "customerId không được để trống")
        Long customerId,

        @NotNull(message = "showtimeId không được để trống")
        Long showtimeId,

        @NotNull(message = "quantity không được để trống")
        @Min(value = 1, message = "quantity phải từ 1 đến 10")
        @Max(value = 10, message = "quantity phải từ 1 đến 10")
        Integer quantity) {
}
