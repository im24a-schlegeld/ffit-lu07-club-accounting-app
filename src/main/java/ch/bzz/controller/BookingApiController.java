package ch.bzz.controller;

import ch.bzz.generated.api.BookingApi;
import ch.bzz.generated.model.Booking;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.service.AccountingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookingApiController implements BookingApi {

    private final AccountingService accountingService;

    public BookingApiController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Override
    public ResponseEntity<List<Booking>> getBookings(String authorization) {
        return ResponseEntity.ok(accountingService.getBookings(authorization));
    }

    @Override
    public ResponseEntity<Void> updateBookings(
            String authorization,
            UpdateBookingsRequest updateBookingsRequest
    ) {
        accountingService.updateBookings(authorization, updateBookingsRequest);
        return ResponseEntity.ok().build();
    }
}
