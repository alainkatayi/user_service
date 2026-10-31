package com.mywallet.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;

public record UserResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String phone,
		UserStatus status,
		Instant createdAt,
		Instant updatedAt) {

	public static UserResponse from(AppUser user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getFirstName(),
				user.getLastName(),
				user.getPhone(),
				user.getStatus(),
				user.getCreatedAt(),
				user.getUpdatedAt());
	}

}
