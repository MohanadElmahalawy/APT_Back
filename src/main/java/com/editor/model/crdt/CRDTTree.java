package com.editor.model.crdt;

import lombok.Data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class CRDTTree {
    public static final String ROOT_ID = "ROOT";
    private final Map<String, CRDTCharacter> characters;
    private final Map<String, List<CRDTCharacter>> childrenMap;
    private final Map<String, List<PendingOperation>> pendingOperations;

    public CRDTTree() {
        this.characters = new ConcurrentHashMap<>();
        this.childrenMap = new ConcurrentHashMap<>();
        this.pendingOperations = new ConcurrentHashMap<>();
        this.childrenMap.put(ROOT_ID, new ArrayList<>());
    }

    /**
     * Class to represent operations waiting for their parent to be inserted
     */
    private static class PendingOperation {
        private final String userId;
        private final char value;
        private final String parentId;
        private final long timestamp;

        public PendingOperation(String userId, char value, String parentId, long timestamp) {
            this.userId = userId;
            this.value = value;
            this.parentId = parentId;
            this.timestamp = timestamp;
        }
    }

    /*
     * Insert a character into the CRDT tree
     */
    /**
     * Insert a character into the CRDT tree
     */
    public synchronized CRDTCharacter insertCharacter(String userId, char value, String parentId) {
        // Validate parent ID
        if (parentId == null) {
            parentId = ROOT_ID;
        }

        // Get timestamp for this operation - CRUCIAL CHANGE: consistent timestamp generation
        long timestamp = System.currentTimeMillis();

        // Create the character with DETERMINISTIC ID FORMAT
        CRDTCharacter character = new CRDTCharacter(userId, value, parentId, timestamp);
        String charId = character.getId();

        // Add to characters map
        characters.put(charId, character);

        // Add to children map
        List<CRDTCharacter> children = childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>());
        children.add(character);

        // Initialize the character's children list
        childrenMap.putIfAbsent(charId, new ArrayList<>());

        return character;
    }


    public synchronized void insertExistingCharacter(CRDTCharacter character) {
        String charId = character.getId();
        String parentId = character.getParentId();

        // Add to characters map
        characters.put(charId, character);

        // Add to children map
        List<CRDTCharacter> children = childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>());
        children.add(character);

        // Initialize an empty list for this character's children
        childrenMap.putIfAbsent(charId, new ArrayList<>());

        System.out.println("Inserted character '" + character.getValue() +
                "' with ID " + charId +
                " and parent " + parentId);
    }

    public synchronized void reparentCharacter(String characterId, String newParentId) {
        CRDTCharacter character = characters.get(characterId);
        if (character == null) {
            System.err.println("Cannot reparent: Character not found: " + characterId);
            return;
        }

        // Detect cycle - don't allow a character to be its own ancestor
        if (wouldCreateCycle(characterId, newParentId)) {
            System.err.println("Cannot reparent: Would create cycle");
            return;
        }

        String oldParentId = character.getParentId();

        // Only proceed if there's actually a change
        if (oldParentId.equals(newParentId)) {
            return;
        }

        System.out.println("Reparenting character " + characterId +
                " (value: '" + character.getValue() + "') from " +
                oldParentId + " to " + newParentId);

        // Remove from old parent's children list
        List<CRDTCharacter> oldParentChildren = childrenMap.get(oldParentId);
        if (oldParentChildren != null) {
            oldParentChildren.removeIf(c -> c.getId().equals(characterId));
        }

        // Create a new character with updated parent (since CRDTCharacter is immutable)
        CRDTCharacter updatedChar = new CRDTCharacter(
                character.getId(),
                character.getUserId(),
                character.getValue(),
                newParentId,
                character.getTimestamp(),
                character.isDeleted()
        );

        // Replace in characters map
        characters.put(characterId, updatedChar);

        // Add to new parent's children list
        List<CRDTCharacter> newParentChildren = childrenMap.computeIfAbsent(
                newParentId, k -> new ArrayList<>());
        newParentChildren.add(updatedChar);
    }

    // Helper method to detect cycles in the parent chain
    private boolean wouldCreateCycle(String characterId, String newParentId) {
        // If parent is ROOT, no cycle is possible
        if (ROOT_ID.equals(newParentId)) {
            return false;
        }

        // If trying to make a character its own parent, that's a cycle
        if (characterId.equals(newParentId)) {
            return true;
        }

        // Check if the new parent has this character as an ancestor
        String currentId = newParentId;
        Set<String> visited = new HashSet<>();

        while (!ROOT_ID.equals(currentId) && !visited.contains(currentId)) {
            visited.add(currentId);
            CRDTCharacter current = characters.get(currentId);

            if (current == null) {
                break;
            }

            // The proposed parent has the character as an ancestor - would create cycle
            if (current.getParentId().equals(characterId)) {
                return true;
            }

            currentId = current.getParentId();
        }

        return false;
    }
    /**
     * Queue an operation that's waiting for its parent
     */
    private void queuePendingOperation(String userId, char value, String parentId, long timestamp) {
        // Check again in case parent was created while waiting
        if (!ROOT_ID.equals(parentId) && !characters.containsKey(parentId)) {
            // Add to pending operations for this parent
            List<PendingOperation> operations = pendingOperations.computeIfAbsent(parentId,
                    k -> new ArrayList<>());
            operations.add(new PendingOperation(userId, value, parentId, timestamp));
            System.out.println("Queued operation for character '" + value + "' waiting for parent: " + parentId);
            System.out.println("Current pending operations count: " + pendingOperations.size());
        } else {
            // Parent exists now, create character
            System.out.println("Parent now exists, creating character '" + value + "' with parent: " + parentId);
            createCharacter(userId, value, parentId, timestamp);
        }
    }

    public CRDTCharacter getCharacter(String characterId) {
        return characters.get(characterId);
    }

    /*
     * Create a character and process any pending operations that depend on it
     */
    /**
     * Create a character and process any pending operations that depend on it
     */
    private CRDTCharacter createCharacter(String userId, char value, String parentId, long timestamp) {
        // Create the character
        CRDTCharacter character = new CRDTCharacter(userId, value, parentId, timestamp);
        String charId = character.getId();

        System.out.println("Creating character '" + value + "' with ID " + charId +
                " and parent " + parentId);

        // Add to characters map
        characters.put(charId, character);

        // Add to children map
        List<CRDTCharacter> children = childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>());
        if (!children.contains(character)) {
            children.add(character);
            System.out.println("Added character to parent's children list");
        }

        // Initialize the character's children list
        childrenMap.putIfAbsent(charId, new ArrayList<>());

        // Process any pending operations waiting for this character
        processPendingOperations(charId);

        // Print tree state for debugging
        printTreeState();

        return character;
    }



    /**
     * Print the current state of the tree for debugging
     */
    public void printTreeState() {
        System.out.println("--- CRDT Tree State ---");
        System.out.println("Total characters: " + characters.size());

        // Count visible characters
        long visibleCount = characters.values().stream()
                .filter(c -> !c.isDeleted())
                .count();
        System.out.println("Visible characters: " + visibleCount);

        // Print first few characters (for brevity)
        System.out.println("First few characters:");
        int count = 0;
        for (CRDTCharacter character : characters.values()) {
            if (count >= 10) break;
            System.out.println("  " + character.getId() + " -> '" + character.getValue() +
                    "' (parent: " + character.getParentId() +
                    ", deleted: " + character.isDeleted() + ")");
            count++;
        }

        // Print document content
        String content = generateDocumentText();
        System.out.println("Current document content: \"" + content + "\"");
        System.out.println("------------------");
    }

    /**
     * Process any operations that were waiting for a specific parent ID
     */
    private void processPendingOperations(String parentId) {
        List<PendingOperation> operations = pendingOperations.remove(parentId);
        if (operations != null && !operations.isEmpty()) {
            // Create a copy to avoid concurrent modification issues
            List<PendingOperation> opsCopy = new ArrayList<>(operations);
            for (PendingOperation op : opsCopy) {
                createCharacter(op.userId, op.value, op.parentId, op.timestamp);
            }
        }
    }

    /**
     * Delete a character from the CRDT tree
     */
    public synchronized void deleteCharacter(String characterId) {
        if (!characters.containsKey(characterId)) {
            throw new IllegalArgumentException("Character not found: " + characterId);
        }

        CRDTCharacter character = characters.get(characterId);
        character.setDeleted(true);
    }



    private void traverseTree(String nodeId, StringBuilder text) {
        if (!childrenMap.containsKey(nodeId)) {
            return;
        }

        // Get all children of this node
        List<CRDTCharacter> children = new ArrayList<>(childrenMap.getOrDefault(nodeId, Collections.emptyList()));

        // IMPORTANT: Sort consistently across all clients and server
        // Sort by timestamp (ascending)
        children.sort((a, b) -> {
            // Direct comparison of long timestamp values
            int timeCompare = Long.compare(a.getTimestamp(), b.getTimestamp());
            if (timeCompare != 0) {
                return timeCompare;
            }
            // Then by user ID for consistent tie-breaking
            return a.getUserId().compareTo(b.getUserId());
        });

        // Process each child in order
        for (CRDTCharacter child : children) {
            if (!child.isDeleted()) {
                text.append(child.getValue());
            }
            // ALWAYS traverse children even for deleted nodes
            traverseTree(child.getId(), text);
        }
    }

    public List<CRDTCharacter> getChildren(String parentId) {
        List<CRDTCharacter> children = new ArrayList<>();

        // Find all characters that have this character as parent
        for (CRDTCharacter character : characters.values()) {
            if (!character.isDeleted() && character.getParentId().equals(parentId)) {
                children.add(character);
            }
        }

        // Sort children by timestamp and user ID for consistency
        children.sort((a, b) -> {
            // Direct comparison of long timestamp values
            int timeCompare = Long.compare(a.getTimestamp(), b.getTimestamp());

            if (timeCompare != 0) {
                return timeCompare;
            }

            // Break ties using user ID for consistency
            return a.getUserId().compareTo(b.getUserId());
        });

        return children;
    }



    public String generateDocumentText() {
        StringBuilder text = new StringBuilder();

        // First, collect all visible characters in a flat list
        List<CRDTCharacter> visibleChars = getAllVisibleCharactersInOrder();

        // Then append them to build the document text
        for (CRDTCharacter character : visibleChars) {
            text.append(character.getValue());
        }

        String result = text.toString();
        System.out.println("Generated document text: \"" + result + "\"");
        return result;
    }

    // Helper method to get all visible characters in proper order
    private List<CRDTCharacter> getAllVisibleCharactersInOrder() {
        // Create a mapping of character ID to its child IDs for quick lookups
        Map<String, List<String>> childIdMap = new HashMap<>();

        // Find all non-deleted characters
        List<CRDTCharacter> allChars = new ArrayList<>();
        for (CRDTCharacter character : characters.values()) {
            if (!character.isDeleted()) {
                allChars.add(character);

                // Build parent-child mapping
                String parentId = character.getParentId();
                childIdMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(character.getId());
            }
        }

        // Sort all children lists consistently
        for (List<String> children : childIdMap.values()) {
            // Sort by relevant criteria
            children.sort((id1, id2) -> {
                CRDTCharacter c1 = characters.get(id1);
                CRDTCharacter c2 = characters.get(id2);

                int timeCompare = Long.compare(c1.getTimestamp(), c2.getTimestamp());
                if (timeCompare != 0) {
                    return timeCompare;
                }

                return c1.getUserId().compareTo(c2.getUserId());
            });
        }

        // Traverse from ROOT to build properly ordered list
        List<CRDTCharacter> result = new ArrayList<>();
        traverseAndCollect(ROOT_ID, childIdMap, result);

        return result;
    }

    private void traverseAndCollect(String nodeId, Map<String, List<String>> childIdMap, List<CRDTCharacter> result) {
        List<String> childIds = childIdMap.get(nodeId);
        if (childIds == null) {
            return;
        }

        for (String childId : childIds) {
            CRDTCharacter character = characters.get(childId);
            if (character != null) {
                result.add(character);
                // Recursively process this character's children
                traverseAndCollect(childId, childIdMap, result);
            }
        }
    }


    public synchronized void clearCharacters() {
        characters.clear();
        childrenMap.clear();
        pendingOperations.clear();
        // Reinitialize ROOT node
        childrenMap.put(ROOT_ID, new ArrayList<>());
    }

}