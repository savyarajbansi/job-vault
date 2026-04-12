package com.project8.jobvault.users;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
	@EntityGraph(attributePaths = "roles")
	Optional<UserAccount> findByEmail(String email);
}
