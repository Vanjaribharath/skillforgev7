package com.executionos.skillforge.model;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SkillForgePrincipalLookupImpl implements SkillForgePrincipalLookup {

    private final SfUserRepository users;

    SkillForgePrincipalLookupImpl(SfUserRepository users) {
        this.users = users;
    }

    @Override
    public Optional<SkillForgePrincipal> findById(UUID userId) {
        return users.findById(userId)
                .map(u -> new SkillForgePrincipal(
                        u.getId(),
                        u.getEmail(),
                        u.getRole().name(),
                        u.getOrganizationId(),
                        u.getStatus() == SkillForgeEnums.UserStatus.ACTIVE));
    }
}
