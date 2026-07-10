package com.megabike.identity.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	Optional<UserAccount> findByEmail(String email);

	@EntityGraph(attributePaths = {"roles", "roles.permissions"})
	Optional<UserAccount> findWithRolesAndPermissionsByEmail(String email);
}
