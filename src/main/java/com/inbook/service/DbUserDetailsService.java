package com.inbook.service;

import com.inbook.repository.AppUserRepository;
import com.inbook.repository.entity.AppUser;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DbUserDetailsService implements UserDetailsService {

    private final AppUserRepository repo;

    public DbUserDetailsService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser u = repo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getUsername())
                .password(u.getPasswordHash())
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(u.getRoles()))
                .disabled(!u.isEnabled())
                .build();
    }
}