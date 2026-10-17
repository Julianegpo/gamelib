package com.gamelibrary.service;

import com.gamelibrary.domain.User;
import com.gamelibrary.error.NotFoundException;
import com.gamelibrary.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User findByUsername(String username) {
        return userRepository.findById(username)
                .orElseThrow(() -> new NotFoundException("User", username));
    }

    @Transactional
    public User create(User user) {
        if (userRepository.existsById(user.getUsername())) {
            throw new IllegalArgumentException("User already exists: " + user.getUsername());
        }
        return userRepository.save(user);
    }
}
