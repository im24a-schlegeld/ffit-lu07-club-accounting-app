package ch.bzz.controller;

import ch.bzz.model.ApiModels.AccessTokenResponse;
import ch.bzz.model.ApiModels.LoginRequest;
import ch.bzz.service.AccountingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final AccountingService accountingService;

    public ProjectController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @PostMapping("/projects")
    public ResponseEntity<Void> createProject(@RequestBody LoginRequest request) {
        accountingService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public AccessTokenResponse login(@RequestBody LoginRequest request) {
        return accountingService.login(request);
    }
}
