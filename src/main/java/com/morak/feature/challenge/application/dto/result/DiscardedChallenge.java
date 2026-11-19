package com.morak.feature.challenge.application.dto.result;

/** 폐기 결과. 이미 폐기된 챌린지를 다시 폐기해도 같은 값이다. */
public record DiscardedChallenge(long challengeId, String status) {}
