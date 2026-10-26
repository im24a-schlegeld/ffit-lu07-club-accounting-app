package ch.bzz.controller;

import ch.bzz.model.ApiModels.Account;
import ch.bzz.model.ApiModels.AccountsUpdateRequest;
import ch.bzz.service.AccountingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountController {

    private final AccountingService accountingService;

    public AccountController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/accounts")
    public List<Account> getAccounts(
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        return accountingService.getAccounts(authorization);
    }

    @PutMapping("/accounts")
    public void updateAccounts(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody AccountsUpdateRequest request) {
        accountingService.updateAccounts(authorization, request == null ? null : request.accounts());
    }
}
