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

    public AppUser RegisterUser (String email, String passwordHash, String username, String name, String surname){
        AppUser u = new AppUser();

        u.setUsername(username);
        u.setPasswordHash(passwordHash);
        u.setName(name);
        u.setSurname(surname);
        u.setRoles("TYPE_DOCENTE");
        u.setEnabled(true);
        u.setEmail(email);

        return repo.save(u);
    }

}