package ch.bzz.controller;

import ch.bzz.generated.api.ProjectApi;
import ch.bzz.generated.model.LoginProject200Response;
import ch.bzz.generated.model.LoginRequest;
import ch.bzz.service.AccountingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectApiController implements ProjectApi {

    private final AccountingService accountingService;

    public ProjectApiController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Override
    public ResponseEntity<Void> createProject(LoginRequest loginRequest) {
        accountingService.createProject(loginRequest);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @Override
    public ResponseEntity<LoginProject200Response> loginProject(LoginRequest loginRequest) {
        return ResponseEntity.ok(
                new LoginProject200Response().accessToken(accountingService.login(loginRequest))
        );
    }
}
