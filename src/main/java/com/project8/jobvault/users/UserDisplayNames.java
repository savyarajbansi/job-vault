package com.project8.jobvault.users;

public final class UserDisplayNames {

    private UserDisplayNames() {
    }

    public static String nameOrEmail(UserAccount user) {
        if (user == null) {
            return null;
        }
        String displayName = user.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return user.getEmail();
    }
}