package com.morak.feature.auth.application.port.out;

public record SocialProfile(String provider, String providerUserId, String email, String profileImageUrl) {}
