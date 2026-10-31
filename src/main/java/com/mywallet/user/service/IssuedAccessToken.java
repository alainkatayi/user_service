package com.mywallet.user.service;

public record IssuedAccessToken(String token, long expiresInSeconds) {
}
