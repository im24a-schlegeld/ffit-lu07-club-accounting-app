package ch.bzz.repository;

import ch.bzz.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByProject_ProjectNameOrderByBookingNumberAsc(String projectName);

    Optional<Booking> findByBookingNumberAndProject_ProjectName(Integer bookingNumber, String projectName);

    Optional<Booking> findFirstByProject_ProjectNameOrderByBookingNumberDesc(String projectName);
}
