package com.editor.dto;

import com.editor.model.User;
import lombok.Data;

@Data
public class UserDTO {
    private String id;
    private String name;
    private CursorDTO cursor;
    private boolean isActive;
    private User.UserRole role;

    @Data
    public static class CursorDTO {
        private int line;
        private int column;
    }
}