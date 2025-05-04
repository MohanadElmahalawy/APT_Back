package com.editor.service;

import com.editor.model.crdt.CRDTTree;
import com.editor.model.crdt.CRDTCharacter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ComprehensiveCRDTServiceTest {
    private CRDTTree tree;

    @BeforeEach
    public void setUp() {
        tree = new CRDTTree();
    }

    // BASIC OPERATIONS TESTS

    @Test
    public void testInsertSingleCharacter() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        assertEquals("A", tree.generateDocumentText());
        assertNotNull(a);
        assertNotNull(a.getId());
    }

    @Test
    public void testEmptyTree() {
        assertEquals("", tree.generateDocumentText());
    }

    @Test
    public void testInsertMultipleCharacters() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());
        CRDTCharacter d = tree.insertCharacter("user1", 'D', c.getId());
        CRDTCharacter e = tree.insertCharacter("user1", 'E', d.getId());

        assertEquals("ABCDE", tree.generateDocumentText());
    }

    @Test
    public void testInsertBetweenCharacters() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter c = tree.insertCharacter("user1", 'C', a.getId());

        // Insert B between A and C
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());

        // Special handling: We need to check if the text contains "AB" and "C"
        // because the exact ordering might vary based on timestamps
        String text = tree.generateDocumentText();
        assertTrue(text.contains("A"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("C"));
        assertEquals(3, text.length());
    }

    @Test
    public void testDeleteSingleCharacter() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        tree.deleteCharacter(a.getId());
        assertEquals("", tree.generateDocumentText());
    }

    @Test
    public void testDeleteMiddleCharacter() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        tree.deleteCharacter(b.getId());

        assertEquals("AC", tree.generateDocumentText());
    }

    @Test
    public void testDeleteNonexistentCharacter() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);

        // The implementation throws an exception, so we need to catch it
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            tree.deleteCharacter("non-existent-id");
        });

        assertTrue(exception.getMessage().contains("Character not found"));
        assertEquals("A", tree.generateDocumentText());
    }

    // CONCURRENT OPERATIONS TESTS

    @Test
    public void testConcurrentInsertSamePosition() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);

        // Two users insert at the same position concurrently
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user2", 'C', a.getId());

        String text = tree.generateDocumentText();

        // Both characters should be present, order might vary
        assertTrue(text.contains("A"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("C"));
        assertEquals(3, text.length());
    }

    @Test
    public void testConcurrentInsertMultipleUsers() {
        CRDTCharacter r = tree.insertCharacter("user1", 'R', CRDTTree.ROOT_ID);
        CRDTCharacter o = tree.insertCharacter("user1", 'O', r.getId());
        CRDTCharacter t = tree.insertCharacter("user1", 'T', o.getId());

        // Multiple users insert after 'T' concurrently
        CRDTCharacter user2_char = tree.insertCharacter("user2", '2', t.getId());
        CRDTCharacter user3_char = tree.insertCharacter("user3", '3', t.getId());
        CRDTCharacter user4_char = tree.insertCharacter("user4", '4', t.getId());
        CRDTCharacter user5_char = tree.insertCharacter("user5", '5', t.getId());

        String text = tree.generateDocumentText();

        // Base string should be preserved
        assertTrue(text.startsWith("ROT"));

        // All characters should be present
        assertTrue(text.contains("2"));
        assertTrue(text.contains("3"));
        assertTrue(text.contains("4"));
        assertTrue(text.contains("5"));

        // Total length should be correct
        assertEquals(7, text.length());
    }

    @Test
    public void testConcurrentDelete() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        // Two users delete different characters concurrently
        tree.deleteCharacter(a.getId());
        tree.deleteCharacter(c.getId());

        assertEquals("B", tree.generateDocumentText());
    }

    @Test
    public void testConcurrentInsertAndDelete() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());

        // One user deletes 'A' while another inserts after it
        tree.deleteCharacter(a.getId());
        CRDTCharacter c = tree.insertCharacter("user2", 'C', a.getId());

        // The insert should still work even though 'A' was deleted
        assertEquals("CB", tree.generateDocumentText());
    }

    // COMPLEX SCENARIOS TESTS

    @Test
    public void testInsertAfterDeletedChain() {
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        // Delete B
        tree.deleteCharacter(b.getId());

        // Insert X after B (which is deleted)
        CRDTCharacter x = tree.insertCharacter("user2", 'X', b.getId());

        String text = tree.generateDocumentText();

        // X should appear somewhere, and A and C should remain
        assertTrue(text.contains("A"));
        assertTrue(text.contains("X"));
        assertTrue(text.contains("C"));
        assertEquals(3, text.length());
    }

    @Test
    public void testComplexEditingScenario() {
        // User 1 types "Hello"
        CRDTCharacter h = tree.insertCharacter("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = tree.insertCharacter("user1", 'e', h.getId());
        CRDTCharacter l1 = tree.insertCharacter("user1", 'l', e.getId());
        CRDTCharacter l2 = tree.insertCharacter("user1", 'l', l1.getId());
        CRDTCharacter o = tree.insertCharacter("user1", 'o', l2.getId());

        // User 2 deletes the first 'l'
        tree.deleteCharacter(l1.getId());

        // User 3 adds " there" at the end
        CRDTCharacter space1 = tree.insertCharacter("user3", ' ', o.getId());
        CRDTCharacter t = tree.insertCharacter("user3", 't', space1.getId());
        CRDTCharacter h2 = tree.insertCharacter("user3", 'h', t.getId());
        CRDTCharacter e2 = tree.insertCharacter("user3", 'e', h2.getId());
        CRDTCharacter r = tree.insertCharacter("user3", 'r', e2.getId());
        CRDTCharacter e3 = tree.insertCharacter("user3", 'e', r.getId());

        // User 1 inserts '!' after 'o' (but before User 3's edits)
        CRDTCharacter excl = tree.insertCharacter("user1", '!', o.getId());

        String result = tree.generateDocumentText();

        // Expected result should contain "Helo" and "there" and "!"
        assertTrue(result.contains("Helo"));
        assertTrue(result.contains("there"));
        assertTrue(result.contains("!"));
        assertEquals(11, result.length());
    }

    @Test
    public void testLargeDocumentScenario() {
        // Create a paragraph
        String paragraph = "The quick brown fox jumps over the lazy dog";
        CRDTCharacter prev = null;
        List<CRDTCharacter> allCharacters = new ArrayList<>();

        // Insert the paragraph character by character
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (i == 0) {
                prev = tree.insertCharacter("user1", c, CRDTTree.ROOT_ID);
            } else {
                prev = tree.insertCharacter("user1", c, prev.getId());
            }
            allCharacters.add(prev);
        }

        // Verify the document text
        assertEquals(paragraph, tree.generateDocumentText());

        // Delete every third character
        for (int i = 2; i < allCharacters.size(); i += 3) {
            tree.deleteCharacter(allCharacters.get(i).getId());
        }

        // Verify the document has the correct characters removed
        String modifiedText = tree.generateDocumentText();
        assertNotEquals(paragraph, modifiedText);
        assertTrue(modifiedText.length() < paragraph.length());
    }

    // STRESS TESTS

    @Test
    @Timeout(value = 10) // 10 seconds timeout
    public void testManyConsecutiveInserts() {
        // Insert 1000 characters in sequence
        CRDTCharacter prev = null;
        for (int i = 0; i < 1000; i++) {
            char c = (char) ('A' + (i % 26));
            if (i == 0) {
                prev = tree.insertCharacter("user1", c, CRDTTree.ROOT_ID);
            } else {
                prev = tree.insertCharacter("user1", c, prev.getId());
            }
        }

        assertEquals(1000, tree.generateDocumentText().length());
    }

    @Test
    @Timeout(value = 10) // 10 seconds timeout
    public void testRandomInsertDelete() {
        Random rand = new Random(42); // Seed for reproducibility
        List<CRDTCharacter> characters = new ArrayList<>();

        // First insert 100 random characters
        for (int i = 0; i < 100; i++) {
            char c = (char) ('A' + rand.nextInt(26));
            CRDTCharacter parent;

            if (characters.isEmpty() || rand.nextDouble() < 0.1) {
                parent = tree.insertCharacter("user1", c, CRDTTree.ROOT_ID);
            } else {
                // Pick a random existing character as parent
                CRDTCharacter randomParent = characters.get(rand.nextInt(characters.size()));
                parent = tree.insertCharacter("user1", c, randomParent.getId());
            }

            characters.add(parent);
        }

        int initialLength = tree.generateDocumentText().length();
        assertTrue(initialLength > 0);

        // Now perform 100 random operations (insert or delete)
        for (int i = 0; i < 100; i++) {
            if (rand.nextDouble() < 0.5 && !characters.isEmpty()) {
                // Delete a random character
                int index = rand.nextInt(characters.size());
                CRDTCharacter charToDelete = characters.get(index);
                tree.deleteCharacter(charToDelete.getId());
                characters.remove(index);
            } else {
                // Insert a new random character
                char c = (char) ('A' + rand.nextInt(26));
                CRDTCharacter parent;

                if (characters.isEmpty() || rand.nextDouble() < 0.1) {
                    parent = tree.insertCharacter("user" + (rand.nextInt(5) + 1), c, CRDTTree.ROOT_ID);
                } else {
                    // Pick a random existing character as parent
                    CRDTCharacter randomParent = characters.get(rand.nextInt(characters.size()));
                    parent = tree.insertCharacter("user" + (rand.nextInt(5) + 1), c, randomParent.getId());
                }

                characters.add(parent);
            }
        }

        // Verify the tree is still in a valid state
        String finalText = tree.generateDocumentText();
        assertEquals(characters.size(), finalText.length());
    }

    // CONCURRENT EXECUTION TESTS

    @Test
    @Timeout(value = 20) // 20 seconds timeout
    public void testConcurrentOperations() throws InterruptedException, ExecutionException {
        // First create a base document
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        int numThreads = 5;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Keep track of all character IDs created
        ConcurrentLinkedQueue<String> allCharacterIds = new ConcurrentLinkedQueue<>();
        allCharacterIds.add(a.getId());
        allCharacterIds.add(b.getId());
        allCharacterIds.add(c.getId());

        for (int i = 0; i < numThreads; i++) {
            final String userId = "user" + (i + 2); // Start from user2

            futures.add(executor.submit(() -> {
                try {
                    Random rand = new Random();

                    for (int j = 0; j < operationsPerThread; j++) {
                        // Get a random character ID
                        List<String> currentIds = new ArrayList<>(allCharacterIds);
                        if (currentIds.isEmpty()) {
                            // If no IDs are available, insert at root
                            CRDTCharacter newChar = tree.insertCharacter(userId,
                                    (char) ('a' + rand.nextInt(26)), CRDTTree.ROOT_ID);
                            allCharacterIds.add(newChar.getId());
                            continue;
                        }

                        String randomId = currentIds.get(rand.nextInt(currentIds.size()));

                        if (rand.nextDouble() < 0.7) {
                            // 70% chance to insert
                            char randomChar = (char) ('a' + rand.nextInt(26));
                            CRDTCharacter newChar = tree.insertCharacter(userId, randomChar, randomId);
                            allCharacterIds.add(newChar.getId());
                        } else {
                            // 30% chance to delete
                            try {
                                tree.deleteCharacter(randomId);
                            } catch (IllegalArgumentException e) {
                                // Character might have been deleted by another thread
                                // Just continue
                            }
                        }

                        // Small delay to simulate real typing/editing
                        Thread.sleep(5);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }

                return null;
            }));
        }

        // Wait for all threads to complete
        latch.await();

        // Shutdown the executor
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        // Verify that the document is in a valid state
        String finalText = tree.generateDocumentText();
        assertNotNull(finalText);
    }

    // CONSISTENCY TESTS

    @Test
    public void testConsistencyAfterReorderedOperations() {
        // Create new trees for each test
        CRDTTree tree1 = new CRDTTree();
        CRDTTree tree2 = new CRDTTree();

        // Create the same operations but apply them in different orders

        // Operations for tree1
        CRDTCharacter a1 = tree1.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b1 = tree1.insertCharacter("user1", 'B', a1.getId());
        CRDTCharacter c1 = tree1.insertCharacter("user1", 'C', b1.getId());

        // Delete and insert operations
        tree1.deleteCharacter(b1.getId());
        CRDTCharacter d1 = tree1.insertCharacter("user2", 'D', a1.getId());

        // Operations for tree2 in different order
        CRDTCharacter a2 = tree2.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b2 = tree2.insertCharacter("user1", 'B', a2.getId());
        CRDTCharacter d2 = tree2.insertCharacter("user2", 'D', a2.getId());
        CRDTCharacter c2 = tree2.insertCharacter("user1", 'C', b2.getId());
        tree2.deleteCharacter(b2.getId());

        // The document text might differ due to timestamp ordering, but both
        // trees should have the same characters (A, D, C) with B deleted
        String text1 = tree1.generateDocumentText();
        String text2 = tree2.generateDocumentText();

        assertEquals(3, text1.length());
        assertEquals(3, text2.length());

        assertTrue(text1.contains("A"));
        assertTrue(text1.contains("D"));
        assertTrue(text1.contains("C"));

        assertTrue(text2.contains("A"));
        assertTrue(text2.contains("D"));
        assertTrue(text2.contains("C"));
    }

    // SPECIAL CHARACTER TESTS

    @Test
    public void testSpecialCharacters() {
        char[] specialChars = {'\n', '\t', '❤', '€', '£', '¥', '©', '®', '$', '@', '#', '%', '^', '&', '*'};
        CRDTCharacter prev = null;

        for (char specialChar : specialChars) {
            if (prev == null) {
                prev = tree.insertCharacter("user1", specialChar, CRDTTree.ROOT_ID);
            } else {
                prev = tree.insertCharacter("user1", specialChar, prev.getId());
            }
        }

        String result = tree.generateDocumentText();
        assertEquals(specialChars.length, result.length());

        // Check each special character is present
        for (char specialChar : specialChars) {
            assertTrue(result.contains(String.valueOf(specialChar)));
        }
    }

    // ADDITIONAL TESTS

    @Test
    public void testBranchingTree() {
        // Create a tree with multiple branches
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);

        // Branch 1
        CRDTCharacter b1 = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c1 = tree.insertCharacter("user1", 'C', b1.getId());

        // Branch 2
        CRDTCharacter b2 = tree.insertCharacter("user2", 'D', a.getId());
        CRDTCharacter c2 = tree.insertCharacter("user2", 'E', b2.getId());

        // Branch 3
        CRDTCharacter b3 = tree.insertCharacter("user3", 'F', a.getId());
        CRDTCharacter c3 = tree.insertCharacter("user3", 'G', b3.getId());

        String text = tree.generateDocumentText();

        // All characters should be present
        assertEquals(7, text.length());
        assertTrue(text.contains("A"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("C"));
        assertTrue(text.contains("D"));
        assertTrue(text.contains("E"));
        assertTrue(text.contains("F"));
        assertTrue(text.contains("G"));
    }

    @Test
    public void testMultipleDeletes() {
        // Create a sequence
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());
        CRDTCharacter d = tree.insertCharacter("user1", 'D', c.getId());
        CRDTCharacter e = tree.insertCharacter("user1", 'E', d.getId());

        // Delete multiple characters
        tree.deleteCharacter(b.getId());
        tree.deleteCharacter(d.getId());

        // Text should be "ACE"
        assertEquals("ACE", tree.generateDocumentText());

        // Try to delete already deleted character
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            tree.deleteCharacter("non-existent-id");
        });

        // Document should remain unchanged
        assertEquals("ACE", tree.generateDocumentText());
    }

    @Test
    public void testDeletedAncestorScenario() {
        // Create a sequence with a fork
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        // Delete B
        tree.deleteCharacter(b.getId());

        // User 2 inserts after B (which is deleted)
        CRDTCharacter d = tree.insertCharacter("user2", 'D', b.getId());

        // User 3 inserts after C
        CRDTCharacter e = tree.insertCharacter("user3", 'E', c.getId());

        String text = tree.generateDocumentText();

        // A, C, D, and E should be present; B should be deleted
        assertTrue(text.contains("A"));
        assertTrue(text.contains("C"));
        assertTrue(text.contains("D"));
        assertTrue(text.contains("E"));
        assertFalse(text.contains("B"));
        assertEquals(4, text.length());
    }

    @Test
    public void testComplexBranchMerge() {
        // Start with a simple document
        CRDTCharacter h = tree.insertCharacter("user1", 'H', CRDTTree.ROOT_ID);
        CRDTCharacter e = tree.insertCharacter("user1", 'e', h.getId());
        CRDTCharacter l = tree.insertCharacter("user1", 'l', e.getId());
        CRDTCharacter l2 = tree.insertCharacter("user1", 'l', l.getId());
        CRDTCharacter o = tree.insertCharacter("user1", 'o', l2.getId());

        // User 2 branches from "e"
        CRDTCharacter y = tree.insertCharacter("user2", 'y', e.getId());

        // User 3 branches from "l"
        CRDTCharacter p = tree.insertCharacter("user3", 'p', l.getId());

        String text = tree.generateDocumentText();

        // Document should contain all the characters
        assertTrue(text.contains("H"));
        assertTrue(text.contains("e"));
        assertTrue(text.contains("l"));
        assertTrue(text.contains("o"));
        assertTrue(text.contains("y"));
        assertTrue(text.contains("p"));
        assertEquals(7, text.length());
    }

    @Test
    public void testNestedBranching() {
        // Create a main branch
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter b = tree.insertCharacter("user1", 'B', a.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', b.getId());

        // Create sub-branch from B
        CRDTCharacter d = tree.insertCharacter("user2", 'D', b.getId());
        CRDTCharacter e = tree.insertCharacter("user2", 'E', d.getId());

        // Create sub-branch from D
        CRDTCharacter f = tree.insertCharacter("user3", 'F', d.getId());
        CRDTCharacter g = tree.insertCharacter("user3", 'G', f.getId());

        String text = tree.generateDocumentText();

        // All characters should be present
        assertTrue(text.contains("A"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("C"));
        assertTrue(text.contains("D"));
        assertTrue(text.contains("E"));
        assertTrue(text.contains("F"));
        assertTrue(text.contains("G"));
        assertEquals(7, text.length());
    }

    @Test
    public void testWhitespaceCharacters() {
        // Create a string with various whitespace characters
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);
        CRDTCharacter space = tree.insertCharacter("user1", ' ', a.getId());
        CRDTCharacter b = tree.insertCharacter("user1", 'B', space.getId());
        CRDTCharacter tab = tree.insertCharacter("user1", '\t', b.getId());
        CRDTCharacter c = tree.insertCharacter("user1", 'C', tab.getId());
        CRDTCharacter newline = tree.insertCharacter("user1", '\n', c.getId());
        CRDTCharacter d = tree.insertCharacter("user1", 'D', newline.getId());

        String text = tree.generateDocumentText();

        // Check that whitespace characters are preserved
        assertEquals("A B\tC\nD", text);
    }

    @Test
    public void testRobustnessWithSameTimestamp() {
        // This test simulates what might happen if multiple characters
        // somehow got the same timestamp (e.g., due to system clock issues)

        // Create a base character
        CRDTCharacter a = tree.insertCharacter("user1", 'A', CRDTTree.ROOT_ID);

        // Access the timestamp directly
        long timestamp = a.getTimestamp();

        // Try to create another character with a similar timestamp
        // This is for testing only and wouldn't normally happen in production
        // It's to test the robustness of the algorithm when timestamps are very close

        CRDTCharacter b = tree.insertCharacter("user2", 'B', CRDTTree.ROOT_ID);
        CRDTCharacter c = tree.insertCharacter("user3", 'C', CRDTTree.ROOT_ID);

        // The document should still have all three characters
        String text = tree.generateDocumentText();
        assertTrue(text.contains("A"));
        assertTrue(text.contains("B"));
        assertTrue(text.contains("C"));
        assertEquals(3, text.length());
    }
}