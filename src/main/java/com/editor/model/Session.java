package com.editor.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class Session {
    private final String documentId;
    private final Map<String, User> activeUsers;
    private final Map<String, User> reconnectingUsers;
    private final Map<String, List<Operation>> missedOperations;
    private LocalDateTime lastActivity;

    public Session(String documentId) {
        this.documentId = documentId;
        this.activeUsers = new ConcurrentHashMap<>();
        this.reconnectingUsers = new ConcurrentHashMap<>();
        this.missedOperations = new ConcurrentHashMap<>();
        this.lastActivity = LocalDateTime.now();
    }

    public void addUser(User user) {
        activeUsers.put(user.getId(), user);
        updateActivity();
    }

    public void removeUser(String userId) {
        activeUsers.remove(userId);
        updateActivity();
    }

    public void moveToReconnecting(String userId) {
        User user = activeUsers.remove(userId);
        if (user != null) {
            reconnectingUsers.put(userId, user);
            missedOperations.putIfAbsent(userId, new ArrayList<>());
        }
        updateActivity();
    }

    public void reconnectUser(String userId) {
        User user = reconnectingUsers.remove(userId);
        if (user != null) {
            user.setActive(true);
            user.updateActivity();
            activeUsers.put(userId, user);
        }
        updateActivity();
    }

    public void addMissedOperation(Operation operation) {
        for (String userId : reconnectingUsers.keySet()) {
            missedOperations.get(userId).add(operation);
        }
        updateActivity();
    }

    public List<Operation> getMissedOperations(String userId) {
        List<Operation> operations = missedOperations.getOrDefault(userId, new ArrayList<>());
        missedOperations.remove(userId);
        return operations;
    }

    private void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }

    public boolean isEmpty() {
        return activeUsers.isEmpty() && reconnectingUsers.isEmpty();
    }
}