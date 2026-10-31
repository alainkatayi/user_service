package com.mywallet.user.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
