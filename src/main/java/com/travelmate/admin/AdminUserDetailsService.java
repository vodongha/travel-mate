package com.travelmate.admin;

import com.travelmate.user.User;
import com.travelmate.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Authenticates the {@code /admin} form login. Only an active super-admin with a password can log
 * in: non-admins, deleted accounts and Google-only accounts (no password hash) are rejected, so the
 * panel can never be entered by a regular user. The session stores the username (email); the chain
 * re-checks the account on each request, so revoking {@code is_superadmin} kills a live session.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AdminUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        final User user = userRepository.findByEmail(email.trim().toLowerCase())
                .filter(User::isSuperadmin)
                .filter(u -> u.getPasswordHash() != null && !u.getPasswordHash().isBlank())
                .orElseThrow(() -> new UsernameNotFoundException("Not an admin account."));
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .build();
    }
}
