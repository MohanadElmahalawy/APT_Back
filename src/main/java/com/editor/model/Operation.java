package com.editor.model;

import lombok.Data;

import java.time.LocalDateTime;
import com.editor.model.crdt.CRDTCharacter;

@Data
public class Operation {
    public enum OperationType {
        INSERT,
        DELETE
    }

    private final String id;
    private final String userId;
    private final OperationType type;
    private final LocalDateTime timestamp;
    private final String characterId;
    private CRDTCharacter value; // Remove final keyword to allow setting later
    private final String parentId;

    // Constructor for INSERT operation
    public static Operation insert(String userId, String characterId, CRDTCharacter value, String parentId) {
        return new Operation(
                userId + "-" + System.currentTimeMillis(),
                userId,
                OperationType.INSERT,
                LocalDateTime.now(),
                characterId,
                value,
                parentId
        );
    }

    // Constructor for DELETE operation with value
    public static Operation delete(String userId, String characterId, CRDTCharacter value) {
        return new Operation(
                userId + "-" + System.currentTimeMillis(),
                userId,
                OperationType.DELETE,
                LocalDateTime.now(),
                characterId,
                value,
                null
        );
    }

    // Constructor for DELETE operation without value
    public static Operation delete(String userId, String characterId) {
        return new Operation(
                userId + "-" + System.currentTimeMillis(),
                userId,
                OperationType.DELETE,
                LocalDateTime.now(),
                characterId,
                null,
                null
        );
    }

    private Operation(String id, String userId, OperationType type, LocalDateTime timestamp,
                      String characterId, CRDTCharacter value, String parentId) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.timestamp = timestamp;
        this.characterId = characterId;
        this.value = value;
        this.parentId = parentId;
    }

    // Add explicit setter for value
    public void setValue(CRDTCharacter value) {
        this.value = value;
    }
}