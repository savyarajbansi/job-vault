package com.project8.jobvault.users;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
	@EntityGraph(attributePaths = "roles")
	Optional<UserAccount> findByEmail(String email);

	@EntityGraph(attributePaths = "roles")
	List<UserAccount> findAllByOrderByEmailAsc();

	@EntityGraph(attributePaths = "roles")
	Optional<UserAccount> findWithRolesById(UUID id);
}
