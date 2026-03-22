package com.ijse.eca.users;

import com.ijse.eca.users.api.dto.AuthRequest;
import com.ijse.eca.users.api.dto.RegisterRequest;
import com.ijse.eca.users.config.JwtProperties;
import com.ijse.eca.users.repo.UserRepository;
import com.ijse.eca.users.service.AuthService;
import com.ijse.eca.users.web.ConflictException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:users;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "security.jwt.secret=01234567890123456789012345678901",
        "security.jwt.issuer=test",
        "security.jwt.expires-minutes=60"
})
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void register_ok() {
        var res = authService.register(new RegisterRequest("A", "a@test.com", "pw"));
        Assertions.assertNotNull(res.token());
        Assertions.assertTrue(userRepository.existsByEmail("a@test.com"));
        Assertions.assertEquals("test", jwtProperties.issuer());
    }

    @Test
    void register_conflict() {
        authService.register(new RegisterRequest("A", "a@test.com", "pw"));
        Assertions.assertThrows(ConflictException.class,
                () -> authService.register(new RegisterRequest("B", "a@test.com", "pw2")));
    }

    @Test
    void login_ok() {
        authService.register(new RegisterRequest("A", "a@test.com", "pw"));
        var res = authService.login(new AuthRequest("a@test.com", "pw"));
        Assertions.assertNotNull(res.token());
    }
}
