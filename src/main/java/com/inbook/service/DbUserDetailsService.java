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
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String cleanEmail = email == null ? "" : email.trim().toLowerCase();
        AppUser u = repo.findByEmail(cleanEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + cleanEmail));

        return org.springframework.security.core.userdetails.User
                .withUsername(u.getEmail())
                .password(u.getPasswordHash())
                .authorities(AuthorityUtils.commaSeparatedStringToAuthorityList(u.getRoles()))
                .disabled(!u.isEnabled())
                .build();
    }

    public AppUser registerUser(String email, String passwordHash, String name, String surname, String roles, boolean enabled){
        AppUser u = new AppUser();

        u.setUsername(email);
        u.setPasswordHash(passwordHash);
        u.setName(name);
        u.setSurname(surname);
        u.setRoles("TYPE_DOCENTE"); //registra di default come docente
        u.setEnabled(enabled);
        u.setEmail(email);

        return repo.save(u);
    }

}
