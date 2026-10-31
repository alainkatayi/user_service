package com.mywallet.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mywallet.user.entity.AppUser;

public interface UserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByPhone(String phone);

}
