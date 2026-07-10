package com.executionos.security;

import com.executionos.repository.UserRepository;
import com.executionos.skillforge.model.SkillForgePrincipalLookup;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Request attribute set for SkillForge principals, readable by downstream code that needs the caller's org. */
    public static final String ORGANIZATION_ID_ATTRIBUTE = "skillforge.organizationId";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final SkillForgePrincipalLookup skillForgePrincipalLookup;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository, SkillForgePrincipalLookup skillForgePrincipalLookup) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.skillForgePrincipalLookup = skillForgePrincipalLookup;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            try {
                var claims = jwtService.claims(token);
                String uidClaim = claims.get("uid", String.class);
                UUID uid = uidClaim != null ? UUID.fromString(uidClaim) : null;

                boolean resolved = false;
                if (uid != null) {
                    var skillForgeUser = skillForgePrincipalLookup.findById(uid);
                    if (skillForgeUser.isPresent()) {
                        resolved = true;
                        var principal = skillForgeUser.get();
                        if (principal.active()) {
                            List<GrantedAuthority> authorities = new ArrayList<>();
                            authorities.add(new SimpleGrantedAuthority("ROLE_" + principal.role()));
                            if (principal.role().equals("ORG_ADMIN") || principal.role().equals("PLATFORM_ADMIN")) {
                                authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                            }
                            var auth = new UsernamePasswordAuthenticationToken(principal.email(), null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                            if (principal.organizationId() != null) {
                                request.setAttribute(ORGANIZATION_ID_ATTRIBUTE, principal.organizationId());
                            }
                        }
                        // else: matched a SkillForge user but their account is
                        // suspended/archived — leave unauthenticated, don't fall
                        // through to the ExecutionOS lookup below.
                    }
                }

                if (!resolved) {
                    // Not a SkillForge-issued token (or uid claim absent) —
                    // fall back to the original ExecutionOS user lookup.
                    String email = claims.getSubject();
                    if (email != null) {
                        userRepository.findByEmail(email).ifPresent(user -> {
                            var auth = new UsernamePasswordAuthenticationToken(
                                    user.getEmail(),
                                    null,
                                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
                    }
                }
            } catch (Exception ex) {
                // Invalid or expired token — clear context, let Spring Security handle 401
                log.debug("JWT validation failed: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
