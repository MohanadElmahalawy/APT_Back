package com.editor.dto;

import lombok.Data;

@Data
public class OperationDTO {
    public enum OperationType {
        INSERT,
        DELETE,
        CURSOR_MOVE,
        UNDO,
        REDO
    }

    private String userId;
    private OperationType type;
    private String characterId;
    private Character value;
    private String parentId;
    private CursorDTO cursor;

    @Data
    public static class CursorDTO {
        private int line;
        private int column;
    }
}