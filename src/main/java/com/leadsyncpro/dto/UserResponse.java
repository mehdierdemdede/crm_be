package com.leadsyncpro.dto;

import com.leadsyncpro.model.Role;
import com.leadsyncpro.model.SupportedLanguages;
import com.leadsyncpro.model.User;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UserResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean active;
    
    private Set<SupportedLanguages> supportedLanguages;
    private Integer dailyCapacity;
    private boolean autoAssignEnabled;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setEmail(user.getEmail());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setRole(user.getRole());
        r.setActive(user.isActive());
        r.setSupportedLanguages(user.getSupportedLanguages());
        r.setDailyCapacity(user.getDailyCapacity());
        r.setAutoAssignEnabled(user.isAutoAssignEnabled());
        return r;
    }
}