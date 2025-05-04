package com.editor.service;


import com.editor.model.User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserService {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public User createUser(String name, User.UserRole role) {
        User user = new User(name, role);
        users.put(user.getId(), user);
        return user;
    }

}