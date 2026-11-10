package ch.bzz.service;

import ch.bzz.generated.model.AccountUpdate;
import ch.bzz.generated.model.BookingUpdate;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.generated.model.UpdateBookingsRequest;
import ch.bzz.model.Account;
import ch.bzz.model.Booking;
import ch.bzz.model.Project;
import ch.bzz.repository.AccountRepository;
import ch.bzz.repository.BookingRepository;
import ch.bzz.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.openapitools.jackson.nullable.JsonNullable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class AccountingService {

    private final ProjectRepository projectRepository;
    private final AccountRepository accountRepository;
    private final BookingRepository bookingRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    public AccountingService(
            ProjectRepository projectRepository,
            AccountRepository accountRepository,
            BookingRepository bookingRepository,
            JwtUtil jwtUtil,
            PasswordEncoder passwordEncoder
    ) {
        this.projectRepository = projectRepository;
        this.accountRepository = accountRepository;
        this.bookingRepository = bookingRepository;
        this.jwtUtil = jwtUtil;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createProject(LoginRequest request) {
        if (request == null || isBlank(request.getProjectName()) || isBlank(request.getPassword())) {
            throw badRequest("project_name and password are required");
        }
        if (projectRepository.existsById(request.getProjectName())) {
            throw badRequest("Project already exists");
        }

        Project project = new Project();
        project.setProjectName(request.getProjectName());
        project.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        projectRepository.save(project);
        log.info("Created accounting project {}", request.getProjectName());
    }

    @Transactional(readOnly = true)
    public String login(LoginRequest request) {
        if (request == null || isBlank(request.getProjectName()) || isBlank(request.getPassword())) {
            throw unauthorized("Invalid credentials");
        }

        Project project = projectRepository.findById(request.getProjectName())
                .orElseThrow(() -> unauthorized("Invalid credentials"));
        if (!passwordEncoder.matches(request.getPassword(), project.getPasswordHash())) {
            throw unauthorized("Invalid credentials");
        }

        log.info("Project {} logged in", project.getProjectName());
        return jwtUtil.generateToken(project.getProjectName());
    }

    @Transactional(readOnly = true)
    public List<ch.bzz.generated.model.Account> getAccounts(String authorization) {
        String projectName = authenticatedProjectName(authorization);
        return accountRepository.findByProject_ProjectNameOrderByAccountNumberAsc(projectName)
                .stream()
                .map(this::toApiAccount)
                .toList();
    }

    @Transactional
    public void updateAccounts(String authorization, UpdateAccountsRequest request) {
        String projectName = authenticatedProjectName(authorization);
        if (request == null || request.getAccounts() == null) {
            throw badRequest("accounts is required");
        }

        Project project = projectRepository.getReferenceById(projectName);
        List<Account> updates = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        for (AccountUpdate update : request.getAccounts()) {
            if (update == null || update.getNumber() == null || update.getNumber() <= 0) {
                throw badRequest("Every account update requires a positive number");
            }
            if (!numbers.add(update.getNumber())) {
                throw badRequest("Account numbers must not be repeated");
            }

            Account account = accountRepository
                    .findByAccountNumberAndProject_ProjectName(update.getNumber(), projectName)
                    .orElseGet(Account::new);
            JsonNullable<String> nameValue = update.getName();
            String name = nameValue != null && nameValue.isPresent()
                    ? nameValue.get()
                    : account.getName();
            if (isBlank(name)) {
                throw badRequest("A new account requires a name");
            }

            account.setAccountNumber(update.getNumber());
            account.setName(name);
            account.setProject(project);
            updates.add(account);
        }

        accountRepository.saveAll(updates);
        log.info("Saved {} account updates for project {}", updates.size(), projectName);
    }

    @Transactional(readOnly = true)
    public List<ch.bzz.generated.model.Booking> getBookings(String authorization) {
        String projectName = authenticatedProjectName(authorization);
        return bookingRepository.findByProject_ProjectNameOrderByBookingNumberAsc(projectName)
                .stream()
                .map(this::toApiBooking)
                .toList();
    }

    @Transactional
    public void updateBookings(String authorization, UpdateBookingsRequest request) {
        String projectName = authenticatedProjectName(authorization);
        if (request == null || request.getEntries() == null) {
            throw badRequest("entries is required");
        }

        Project project = projectRepository.getReferenceById(projectName);
        List<Booking> updates = new ArrayList<>();
        Set<Integer> numbers = new HashSet<>();
        int nextNumber = bookingRepository
                .findFirstByProject_ProjectNameOrderByBookingNumberDesc(projectName)
                .map(booking -> incrementBookingNumber(booking.getBookingNumber()))
                .orElse(1);

        for (BookingUpdate update : request.getEntries()) {
            if (update == null) {
                throw badRequest("Booking updates must not be null");
            }

            Integer number = update.getId() == null ? nextNumber : update.getId();
            if (number <= 0 || !numbers.add(number)) {
                throw badRequest("Booking numbers must be positive and unique in each update");
            }
            if (number >= nextNumber) {
                nextNumber = incrementBookingNumber(number);
            }
            Booking booking = bookingRepository
                    .findByBookingNumberAndProject_ProjectName(number, projectName)
                    .orElseGet(Booking::new);

            LocalDate date = valueOrExisting(update.getDate(), booking.getDate());
            String text = valueOrExisting(update.getText(), booking.getText());
            Integer debitNumber = valueOrExisting(update.getDebit(), accountNumber(booking.getDebitAccount()));
            Integer creditNumber = valueOrExisting(update.getCredit(), accountNumber(booking.getCreditAccount()));
            Double amount = valueOrExisting(update.getAmount(), amountValue(booking.getAmount()));
            if (date == null || isBlank(text) || debitNumber == null || creditNumber == null || amount == null) {
                throw badRequest("A new booking requires date, text, debit, credit, and amount");
            }

            Account debit = accountRepository
                    .findByAccountNumberAndProject_ProjectName(debitNumber, projectName)
                    .orElseThrow(() -> badRequest("Debit account does not exist in this project"));
            Account credit = accountRepository
                    .findByAccountNumberAndProject_ProjectName(creditNumber, projectName)
                    .orElseThrow(() -> badRequest("Credit account does not exist in this project"));

            booking.setBookingNumber(number);
            booking.setDate(date);
            booking.setText(text);
            booking.setDebitAccount(debit);
            booking.setCreditAccount(credit);
            booking.setAmount(BigDecimal.valueOf(amount));
            booking.setProject(project);
            updates.add(booking);
        }

        bookingRepository.saveAll(updates);
        log.info("Saved {} booking updates for project {}", updates.size(), projectName);
    }

    private String authenticatedProjectName(String authorization) {
        String projectName = jwtUtil.verifyTokenAndExtractSubject(authorization);
        if (projectName == null || !projectRepository.existsById(projectName)) {
            throw unauthorized("Invalid token");
        }
        return projectName;
    }

    private ch.bzz.generated.model.Account toApiAccount(Account account) {
        return new ch.bzz.generated.model.Account(account.getAccountNumber(), account.getName());
    }

    private ch.bzz.generated.model.Booking toApiBooking(Booking booking) {
        return new ch.bzz.generated.model.Booking(
                booking.getBookingNumber(),
                booking.getDate(),
                booking.getText(),
                booking.getDebitAccount().getAccountNumber(),
                booking.getCreditAccount().getAccountNumber(),
                booking.getAmount().doubleValue()
        );
    }

    private static Integer accountNumber(Account account) {
        return account == null ? null : account.getAccountNumber();
    }

    private static Double amountValue(BigDecimal amount) {
        return amount == null ? null : amount.doubleValue();
    }

    private static <T> T valueOrExisting(JsonNullable<T> update, T existing) {
        return update != null && update.isPresent() ? update.get() : existing;
    }

    private static int incrementBookingNumber(Integer current) {
        if (current == Integer.MAX_VALUE) {
            throw badRequest("No further booking number is available");
        }
        return current + 1;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
