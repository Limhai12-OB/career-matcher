package com.careermatcher.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only user queries exposed to other modules (security, auth). */
@Service
@Transactional(readOnly = true)
public class UserLookup {

  private final UserRepository users;

  public UserLookup(UserRepository users) {
    this.users = users;
  }

  public Optional<UserTokenState> tokenState(UUID userId) {
    return users
        .findById(userId)
        .map(u -> new UserTokenState(u.getId(), u.getRole(), u.getStatus(), u.getTokenVersion()));
  }
}
