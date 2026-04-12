package com.project8.jobvault.auth;

import com.project8.jobvault.users.UserAccount;

public record RefreshTokenRotation(UserAccount user, RefreshTokenResult refreshToken) {
}
