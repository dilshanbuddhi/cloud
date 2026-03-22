package com.ijse.eca.users.api;

import com.ijse.eca.users.api.dto.UserResponse;
import com.ijse.eca.users.domain.User;
import com.ijse.eca.users.repo.UserRepository;
import com.ijse.eca.users.web.NotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public UserResponse getById(@PathVariable("id") Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
}
