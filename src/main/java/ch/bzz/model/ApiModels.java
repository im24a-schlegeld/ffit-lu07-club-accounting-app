package ch.bzz.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class ApiModels {

    private ApiModels() {
    }

    public record LoginRequest(
            @JsonProperty("project_name") String projectName,
            String password) {
    }

    public record AccessTokenResponse(
            @JsonProperty("access_token") String accessToken) {
    }

    public record Account(Integer number, String name) {
    }

    public record AccountUpdate(Integer number, String name) {
    }

    public record AccountsUpdateRequest(List<AccountUpdate> accounts) {
    }

    public record Booking(
            Integer number,
            LocalDate date,
            String text,
            Integer debit,
            Integer credit,
            BigDecimal amount) {
    }

    public record BookingUpdate(
            Integer id,
            LocalDate date,
            String text,
            Integer debit,
            Integer credit,
            BigDecimal amount) {
    }

    public record BookingsUpdateRequest(List<BookingUpdate> entries) {
    }
}
