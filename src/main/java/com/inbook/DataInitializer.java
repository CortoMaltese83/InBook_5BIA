package com.inbook;

import com.inbook.repository.AppUserRepository;
import com.inbook.repository.entity.AppUser;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(AppUserRepository repo, PasswordEncoder encoder) {
        return args -> {
            if (repo.findByUsername("admin").isEmpty()) {
                repo.save(new AppUser(
                        "admin@gmail.com",
                        encoder.encode("admin123"),
                        "admin0",
                        "ad0",
                        "admin",
                        "ROLE_ADMIN,ROLE_USER",
                        true
                ));
            }
        };
    }
}