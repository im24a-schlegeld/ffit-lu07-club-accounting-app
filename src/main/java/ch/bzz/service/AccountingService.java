package ch.bzz.service;

import ch.bzz.model.ApiModels.AccessTokenResponse;
import ch.bzz.model.ApiModels.Account;
import ch.bzz.model.ApiModels.AccountUpdate;
import ch.bzz.model.ApiModels.Booking;
import ch.bzz.model.ApiModels.BookingUpdate;
import ch.bzz.model.ApiModels.LoginRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountingService {

    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ProjectData> projects = new ConcurrentHashMap<>();
    private final Map<String, String> tokenProjects = new ConcurrentHashMap<>();

    public void createProject(LoginRequest request) {
        if (request == null || isBlank(request.projectName()) || isBlank(request.password())) {
            throw badRequest("project_name and password are required");
        }

        ProjectData project = new ProjectData(hashPassword(request.password()));
        if (projects.putIfAbsent(request.projectName(), project) != null) {
            throw badRequest("Project already exists");
        }
    }

    public AccessTokenResponse login(LoginRequest request) {
        if (request == null || isBlank(request.projectName()) || isBlank(request.password())) {
            throw unauthorized();
        }

        ProjectData project = projects.get(request.projectName());
        if (project == null || !passwordMatches(request.password(), project.passwordHash)) {
            throw unauthorized();
        }

        String token = issueToken(request.projectName());
        return new AccessTokenResponse(token);
    }

    public List<Account> getAccounts(String authorization) {
        ProjectData project = authenticatedProject(authorization);
        synchronized (project) {
            return project.accounts.values().stream()
                    .sorted(Comparator.comparing(Account::number))
                    .toList();
        }
    }

    public void updateAccounts(String authorization, List<AccountUpdate> updates) {
        ProjectData project = authenticatedProject(authorization);
        if (updates == null) {
            throw badRequest("accounts is required");
        }

        synchronized (project) {
            Map<Integer, Account> changed = new HashMap<>(project.accounts);
            for (AccountUpdate update : updates) {
                if (update == null || update.number() == null) {
                    throw badRequest("Every account update requires a number");
                }

                Account current = changed.get(update.number());
                String name = update.name() != null
                        ? update.name()
                        : current == null ? null : current.name();
                if (name == null) {
                    throw badRequest("A new account requires a name");
                }
                changed.put(update.number(), new Account(update.number(), name));
            }
            project.accounts.clear();
            project.accounts.putAll(changed);
        }
    }

    public List<Booking> getBookings(String authorization) {
        ProjectData project = authenticatedProject(authorization);
        synchronized (project) {
            return project.bookings.values().stream()
                    .sorted(Comparator.comparing(Booking::number))
                    .toList();
        }
    }

    public void updateBookings(String authorization, List<BookingUpdate> updates) {
        ProjectData project = authenticatedProject(authorization);
        if (updates == null) {
            throw badRequest("entries is required");
        }

        synchronized (project) {
            Map<Integer, Booking> changed = new HashMap<>(project.bookings);
            for (BookingUpdate update : updates) {
                if (update == null) {
                    throw badRequest("Booking updates must not be null");
                }

                Integer number = update.id() != null ? update.id() : nextBookingNumber(changed);
                Booking current = changed.get(number);
                LocalDate date = update.date() != null ? update.date() : valueOf(current, Booking::date);
                String text = update.text() != null ? update.text() : valueOf(current, Booking::text);
                Integer debit = update.debit() != null ? update.debit() : valueOf(current, Booking::debit);
                Integer credit = update.credit() != null ? update.credit() : valueOf(current, Booking::credit);
                var amount = update.amount() != null ? update.amount() : valueOf(current, Booking::amount);

                if (date == null || text == null || debit == null || credit == null || amount == null) {
                    throw badRequest("A new booking requires date, text, debit, credit, and amount");
                }
                if (!project.accounts.containsKey(debit) || !project.accounts.containsKey(credit)) {
                    throw badRequest("Both booking accounts must exist in this project");
                }
                changed.put(number, new Booking(number, date, text, debit, credit, amount));
            }
            project.bookings.clear();
            project.bookings.putAll(changed);
        }
    }

    private ProjectData authenticatedProject(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthorized();
        }

        String token = authorization.substring("Bearer ".length()).trim();
        String projectName = tokenProjects.get(token);
        ProjectData project = projectName == null ? null : projects.get(projectName);
        if (project == null) {
            throw unauthorized();
        }
        return project;
    }

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        char[] passwordCharacters = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(passwordCharacters, salt, PASSWORD_ITERATIONS, PASSWORD_KEY_BITS);
        try {
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return PASSWORD_ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Password hashing is unavailable", exception);
        } finally {
            spec.clearPassword();
            Arrays.fill(passwordCharacters, '\0');
        }
    }

    private boolean passwordMatches(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 3) {
            return false;
        }

        try {
            int iterations = Integer.parseInt(parts[0]);
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] salt = decoder.decode(parts[1]);
            byte[] expected = decoder.decode(parts[2]);
            char[] passwordCharacters = password.toCharArray();
            PBEKeySpec spec = new PBEKeySpec(passwordCharacters, salt, iterations, expected.length * Byte.SIZE);
            try {
                byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } finally {
                spec.clearPassword();
                Arrays.fill(passwordCharacters, '\0');
            }
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException("Stored password hash cannot be verified", exception);
        }
    }

    private String issueToken(String projectName) {
        byte[] bytes = new byte[TOKEN_BYTES];
        String token;
        do {
            secureRandom.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (tokenProjects.putIfAbsent(token, projectName) != null);
        return token;
    }

    private Integer nextBookingNumber(Map<Integer, Booking> bookings) {
        return bookings.keySet().stream().max(Integer::compareTo)
                .map(number -> {
                    try {
                        return Math.addExact(number, 1);
                    } catch (ArithmeticException exception) {
                        throw badRequest("No further booking number is available");
                    }
                })
                .orElse(1);
    }

    private static <T> T valueOf(Booking booking, java.util.function.Function<Booking, T> getter) {
        return booking == null ? null : getter.apply(booking);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }

    private static final class ProjectData {
        private final String passwordHash;
        private final Map<Integer, Account> accounts = new HashMap<>();
        private final Map<Integer, Booking> bookings = new HashMap<>();

        private ProjectData(String passwordHash) {
            this.passwordHash = passwordHash;
        }
    }
}
