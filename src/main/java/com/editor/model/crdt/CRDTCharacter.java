package com.editor.model.crdt;

import lombok.Data;

import java.util.Objects;

@Data
public class CRDTCharacter {
    private final String id;
    private final char value;
    private final String parentId;
    private boolean deleted;
    private final String userId;
    private final long timestamp;

    public CRDTCharacter(String userId, char value, String parentId, long timestamp) {
        this.userId = userId;
        this.value = value;
        this.parentId = parentId;
        this.timestamp = timestamp;
        // CRITICAL: Deterministic ID generation
        this.id = userId + "-" + timestamp;
        this.deleted = false;
    }



    public CRDTCharacter(String characterId, String userId, Character value, String parentId, long l, boolean b) {
        this.id = characterId;

        this.userId = userId;
        this.value = value;
        this.parentId = parentId;
        this.timestamp = l;

        this.deleted = false;

    }

    public String getId() {
        return id;
    }

    public char getValue() {
        return value;
    }

    public String getParentId() {
        return parentId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getUserId() {
        return userId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CRDTCharacter that = (CRDTCharacter) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }


}
