package com.jarvis.api;

import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jarvis.secrets.SecretView;
import com.jarvis.secrets.SecretsVaultService;

import jakarta.validation.constraints.NotBlank;

/**
 * Secrets Vault endpoints (spec §11.3). There is deliberately NO endpoint that
 * returns a plaintext value — only masked metadata is ever exposed.
 */
@RestController
@RequestMapping("/api/secrets")
@RequiredArgsConstructor
public class SecretsController {

    private final SecretsVaultService vault;


    public record CreateRequest(
            @NotBlank String name,
            String connector,
            @NotBlank String value,
            List<String> scopes) {}

    @GetMapping
    public List<SecretView> list() {
        return vault.list();
    }

    @PostMapping
    public ResponseEntity<SecretView> create(@jakarta.validation.Valid @RequestBody CreateRequest request) {
        SecretView view = vault.store(request.name(), request.connector(), request.value(), request.scopes());
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        vault.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
