package ch.bzz.controller;

import ch.bzz.generated.api.AccountApi;
import ch.bzz.generated.model.Account;
import ch.bzz.generated.model.UpdateAccountsRequest;
import ch.bzz.service.AccountingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountApiController implements AccountApi {

    private final AccountingService accountingService;

    public AccountApiController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Override
    public ResponseEntity<List<Account>> getAccounts(String authorization) {
        return ResponseEntity.ok(accountingService.getAccounts(authorization));
    }

    @Override
    public ResponseEntity<Void> updateAccounts(
            String authorization,
            UpdateAccountsRequest updateAccountsRequest
    ) {
        accountingService.updateAccounts(authorization, updateAccountsRequest);
        return ResponseEntity.ok().build();
    }
}
