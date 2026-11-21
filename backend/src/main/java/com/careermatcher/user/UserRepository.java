package com.careermatcher.user;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {

  Optional<UserAccount> findByEmail(String normalizedEmail);

  boolean existsByEmail(String normalizedEmail);

  /**
   * Records a sign-in without touching the optimistic-lock version, so concurrent logins to the
   * same account (several devices or tabs) never conflict.
   */
  @Modifying
  @Query("update UserAccount u set u.lastLoginAt = :at where u.id = :id")
  int recordLogin(@Param("id") UUID id, @Param("at") Instant at);
}
