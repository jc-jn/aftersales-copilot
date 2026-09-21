package com.aftersales.copilot.auth.application;

import com.aftersales.copilot.auth.api.UserResponse;

public record TokenPair(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
}
