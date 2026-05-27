package com.project8.jobvault.users;

import org.springframework.stereotype.Component;

@Component
public class UserAccountFactory {

    public UserAccount newRegisteredUser(String email, String passwordHash, String displayName, Role role) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setDisplayName(displayName);
        user.setEnabled(true);
        if (role != null) {
            user.getRoles().add(role);
        }
        return user;
    }
}
