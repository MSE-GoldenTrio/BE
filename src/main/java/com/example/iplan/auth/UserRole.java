package com.example.iplan.auth;

import lombok.Getter;

@Getter
public enum UserRole {
    CHILD("ROLE_CHILD"),
    PARENT("ROLE_PARENT");

    private final String role;

    UserRole(String role) {
        this.role = role;
    }

    public static UserRole fromString(String role) {
        for (UserRole userRole : UserRole.values()) {
            if (userRole.getRole().equalsIgnoreCase(role)) {  // getRole()과 비교
                return userRole;
            }
        }
        throw new IllegalArgumentException("Unknown role: " + role);
    }
}
