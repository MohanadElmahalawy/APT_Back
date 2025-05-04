package com.editor.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class User {
    private final String id;
    private String name;
    private CursorPosition cursorPosition;
    private boolean isActive;
    private LocalDateTime lastActivity;
    private UserRole role;

    public User(String name, UserRole role) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.role = role;
        this.isActive = true;
        this.lastActivity = LocalDateTime.now();
        this.cursorPosition = new CursorPosition(0, 0);
    }

    public enum UserRole {
        EDITOR,
        VIEWER
    }

    @Data
    public static class CursorPosition {
        private int line;
        private int column;

        public CursorPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}