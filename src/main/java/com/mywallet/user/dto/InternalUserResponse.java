package com.mywallet.user.dto;

import java.util.UUID;

import com.mywallet.user.entity.AppUser;
import com.mywallet.user.entity.UserStatus;

public record InternalUserResponse(
		UUID id,
		String firstName,
		String lastName,
		String phone,
		UserStatus status) {

	public static InternalUserResponse from(AppUser user) {
		return new InternalUserResponse(
				user.getId(),
				user.getFirstName(),
				user.getLastName(),
				user.getPhone(),
				user.getStatus());
	}

}
