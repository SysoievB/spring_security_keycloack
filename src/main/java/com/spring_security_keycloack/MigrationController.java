package com.spring_security_keycloack;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/migration")
@RequiredArgsConstructor
public class MigrationController {
    private final RolePermissionMigrationGenerator generator;

    @PostMapping("/generate")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<String>> generateMigration(@RequestBody List<String> existingRoles) {
        return Mono.fromCallable(() -> {
            RolePermissionMigrationGenerator.MigrationResult result =
                    generator.generateMigrationFromList(existingRoles);

            return ResponseEntity.ok(
                    "Migration generated! " +
                            result.getNewRoles().size() + " roles created. " +
                            "Check project root for generated files."
            );
        });
    }
}