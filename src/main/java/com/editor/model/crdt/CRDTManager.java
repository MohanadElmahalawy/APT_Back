package com.editor.model.crdt;

import com.editor.model.Operation;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class CRDTManager {
    @Getter
    private final CRDTTree tree;
    private final Map<String, List<Operation>> operationHistory;
    private final Map<String, Stack<Operation>> redoStack;
    private final int maxHistorySize = 3;  // Store 3 operations as required by project spec

    public CRDTManager(CRDTTree tree) {
        this.tree = tree;
        this.operationHistory = new HashMap<>();
        this.redoStack = new HashMap<>();
    }

    // Overloaded constructor that accepts existing history and redo stack
    public CRDTManager(CRDTTree tree, Map<String, List<Operation>> operationHistory, Map<String, Stack<Operation>> redoStack) {
        this.tree = tree;
        this.operationHistory = operationHistory;
        this.redoStack = redoStack;
    }

    public CRDTCharacter applyInsertOperation(String userId, char value, String parentId) {
        System.out.println("Applying insert operation: userId=" + userId +
                ", value='" + value + "', parentId=" + parentId);

        CRDTCharacter character = tree.insertCharacter(userId, value, parentId);

        if (character == null) {
            System.out.println("Character insertion failed!");
            return null;
        }

        System.out.println("Character inserted successfully with ID: " + character.getId());

        return character;
    }

    public void applyDeleteOperation(String userId, String characterId) {
        // First get the character to store it for potential redo
        CRDTCharacter character = tree.getCharacter(characterId);

        if (character == null || character.isDeleted()) {
            System.out.println("Character not found or already deleted: " + characterId);
            return; // Character doesn't exist or is already deleted
        }

        tree.deleteCharacter(characterId);
        System.out.println("Character deleted: " + characterId);
    }

    /**
     * Explicitly add an operation to a user's history
     */
    public void addToUserHistory(String userId, Operation operation) {
        operationHistory.putIfAbsent(userId, new ArrayList<>());
        List<Operation> userHistory = operationHistory.get(userId);
        userHistory.add(operation);

        // Limit history size to 3 operations (as required by the project spec)
        while (userHistory.size() > maxHistorySize) {
            userHistory.remove(0);
        }

        // Clear redo stack when a new operation is performed
        clearRedoStack(userId);

        System.out.println("Added operation to history for user " + userId +
                ", type: " + operation.getType() +
                ", characterId: " + operation.getCharacterId() +
                ", new history size: " + userHistory.size());
    }

    /**
     * Get the size of a user's operation history
     */
    public int getUserHistorySize(String userId) {
        if (!operationHistory.containsKey(userId)) {
            return 0;
        }
        return operationHistory.get(userId).size();
    }

    private void clearRedoStack(String userId) {
        redoStack.computeIfAbsent(userId, k -> new Stack<>()).clear();
    }

    public Operation undoLastOperation(String userId) {
        System.out.println("Processing UNDO operation for user: " + userId);

        if (!operationHistory.containsKey(userId) || operationHistory.get(userId).isEmpty()) {
            System.out.println("No operations to undo for user: " + userId);
            return null;
        }

        List<Operation> userHistory = operationHistory.get(userId);

        // Print the current history for debugging
        System.out.println("Current history for user " + userId + " (size: " + userHistory.size() + "):");
        for (int i = 0; i < userHistory.size(); i++) {
            Operation op = userHistory.get(i);
            System.out.println("  " + i + ": " + op.getType() + " - characterId: " + op.getCharacterId());
        }

        Operation lastOperation = userHistory.remove(userHistory.size() - 1);

        System.out.println("Undoing operation: " + lastOperation.getType() +
                " for character: " + lastOperation.getCharacterId());

        // Add to redo stack
        redoStack.computeIfAbsent(userId, k -> new Stack<>()).push(lastOperation);

        // Perform the inverse operation
        Operation resultOperation = null;

        if (lastOperation.getType() == Operation.OperationType.INSERT) {
            // Undo an insert by deleting the character
            String charId = lastOperation.getCharacterId();
            tree.deleteCharacter(charId);
            resultOperation = Operation.delete(userId, charId);
            System.out.println("Created DELETE operation for characterId: " + charId);
        } else if (lastOperation.getType() == Operation.OperationType.DELETE) {
            // Undo delete by re-inserting the character
            CRDTCharacter character = lastOperation.getValue();
            if (character != null) {
                character.setDeleted(false);
                // No need to re-add to tree as it's still there (just marked as deleted)
                resultOperation = Operation.insert(userId, character.getId(), character, character.getParentId());
                System.out.println("Created INSERT operation to restore character: " + character.getId());
            }
        }

        // Debug: print the document state after undo
        String documentText = tree.generateDocumentText();
        System.out.println("Document text after UNDO: \"" + documentText + "\"");

        return resultOperation;
    }

    public Operation redoLastUndoneOperation(String userId) {
        Stack<Operation> userRedoStack = redoStack.get(userId);
        if (userRedoStack == null || userRedoStack.isEmpty()) {
            System.out.println("No operations to redo for user: " + userId);
            return null;
        }

        // Print the current redo stack for debugging
        System.out.println("Current redo stack for user " + userId + " (size: " + userRedoStack.size() + "):");
        int index = 0;
        for (Operation op : userRedoStack) {
            System.out.println("  " + index++ + ": " + op.getType() + " - characterId: " + op.getCharacterId());
        }

        Operation operationToRedo = userRedoStack.pop();
        System.out.println("Redoing operation: " + operationToRedo.getType() +
                " for character: " + operationToRedo.getCharacterId());

        // Add back to history
        operationHistory.computeIfAbsent(userId, k -> new ArrayList<>()).add(operationToRedo);

        // Apply the operation
        Operation resultOperation = null;

        if (operationToRedo.getType() == Operation.OperationType.INSERT) {
            // Redo an insert
            CRDTCharacter character = operationToRedo.getValue();
            if (character != null) {
                character.setDeleted(false);
                resultOperation = Operation.insert(
                        userId,
                        character.getId(),
                        character,
                        character.getParentId()
                );
            }
        } else if (operationToRedo.getType() == Operation.OperationType.DELETE) {
            // Redo a delete
            String charId = operationToRedo.getCharacterId();
            tree.deleteCharacter(charId);
            resultOperation = Operation.delete(userId, charId);
        }

        // Debug: print the document state after redo
        String documentText = tree.generateDocumentText();
        System.out.println("Document text after REDO: \"" + documentText + "\"");

        return resultOperation;
    }

    public String getDocumentText() {
        return tree.generateDocumentText();
    }
}