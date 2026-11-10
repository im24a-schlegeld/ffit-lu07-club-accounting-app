package ch.bzz.repository;

import ch.bzz.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByProject_ProjectNameOrderByAccountNumberAsc(String projectName);

    Optional<Account> findByAccountNumberAndProject_ProjectName(Integer accountNumber, String projectName);
}
