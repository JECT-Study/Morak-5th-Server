package com.morak.feature.auth.application.dto.command;

public record SocialLoginCommand(String provider, String code) {}
