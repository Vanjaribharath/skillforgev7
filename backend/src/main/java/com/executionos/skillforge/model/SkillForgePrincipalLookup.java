package com.executionos.skillforge.model;

import java.util.Optional;
import java.util.UUID;

/**
 * Narrow, public contract that lets {@code com.executionos.security}
 * resolve an authenticated principal for SkillForge-issued JWTs, without
 * needing to depend on SfUser/SfUserRepository directly (both are
 * package-private by design — this is the one intentional seam).
 */
public interface SkillForgePrincipalLookup {
    Optional<SkillForgePrincipal> findById(UUID userId);

    record SkillForgePrincipal(UUID userId, String email, String role, UUID organizationId, boolean active) {}
}
