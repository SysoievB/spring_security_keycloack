package com.spring_security_keycloack;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class RolePermissionMigrationGenerator {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RolePermissionMigrationGenerator() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Fetches existing roles from Keycloak and generates migration configuration
     */
    public Mono<MigrationResult> generateMigrationFromKeycloak(
            String keycloakUrl, String realm, String adminUsername, String adminPassword) {

        return getAdminToken(keycloakUrl, adminUsername, adminPassword)
                .flatMap(token -> getAllRoles(keycloakUrl, realm, token))
                .map(this::generateMigrationConfiguration)
                .doOnSuccess(result -> {
                    try {
                        saveMigrationFiles(result);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to save migration files", e);
                    }
                });
    }

    /**
     * Generates migration configuration from a list of existing role names
     */
    public MigrationResult generateMigrationFromList(List<String> existingSettingRoles) {
        return generateMigrationConfiguration(existingSettingRoles);
    }

    private Mono<String> getAdminToken(String keycloakUrl, String username, String password) {
        return webClient.post()
                .uri(keycloakUrl + "/realms/master/protocol/openid-connect/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .bodyValue("grant_type=password&client_id=admin-cli&username=" + username + "&password=" + password)
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (String) response.get("access_token"));
    }

    private Mono<List<String>> getAllRoles(String keycloakUrl, String realm, String token) {
        return webClient.get()
                .uri(keycloakUrl + "/admin/realms/" + realm + "/roles")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .bodyToFlux(Map.class)
                .map(role -> (String) role.get("name"))
                .filter(this::isSettingRole) // Filter only setting roles, not base USER/ADMIN
                .collectList();
    }

    private boolean isSettingRole(String roleName) {
        // Exclude system roles
        return !roleName.equals("USER") &&
                !roleName.equals("ADMIN") &&
                !roleName.startsWith("default-") &&
                !roleName.equals("offline_access") &&
                !roleName.equals("uma_authorization");
    }

    private MigrationResult generateMigrationConfiguration(List<String> existingRoles) {
        MigrationResult result = new MigrationResult();
        result.setExistingRoles(existingRoles);

        List<RoleDefinition> newRoles = new ArrayList<>();
        Map<String, String> roleMapping = new HashMap<>();

        // Generate READ/WRITE roles for each existing setting role
        for (String roleName : existingRoles) {
            // Create READ permission role
            String readRoleName = roleName + "_READ";
            newRoles.add(new RoleDefinition(
                    readRoleName,
                    "Read permission for " + roleName,
                    false,
                    null
            ));

            // Create WRITE permission role
            String writeRoleName = roleName + "_WRITE";
            newRoles.add(new RoleDefinition(
                    writeRoleName,
                    "Write permission for " + roleName,
                    false,
                    null
            ));

            // Convert existing role to composite (backward compatibility)
            newRoles.add(new RoleDefinition(
                    roleName,
                    "Composite role for " + roleName + " (READ + WRITE)",
                    true,
                    Arrays.asList(readRoleName, writeRoleName)
            ));

            // Store mapping for documentation
            roleMapping.put(roleName, readRoleName + " + " + writeRoleName);
        }

        // Add composite USER and ADMIN roles
        List<String> allReadPermissions = existingRoles.stream()
                .map(role -> role + "_READ")
                .collect(Collectors.toList());

        List<String> allWritePermissions = existingRoles.stream()
                .map(role -> role + "_WRITE")
                .collect(Collectors.toList());

        // USER_READ_ALL = USER + all READ permissions
        List<String> userReadComposites = new ArrayList<>();
        userReadComposites.add("USER");
        userReadComposites.addAll(allReadPermissions);
        newRoles.add(new RoleDefinition(
                "USER_READ_ALL",
                "User with all read permissions",
                true,
                userReadComposites
        ));

        // USER_FULL_ACCESS = USER + all READ + all WRITE permissions
        List<String> userFullComposites = new ArrayList<>();
        userFullComposites.add("USER");
        userFullComposites.addAll(allReadPermissions);
        userFullComposites.addAll(allWritePermissions);
        newRoles.add(new RoleDefinition(
                "USER_FULL_ACCESS",
                "User with full read/write access",
                true,
                userFullComposites
        ));

        // ADMIN_FULL_ACCESS = ADMIN + USER + all permissions
        List<String> adminFullComposites = new ArrayList<>();
        adminFullComposites.add("ADMIN");
        adminFullComposites.add("USER");
        adminFullComposites.addAll(allReadPermissions);
        adminFullComposites.addAll(allWritePermissions);
        newRoles.add(new RoleDefinition(
                "ADMIN_FULL_ACCESS",
                "Admin with all permissions",
                true,
                adminFullComposites
        ));

        result.setNewRoles(newRoles);
        result.setRoleMapping(roleMapping);
        result.setKeycloakImportJson(generateKeycloakJson(newRoles));
        result.setSpringSecurityUpdates(generateSpringSecurityCode(existingRoles));

        return result;
    }

    private Map<String, Object> generateKeycloakJson(List<RoleDefinition> roles) {
        Map<String, Object> keycloakConfig = new HashMap<>();

        List<Map<String, Object>> rolesList = new ArrayList<>();
        for (RoleDefinition role : roles) {
            Map<String, Object> roleMap = new HashMap<>();
            roleMap.put("id", UUID.randomUUID().toString());
            roleMap.put("name", role.getName());
            roleMap.put("description", role.getDescription());
            roleMap.put("composite", role.isComposite());
            roleMap.put("clientRole", false);

            if (role.isComposite() && role.getCompositeRoles() != null) {
                Map<String, Object> composites = new HashMap<>();
                composites.put("realm", role.getCompositeRoles());
                roleMap.put("composites", composites);
            }

            rolesList.add(roleMap);
        }

        keycloakConfig.put("roles", Map.of("realm", rolesList));
        return keycloakConfig;
    }

    private String generateSpringSecurityCode(List<String> existingRoles) {
        StringBuilder code = new StringBuilder();
        code.append("// Updated SecurityConfig for permission-based access\n\n");
        code.append("// Authority extraction in SecurityConfig.java:\n");
        code.append("private Collection<SimpleGrantedAuthority> extractAuthorities(Jwt jwt) {\n");
        code.append("    Map<String, Object> realmAccess = jwt.getClaimAsMap(\"realm_access\");\n");
        code.append("    if (realmAccess == null || !realmAccess.containsKey(\"roles\")) {\n");
        code.append("        return List.of();\n");
        code.append("    }\n");
        code.append("    List<String> roles = (List<String>) realmAccess.get(\"roles\");\n");
        code.append("    return roles.stream()\n");
        code.append("        .flatMap(role -> {\n");
        code.append("            // Map READ permissions\n");

        for (String role : existingRoles) {
            code.append(String.format("            if (role.equals(\"%s_READ\")) {\n", role));
            code.append(String.format("                return Stream.of(new SimpleGrantedAuthority(\"PERMISSION_%s_READ\"));\n", role));
            code.append("            }\n");
            code.append(String.format("            if (role.equals(\"%s_WRITE\")) {\n", role));
            code.append(String.format("                return Stream.of(new SimpleGrantedAuthority(\"PERMISSION_%s_WRITE\"));\n", role));
            code.append("            }\n");
        }

        code.append("            return Stream.empty();\n");
        code.append("        })\n");
        code.append("        .collect(Collectors.toList());\n");
        code.append("}\n\n");

        code.append("// Example @PreAuthorize annotations:\n");
        for (String role : existingRoles.subList(0, Math.min(3, existingRoles.size()))) {
            code.append(String.format("@PreAuthorize(\"hasAuthority('PERMISSION_%s_READ') or hasRole('ADMIN')\")\n", role));
            code.append(String.format("public Mono<Data> read%sData() { ... }\n\n", toCamelCase(role)));

            code.append(String.format("@PreAuthorize(\"hasAuthority('PERMISSION_%s_WRITE') or hasRole('ADMIN')\")\n", role));
            code.append(String.format("public Mono<Void> write%sData() { ... }\n\n", toCamelCase(role)));
        }

        return code.toString();
    }

    private String toCamelCase(String input) {
        String[] parts = input.split("_");
        return Arrays.stream(parts)
                .map(part -> part.substring(0, 1).toUpperCase() + part.substring(1).toLowerCase())
                .collect(Collectors.joining());
    }

    private void saveMigrationFiles(MigrationResult result) throws Exception {
        // Save Keycloak import JSON
        objectMapper.writeValue(
                new File("keycloak-permission-migration.json"),
                result.getKeycloakImportJson()
        );

        // Save migration report
        Map<String, Object> report = new HashMap<>();
        report.put("existingRoles", result.getExistingRoles());
        report.put("roleMapping", result.getRoleMapping());
        report.put("newRolesCount", result.getNewRoles().size());
        objectMapper.writeValue(new File("migration-report.json"), report);

        // Save Spring Security code example
        java.nio.file.Files.write(
                java.nio.file.Paths.get("SecurityConfig-updates.java"),
                result.getSpringSecurityUpdates().getBytes()
        );

        System.out.println("Migration files generated:");
        System.out.println("- keycloak-permission-migration.json (import this to Keycloak)");
        System.out.println("- migration-report.json (documentation)");
        System.out.println("- SecurityConfig-updates.java (code changes needed)");
    }

    // Inner classes
    @Getter
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class RoleDefinition {
        String name;
        String description;
        boolean composite;
        List<String> compositeRoles;
    }

    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class MigrationResult {
        List<String> existingRoles;
        List<RoleDefinition> newRoles;
        Map<String, String> roleMapping;
        Map<String, Object> keycloakImportJson;
        String springSecurityUpdates;
    }
}
