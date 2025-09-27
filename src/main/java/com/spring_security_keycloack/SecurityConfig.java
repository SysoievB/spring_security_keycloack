package com.spring_security_keycloack;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
/*
eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJIdWNsblZOOWVSTFg2WFVaYmQ3eUEyNnBUQVRIMWJ2ZXduTUFsdzRPWlNFIn0.eyJleHAiOjE3NTg5NTgxNDYsImlhdCI6MTc1ODk1Nzg0NiwianRpIjoiMDQ0ZTJmNTItOTI2Mi00ZmNkLWJkYmUtZTBkOWM3ODVhYmYyIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgwL3JlYWxtcy9zcHJpbmcta2V5Y2xvYWNrLWFwcCIsImF1ZCI6ImFjY291bnQiLCJzdWIiOiJlMWEyZWNlZS00ZDViLTQyMTktYmNkNS00OWM4YjE5NGFlYzEiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJzcHJpbmctd2ViZmx1eC1hcHAiLCJzZXNzaW9uX3N0YXRlIjoiMThkZjg5MzAtNGVmOS00OTNhLTgyYTgtMjVlNTcxZjI4OWEzIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwOTAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtc3ByaW5nLWtleWNsb2Fjay1hcHAiLCJvZmZsaW5lX2FjY2VzcyIsInVtYV9hdXRob3JpemF0aW9uIiwiQURNSU4iXX0sInJlc291cmNlX2FjY2VzcyI6eyJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJzaWQiOiIxOGRmODkzMC00ZWY5LTQ5M2EtODJhOC0yNWU1NzFmMjg5YTMiLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwibmFtZSI6IkFkbWluIFVzZXIiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJhZG1pbl91c2VyIiwiZ2l2ZW5fbmFtZSI6IkFkbWluIiwiZmFtaWx5X25hbWUiOiJVc2VyIiwiZW1haWwiOiJhZG1pbkBleGFtcGxlLmNvbSJ9.gq2M7IZwSE-iBsdBGOwT6gQaWVWNgQEAF43H0Tq3lg_P7ZH9VlDOJOFz3wWTLpJH2FYcUcz8NxB0xX09YA72KIcEJOlaRIpDN1ZOURGQHK-nzuUvbfYFtQjHqfeEBEcP1HsBGpuwjeugrvPEy5BO8nhKhElzwyxc5a7a-Svq2kIHKXQ1gcmR-wE26hblYuSoe9lvN4cqg0dkEyeylgVyszIMjuTgKmo8BJjm1OKFucLa84Fyyd8SKFkmVn7ZzsOjdCWYhD0VhLGLi1UjSh2otTgoBY6FyVR65YqJTKC_imwgXCXPMsVmtIdY7UPwR2dIAKkK3c_z_86Bf9SXs82PPg

 */
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/api/public/**").permitAll()
                        .pathMatchers("/api/admin/**").hasRole("ADMIN")
                        .pathMatchers("/api/user/**").hasAnyRole("USER", "ADMIN")
                        .anyExchange().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                )
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    public ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return new ReactiveJwtAuthenticationConverterAdapter(jwtConverter);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");

        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) realmAccess.get("roles");

        return roles
                .stream()
                .filter(role -> role.equals("ADMIN") || role.equals("USER"))
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
