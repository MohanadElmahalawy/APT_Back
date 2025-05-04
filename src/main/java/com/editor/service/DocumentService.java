package com.editor.service;

import com.editor.dto.CRDTCharacterDTO;
import com.editor.dto.DocumentDTO;
import com.editor.model.Document;
import com.editor.model.User;
import com.editor.model.crdt.CRDTCharacter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    private final Map<String, Document> documents = new ConcurrentHashMap<>();
    private final Map<String, String> shareCodesToDocumentId = new ConcurrentHashMap<>();

    public Document createDocument(String name, String ownerId) {
        Document document = new Document(name, ownerId);
        documents.put(document.getId(), document);

        // Map share codes to document ID
        shareCodesToDocumentId.put(document.getEditorCode(), document.getId());
        shareCodesToDocumentId.put(document.getViewerCode(), document.getId());

        return document;
    }

    public Document importDocument(String name, String ownerId, String content) {
        Document document = createDocument(name, ownerId);

        if (content.isEmpty()) {
            return document;
        }

        // Always use ROOT as parent for the first character
        String parentId = "ROOT";

        // For each character in the content
        for (int i = 0; i < content.length(); i++) {
            // Insert the character
            CRDTCharacter newChar = document.getCrdtTree().insertCharacter(ownerId, content.charAt(i), parentId);

            // Add a small delay between insertions to ensure unique timestamps
            try {
                Thread.sleep(1); // 1ms delay should be enough to get different timestamps
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Update parentId for the next iteration
            parentId = newChar.getId();
        }

        return document;
    }



    public Document getDocument(String documentId) {
        return documents.get(documentId);
    }

    public Document getDocumentByShareCode(String shareCode) {
        String documentId = shareCodesToDocumentId.get(shareCode);
        if (documentId == null) {
            return null;
        }
        return documents.get(documentId);
    }

    public User.UserRole getUserRoleForDocument(String shareCode) {
        String documentId = shareCodesToDocumentId.get(shareCode);
        if (documentId == null) {
            return null;
        }

        Document document = documents.get(documentId);
        if (document == null) {
            return null;
        }

        // Determine if editor or viewer based on the share code
        if (shareCode.equals(document.getEditorCode())) {
            return User.UserRole.EDITOR;
        } else if (shareCode.equals(document.getViewerCode())) {
            return User.UserRole.VIEWER;
        }

        return null;
    }

    public DocumentDTO convertToDTO(Document document, String userId) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setName(document.getName());
        dto.setOwnerId(document.getOwnerId());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        dto.setContent(document.getContentAsString());
        dto.setOwner(document.getOwnerId().equals(userId));

        // Only include share codes if this is the owner
        if (document.getOwnerId().equals(userId)) {
            dto.setEditorCode(document.getEditorCode());
            dto.setViewerCode(document.getViewerCode());
        }

        // NEW CODE: Add CRDT tree data
        List<CRDTCharacterDTO> characterDTOs = new ArrayList<>();
        for (CRDTCharacter character : document.getCrdtTree().getCharacters().values()) {
            CRDTCharacterDTO charDTO = new CRDTCharacterDTO();
            charDTO.setId(character.getId());
            charDTO.setUserId(character.getUserId());
            charDTO.setValue(character.getValue());
            charDTO.setParentId(character.getParentId());
            charDTO.setTimestamp(character.getTimestamp());
            charDTO.setDeleted(character.isDeleted());
            characterDTOs.add(charDTO);
        }
        dto.setCrdtCharacters(characterDTOs);

        return dto;
    }


}