package com.editor.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class DocumentDTO {
    private String id;
    private String name;
    private String ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String content;
    private String editorCode;
    private String viewerCode;
    private boolean isOwner;
    private List<CRDTCharacterDTO> crdtCharacters;

    public List<CRDTCharacterDTO> getCrdtCharacters() {
        return crdtCharacters;
    }

    public void setCrdtCharacters(List<CRDTCharacterDTO> crdtCharacters) {
        this.crdtCharacters = crdtCharacters;
    }


}