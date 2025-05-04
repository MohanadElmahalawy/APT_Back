package com.editor.Controller;

//import com.editor.dto.CursorDTO;
import com.editor.dto.OperationDTO;
import com.editor.dto.UserDTO;
import com.editor.model.Document;
import com.editor.model.Operation;
import com.editor.model.crdt.CRDTCharacter;
import com.editor.model.crdt.CRDTTree;
import com.editor.service.CRDTService;
import com.editor.service.DocumentService;
import com.editor.service.SessionService;
import com.editor.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class WebSocketController {
    private final CRDTService crdtService;
    private final SessionService sessionService;
    private final UserService userService;
    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public WebSocketController(CRDTService crdtService,
                               SessionService sessionService,
                               UserService userService,
                               DocumentService documentService,
                               SimpMessagingTemplate messagingTemplate) {
        this.crdtService = crdtService;
        this.sessionService = sessionService;
        this.userService = userService;
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }


    @MessageMapping("/document/{documentId}/operation")
    public void handleOperation(@DestinationVariable String documentId,
                                @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String type = (String) payload.get("type");

        System.out.println("Server Received operation: " + type + " from user " + userId);

        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        // Handle operation based on type
        if ("INSERT".equals(type)) {
            // Extract character information directly from the payload
            String characterId = (String) payload.get("characterId");
            Character value = null;
            if (payload.get("value") instanceof Character) {
                value = (Character) payload.get("value");
            } else if (payload.get("value") instanceof String) {
                String valueStr = (String) payload.get("value");
                if (valueStr != null && !valueStr.isEmpty()) {
                    value = valueStr.charAt(0);
                }
            }
            String parentId = (String) payload.get("parentId");

            if (value != null && characterId != null) {
                // IMPORTANT: Use the client-provided characterId instead of generating a new one
                CRDTCharacter character = new CRDTCharacter(
                        characterId,  // Use the ID from the client
                        userId,
                        value,
                        parentId,
                        System.currentTimeMillis(),  // Still use server timestamp for ordering
                        false
                );

                // Add to document tree
                document.getCrdtTree().insertExistingCharacter(character);

                // Record operation
                Operation operation = Operation.insert(userId, characterId, character, parentId);
                document.recordOperation(userId, operation);

                // Broadcast the operation to all clients
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        operation
                );

                // Log the document state after operation
                System.out.println("Document content after operation: " + document.getContentAsString());
            }
        } else if ("DELETE".equals(type)) {
            String characterId = (String) payload.get("characterId");

            // Get the character to be deleted
            CRDTCharacter character = document.getCrdtTree().getCharacter(characterId);
            if (character == null) {
                System.err.println("Character not found for deletion: " + characterId);
                return;
            }

            // ONLY mark character as deleted - NO reparenting
            character.setDeleted(true);

            // Create operation for tracking
            Operation operation = Operation.delete(userId, characterId, character);

            // Record operation
            document.recordOperation(userId, operation);

            // Broadcast the delete operation to all clients
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/operation",
                    operation
            );

            System.out.println("Character with ID " + characterId + " marked as deleted");
        } else if ("REPARENT".equals(type)) {
            String characterId = (String) payload.get("characterId");
            String newParentId = (String) payload.get("newParentId");

            // Reparent the character in the tree
            document.getCrdtTree().reparentCharacter(characterId, newParentId);

            // Broadcast the reparent operation
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/operation",
                    payload  // Just forward the original payload
            );
        } else if ("UNDO".equals(type)) {
            // Process the undo operation
            Operation resultOperation = document.processUndo(userId);

            if (resultOperation != null) {
                // Broadcast the operation to all clients
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        resultOperation
                );

                // Send confirmation
                Map<String, Object> confirmation = new HashMap<>();
                confirmation.put("type", "UNDO_RESULT");
                confirmation.put("userId", userId);
                confirmation.put("success", true);

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        confirmation
                );
            } else {
                // Send failure
                Map<String, Object> failure = new HashMap<>();
                failure.put("type", "UNDO_RESULT");
                failure.put("userId", userId);
                failure.put("success", false);

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        failure
                );
            }
        } else if ("REDO".equals(type)) {
            // Process the redo operation
            Operation resultOperation = document.processRedo(userId);

            if (resultOperation != null) {
                // Broadcast the operation to all clients
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        resultOperation
                );

                // Send confirmation
                Map<String, Object> confirmation = new HashMap<>();
                confirmation.put("type", "REDO_RESULT");
                confirmation.put("userId", userId);
                confirmation.put("success", true);

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        confirmation
                );
            } else {
                // Send failure
                Map<String, Object> failure = new HashMap<>();
                failure.put("type", "REDO_RESULT");
                failure.put("userId", userId);
                failure.put("success", false);

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        failure
                );
            }
        }

        // Update document timestamp
        document.updateTimestamp();
    }



    @MessageMapping("/document/{documentId}/cursor")
    public void handleCursorPosition(@DestinationVariable String documentId,
                                     @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        int line = (int) payload.get("line");
        int column = (int) payload.get("column");

        // Broadcast the cursor position to all connected clients
        messagingTemplate.convertAndSend("/topic/document/" + documentId + "/cursor", payload);
    }

    @MessageMapping("/document/{documentId}/join")
    public List<UserDTO> handleJoin(@DestinationVariable String documentId,
                                    @Payload String userId,
                                    SimpMessageHeaderAccessor headerAccessor) {
        // Get active users in the session
        List<UserDTO> activeUsers = sessionService.getActiveUsers(documentId);

        // Send the active users list directly to the joining user
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/document/" + documentId + "/users",
                activeUsers
        );

        // Also broadcast to all users (as before)
        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/users",
                activeUsers
        );

        return activeUsers;
    }

    @MessageMapping("/document/{documentId}/leave")
    public void handleLeave(@DestinationVariable String documentId,
                            @Payload String userId,
                            SimpMessageHeaderAccessor headerAccessor) {
        // Remove user from session
        sessionService.removeUserFromSession(documentId, userId);
    }

    @MessageMapping("/document/{documentId}/reconnect")
    public void handleReconnect(@DestinationVariable String documentId,
                                @Payload String userId,
                                SimpMessageHeaderAccessor headerAccessor) {
        // Reconnect user to session
        sessionService.reconnectUser(documentId, userId);
    }

    // In WebSocketController.java on the server - add a new endpoint
    @MessageMapping("/document/{documentId}/paste")
    public void handlePasteOperation(@DestinationVariable String documentId,
                                     @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String text = (String) payload.get("text");
        String initialParentId = (String) payload.get("parentId");

        System.out.println("Processing paste operation from user " + userId +
                " with " + text.length() + " characters");

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        // Insert all characters in the text as a batch
        String parentId = initialParentId;
        List<Operation> operations = new ArrayList<>();

        // Process characters one by one
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Insert the character
            CRDTCharacter character = document.getCrdtTree().insertCharacter(userId, c, parentId);

            // Create an operation for this insertion
            Operation operation = Operation.insert(userId, character.getId(), character, parentId);

            // Record the operation and add to list
            document.recordOperation(userId, operation);
            operations.add(operation);

            // Update parentId for next character
            parentId = character.getId();
        }

        // Update document timestamp
        document.updateTimestamp();

        // After all characters are inserted, send a content update to all clients
        Map<String, Object> contentUpdate = new HashMap<>();
        contentUpdate.put("documentId", documentId);
        contentUpdate.put("content", document.getContentAsString());
        contentUpdate.put("pasteUserId", userId); // Include who did the paste

        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/content",
                contentUpdate
        );

        // Also broadcast each individual operation for history tracking
        for (Operation operation : operations) {
            // Record for reconnecting users
            sessionService.recordOperation(documentId, operation);

            // Broadcast operation to all users
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/operation",
                    operation
            );
        }
    }

    // In WebSocketController.java on the server side
    @MessageMapping("/document/{documentId}/batchOperation")
    public void handleBatchOperation(@DestinationVariable String documentId,
                                     @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String type = (String) payload.get("type");

        if ("PASTE".equals(type)) {
            String text = (String) payload.get("text");
            String parentId = (String) payload.get("parentId");

            System.out.println("Processing batch paste operation from user " + userId +
                    " with " + text.length() + " characters");

            // Get the document
            Document document = documentService.getDocument(documentId);
            if (document == null) {
                System.err.println("Document not found: " + documentId);
                return;
            }

            try {
                // Process the paste as a single batch operation
                String startParentId = parentId;

                // Insert all characters in sequence
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);

                    // Insert character with proper parent ID
                    CRDTCharacter character = document.getCrdtTree().insertCharacter(userId, c, parentId);

                    // Create operation object for tracking
                    Operation operation = Operation.insert(userId, character.getId(), character, parentId);

                    // Update parent ID for next character
                    parentId = character.getId();

                    // Record operation but don't broadcast individually
                    document.recordOperation(userId, operation);
                }

                // Update document timestamp
                document.updateTimestamp();

                // Send a complete content update to all clients
                Map<String, Object> contentUpdate = new HashMap<>();
                contentUpdate.put("documentId", documentId);
                contentUpdate.put("content", document.getContentAsString());
                contentUpdate.put("batchOperation", true);
                contentUpdate.put("userId", userId);

                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/batchUpdate",
                        contentUpdate
                );

                // Acknowledge successful paste to the sender
                Map<String, Object> ackResponse = new HashMap<>();
                ackResponse.put("status", "success");
                ackResponse.put("operation", "PASTE");
                ackResponse.put("userId", userId);

                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/document/batchAck",
                        ackResponse
                );

            } catch (Exception e) {
                System.err.println("Error processing batch paste: " + e.getMessage());
                e.printStackTrace();

                // Send error response to sender
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("operation", "PASTE");
                errorResponse.put("userId", userId);
                errorResponse.put("message", e.getMessage());

                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/document/batchAck",
                        errorResponse
                );
            }
        }
    }


    // In WebSocketController.java on the server side
    @MessageMapping("/document/{documentId}/refresh")
    public void handleRefreshRequest(@DestinationVariable String documentId,
                                     @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");

        System.out.println("Processing refresh request from user " + userId +
                " for document " + documentId);

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        // Create refresh response with current document state
        Map<String, Object> refreshResponse = new HashMap<>();
        refreshResponse.put("documentId", documentId);
        refreshResponse.put("content", document.getContentAsString());
        refreshResponse.put("timestamp", System.currentTimeMillis());

        // Send to refresh topic
        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/refresh",
                refreshResponse
        );

        System.out.println("Sent refresh response for document " + documentId);
    }

    // In WebSocketController.java on the server side
    @MessageMapping("/document/{documentId}/serverPaste")
    public void handleServerPaste(@DestinationVariable String documentId,
                                  @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String text = (String) payload.get("text");
        String parentId = (String) payload.get("parentId");

        System.out.println("Processing server-side paste from user " + userId +
                " with " + text.length() + " characters");

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        try {
            // Process the entire paste operation on the server side
            String currentParentId = parentId;

            // Insert all characters
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                // Insert character with current parent
                CRDTCharacter character = document.getCrdtTree().insertCharacter(userId, c, currentParentId);

                // Create operation for tracking (but don't broadcast individually)
                Operation operation = Operation.insert(userId, character.getId(), character, currentParentId);

                // Record operation but no need to broadcast
                document.recordOperation(userId, operation);

                // Update parent for next character
                currentParentId = character.getId();

                // For very long texts, periodically update clients
                if (text.length() > 100 && i > 0 && i % 50 == 0) {
                    // Send a partial update
                    sendContentUpdate(documentId, document.getContentAsString());
                }
            }

            // Update document timestamp
            document.updateTimestamp();

            // Send final content update to all clients
            sendContentUpdate(documentId, document.getContentAsString());

            System.out.println("Server-side paste completed successfully");

        } catch (Exception e) {
            System.err.println("Error processing server-side paste: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to send content updates
    private void sendContentUpdate(String documentId, String content) {
        Map<String, Object> contentUpdate = new HashMap<>();
        contentUpdate.put("documentId", documentId);
        contentUpdate.put("content", content);
        contentUpdate.put("timestamp", System.currentTimeMillis());

        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/content",
                contentUpdate
        );
    }



    @MessageMapping("/document/{documentId}/reparent")
    public void handleReparentOperation(@DestinationVariable String documentId,
                                        @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String characterId = (String) payload.get("characterId");
        String newParentId = (String) payload.get("newParentId");

        System.out.println("Processing reparent operation: character=" + characterId +
                ", newParent=" + newParentId + " from user " + userId);

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        // Get the CRDT tree
        CRDTTree crdtTree = document.getCrdtTree();

        // Get the character
        CRDTCharacter character = crdtTree.getCharacter(characterId);
        if (character == null) {
            System.err.println("Character not found: " + characterId);
            return;
        }

        // Get the new parent
        CRDTCharacter newParent = crdtTree.getCharacter(newParentId);
        if (newParent == null && !newParentId.equals("ROOT")) {
            System.err.println("New parent not found: " + newParentId);
            return;
        }

        // Update the parent relationship in the tree
        crdtTree.reparentCharacter(characterId, newParentId);

        // Broadcast the reparent operation to all users
        Map<String, Object> reparentOperation = new HashMap<>();
        reparentOperation.put("type", "REPARENT");
        reparentOperation.put("userId", userId);
        reparentOperation.put("characterId", characterId);
        reparentOperation.put("newParentId", newParentId);

        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/operation",
                reparentOperation
        );
    }
}