package ch.bzz.controller;

import ch.bzz.model.ApiModels.Booking;
import ch.bzz.model.ApiModels.BookingsUpdateRequest;
import ch.bzz.service.AccountingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class BookingController {

    private final AccountingService accountingService;

    public BookingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/bookings")
    public List<Booking> getBookings(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        return accountingService.getBookings(authorization);
    }

    @PutMapping("/bookings")
    public void updateBookings(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody BookingsUpdateRequest request) {
        accountingService.updateBookings(authorization, request == null ? null : request.entries());
    }
}
