package com.editor.model;

import com.editor.model.crdt.CRDTCharacter;
import com.editor.model.crdt.CRDTTree;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

@Data
public class Document {
    private final String id;
    private String name;
    private final String ownerId;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final CRDTTree crdtTree;
    private final String editorCode;
    private final String viewerCode;

    // Add operation history to the document for persistence
    private final Map<String, List<Operation>> operationHistory = new HashMap<>();
    private final Map<String, Stack<Operation>> redoStack = new HashMap<>();

    public Document(String name, String ownerId) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.crdtTree = new CRDTTree();

        // Generate unique codes for editors and viewers
        this.editorCode = generateUniqueCode("ED");
        this.viewerCode = generateUniqueCode("VW");
    }

    private String generateUniqueCode(String prefix) {
        // Generate a short, readable code for sharing
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public void updateTimestamp() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getContentAsString() {
        return crdtTree.generateDocumentText();
    }

    // Add this method to record an operation in the history
    public synchronized void recordOperation(String userId, Operation operation) {
        // Initialize the user's history list if it doesn't exist
        operationHistory.computeIfAbsent(userId, k -> new ArrayList<>());
        List<Operation> userHistory = operationHistory.get(userId);
        userHistory.add(operation);

        // Limit history size to 3 (as required by project)
        while (userHistory.size() > 3) {
            userHistory.remove(0);
        }

        // Clear redo stack when a new operation is performed
        redoStack.computeIfAbsent(userId, k -> new Stack<>());
        redoStack.get(userId).clear();

        System.out.println("Recorded operation for user " + userId + ", type: " +
                operation.getType() + ", history size now: " + userHistory.size());
    }

    public synchronized Operation recordUndo(String userId) {
        // Get the user's history
        List<Operation> userHistory = operationHistory.getOrDefault(userId, new ArrayList<>());
        if (userHistory.isEmpty()) {
            return null;  // Nothing to undo
        }

        // Get the last operation
        Operation lastOperation = userHistory.remove(userHistory.size() - 1);

        // Add to redo stack
        redoStack.computeIfAbsent(userId, k -> new Stack<>()).push(lastOperation);

        // Create inverse operation
        Operation inverseOperation;
        if (lastOperation.getType() == Operation.OperationType.INSERT) {
            // Undo insert by deleting
            inverseOperation = Operation.delete(userId, lastOperation.getCharacterId());
            // Mark as deleted in CRDT tree
            CRDTCharacter character = crdtTree.getCharacter(lastOperation.getCharacterId());
            if (character != null) {
                character.setDeleted(true);
            }
        } else {
            // Undo delete by re-inserting
            CRDTCharacter character = lastOperation.getValue();
            if (character != null) {
                character.setDeleted(false);
                inverseOperation = Operation.insert(userId, character.getId(), character, character.getParentId());
            } else {
                return null;  // Can't undo without the character info
            }
        }

        return inverseOperation;
    }


    /**
     * Process an undo operation from a user
     * @param userId The user ID
     * @return The inverse operation to be broadcast, or null if there's nothing to undo
     */
    public synchronized Operation processUndo(String userId) {
        // Get the user's operation history
        List<Operation> userHistory = operationHistory.getOrDefault(userId, new ArrayList<>());

        // Check if there's anything to undo
        if (userHistory.isEmpty()) {
            System.out.println("No operations to undo for user " + userId);
            return null;
        }

        // Get the last operation
        Operation lastOperation = userHistory.remove(userHistory.size() - 1);

        // Add to redo stack
        redoStack.computeIfAbsent(userId, k -> new Stack<>()).push(lastOperation);

        System.out.println("Processing undo for user " + userId + ", operation: " + lastOperation.getType());

        // Create inverse operation
        Operation inverseOperation = null;

        if (lastOperation.getType() == Operation.OperationType.INSERT) {
            // Undo insert by deleting
            String characterId = lastOperation.getCharacterId();
            CRDTCharacter character = crdtTree.getCharacter(characterId);

            if (character != null) {
                character.setDeleted(true);
                inverseOperation = Operation.delete(userId, characterId, character);
                System.out.println("Created DELETE operation for undo with characterId: " + characterId);
            }
        } else if (lastOperation.getType() == Operation.OperationType.DELETE) {
            // Undo delete by re-inserting
            CRDTCharacter character = lastOperation.getValue();

            if (character != null) {
                // Mark as not deleted
                CRDTCharacter existingChar = crdtTree.getCharacter(character.getId());
                if (existingChar != null) {
                    existingChar.setDeleted(false);

                    // Create inverse operation
                    inverseOperation = Operation.insert(
                            userId,
                            existingChar.getId(),
                            existingChar,
                            existingChar.getParentId()
                    );

                    System.out.println("Created INSERT operation for undo with characterId: " +
                            existingChar.getId());
                }
            }
        }

        return inverseOperation;
    }

    /**
     * Process a redo operation from a user
     * @param userId The user ID
     * @return The operation to be broadcast, or null if there's nothing to redo
     */
    public synchronized Operation processRedo(String userId) {
        // Get the user's redo stack
        Stack<Operation> userRedoStack = redoStack.getOrDefault(userId, new Stack<>());

        // Check if there's anything to redo
        if (userRedoStack.isEmpty()) {
            System.out.println("No operations to redo for user " + userId);
            return null;
        }

        // Get the operation to redo
        Operation operationToRedo = userRedoStack.pop();

        // Add back to history
        operationHistory.computeIfAbsent(userId, k -> new ArrayList<>()).add(operationToRedo);

        System.out.println("Processing redo for user " + userId + ", operation: " +
                operationToRedo.getType());

        // Apply operation
        Operation resultOperation = null;

        if (operationToRedo.getType() == Operation.OperationType.INSERT) {
            // Redo insert
            CRDTCharacter character = operationToRedo.getValue();

            if (character != null) {
                CRDTCharacter existingChar = crdtTree.getCharacter(character.getId());
                if (existingChar != null) {
                    existingChar.setDeleted(false);
                } else {
                    // If character doesn't exist yet, add it to the tree
                    crdtTree.insertExistingCharacter(character);
                }

                resultOperation = Operation.insert(
                        userId,
                        character.getId(),
                        character,
                        character.getParentId()
                );
            }
        } else if (operationToRedo.getType() == Operation.OperationType.DELETE) {
            // Redo delete
            String characterId = operationToRedo.getCharacterId();
            CRDTCharacter character = crdtTree.getCharacter(characterId);

            if (character != null) {
                character.setDeleted(true);
                resultOperation = Operation.delete(userId, characterId, character);
            }
        }

        return resultOperation;
    }
}