package com.editor.service;

import com.editor.model.Operation;
import com.editor.model.crdt.CRDTCharacter;
import com.editor.model.crdt.CRDTManager;
import com.editor.model.crdt.CRDTTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CRDTFunctionalityTests {
    private CRDTTree tree;
    private CRDTManager manager;

    @BeforeEach
    public void setUp() {
        tree = new CRDTTree();
        manager = new CRDTManager(tree);
    }

    @Test
    public void testInsertCharacters() {
        // Insert some characters: "Hello"
        CRDTCharacter h = manager.applyInsertOperation("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = manager.applyInsertOperation("user1", 'e', h.getId());
        CRDTCharacter l1 = manager.applyInsertOperation("user1", 'l', e.getId());
        CRDTCharacter l2 = manager.applyInsertOperation("user1", 'l', l1.getId());
        CRDTCharacter o = manager.applyInsertOperation("user1", 'o', l2.getId());

        assertEquals("Hello", manager.getDocumentText());
    }

    @Test
    public void testDeleteCharacter() {
        // Insert "Hello"
        CRDTCharacter h = manager.applyInsertOperation("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = manager.applyInsertOperation("user1", 'e', h.getId());
        CRDTCharacter l1 = manager.applyInsertOperation("user1", 'l', e.getId());
        CRDTCharacter l2 = manager.applyInsertOperation("user1", 'l', l1.getId());
        CRDTCharacter o = manager.applyInsertOperation("user1", 'o', l2.getId());

        // Delete 'l'
        manager.applyDeleteOperation("user1", l1.getId());

        assertEquals("Helo", manager.getDocumentText());
    }

    @Test
    public void testUndoOperation() {
        // Insert "Hello"
        CRDTCharacter h = manager.applyInsertOperation("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = manager.applyInsertOperation("user1", 'e', h.getId());
        CRDTCharacter l1 = manager.applyInsertOperation("user1", 'l', e.getId());
        CRDTCharacter l2 = manager.applyInsertOperation("user1", 'l', l1.getId());
        CRDTCharacter o = manager.applyInsertOperation("user1", 'o', l2.getId());

        // Undo the last insertion (o)
        Operation undoOp = manager.undoLastOperation("user1");
        manager.undoLastOperation("user1");
        manager.undoLastOperation("user1");
        manager.undoLastOperation("user1");

        manager.redoLastUndoneOperation("user1");
        assertNotNull(undoOp);
        assertEquals(Operation.OperationType.DELETE, undoOp.getType());
        assertEquals(o.getId(), undoOp.getCharacterId());
        assertEquals("He", manager.getDocumentText());
    }

    @Test
    public void testRedoOperation() {
        // Insert "Hello"
        CRDTCharacter h = manager.applyInsertOperation("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = manager.applyInsertOperation("user1", 'e', h.getId());
        CRDTCharacter l1 = manager.applyInsertOperation("user1", 'l', e.getId());
        CRDTCharacter l2 = manager.applyInsertOperation("user1", 'l', l1.getId());
        CRDTCharacter o = manager.applyInsertOperation("user1", 'o', l2.getId());

        // Undo the last insertion (o)
        manager.undoLastOperation("user1");
        manager.undoLastOperation("user1");
        assertEquals("Hel", manager.getDocumentText());

        // Redo the last undone operation
        Operation redoOp = manager.redoLastUndoneOperation("user1");
        Operation redoOp1 = manager.redoLastUndoneOperation("user1");

        assertNotNull(redoOp);
        assertEquals(Operation.OperationType.INSERT, redoOp.getType());
        assertEquals("Hello", manager.getDocumentText());
    }

    @Test
    public void testInsertFromMultipleUsers() {
        // User 1 inserts "He"
        CRDTCharacter h = manager.applyInsertOperation("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = manager.applyInsertOperation("user1", 'e', h.getId());

        // User 2 inserts "ll"
        CRDTCharacter l1 = manager.applyInsertOperation("user2", 'l', e.getId());
        CRDTCharacter l2 = manager.applyInsertOperation("user2", 'l', l1.getId());

        // User 1 inserts "o"
        CRDTCharacter o = manager.applyInsertOperation("user1", 'o', l2.getId());

        assertEquals("Hello", manager.getDocumentText());
    }

    @Test
    public void testConcurrentEdits() {
        // User 1 inserts "AC"
        CRDTCharacter a = manager.applyInsertOperation("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter c = manager.applyInsertOperation("user1", 'C', a.getId());

        // User 2 inserts "B" between A and C
        CRDTCharacter b = manager.applyInsertOperation("user2", 'B', a.getId());

        assertEquals("ABC", manager.getDocumentText());
    }
}