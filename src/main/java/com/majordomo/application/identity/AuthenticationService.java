package com.majordomo.application.identity;

import com.majordomo.domain.port.out.identity.CredentialRepository;
import com.majordomo.domain.port.out.identity.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Bridges Spring Security authentication to the Majordomo domain model.
 *
 * <p>Implements {@link UserDetailsService} by loading user profiles and credentials
 * through the hexagonal outbound ports ({@link UserRepository}, {@link CredentialRepository}),
 * keeping Spring Security concerns out of the domain layer.</p>
 */
@Service
public class AuthenticationService implements UserDetailsService {

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;

    /**
     * Constructs the service with the required domain repositories.
     *
     * @param userRepository       port for looking up user profiles
     * @param credentialRepository port for looking up user credentials
     */
    public AuthenticationService(UserRepository userRepository,
                                  CredentialRepository credentialRepository) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
    }

    /**
     * Loads user details by username for Spring Security authentication.
     *
     * @param username the username to look up
     * @return the {@link UserDetails} containing username and hashed password
     * @throws UsernameNotFoundException if the user or their credential is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));

        var credential = credentialRepository.findByUserId(user.getId())
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No credential for user: " + username));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(credential.getHashedPassword())
                .authorities(List.of())
                .build();
    }
}
