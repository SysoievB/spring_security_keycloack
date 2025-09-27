package com.spring_security_keycloack;

import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User {
    String id;
    String username;
    String email;
    String firstName;
    String lastName;
    List<String> roles;
    LocalDateTime createdAt;
    boolean active;

    // Constructors
    public User() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    public User(String id, String username, String email, String firstName, String lastName, List<String> roles) {
        this();
        this.id = id;
        this.username = username;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.roles = roles;
    }
}