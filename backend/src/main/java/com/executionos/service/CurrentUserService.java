package com.executionos.service;

import com.executionos.model.User;
import com.executionos.repository.UserRepository;
import java.security.Principal;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Resolves the ExecutionOS-side user row for whoever is currently
     * authenticated. This app has two independent user tables (ExecutionOS's
     * own "users", and SkillForge's "sf_users") sharing one JWT-issuing
     * login flow. A SkillForge-only account (the normal case, since that's
     * this app's real product) has no matching ExecutionOS row by default --
     * without this, every task/schedule/note/journal/etc. endpoint would
     * throw for every real user. Lazily provisions a matching row keyed on
     * email the first time it's needed, with no usable password (nobody
     * logs into this table directly -- they always arrive via an
     * already-validated JWT from the real login flow).
     */
    public User requireUser(Principal principal) {
        if (principal == null) {
            throw new IllegalArgumentException("Authentication is required");
        }
        String email = principal.getName();
        return users.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setName(email);
            return users.save(user);
        });
    }
}
