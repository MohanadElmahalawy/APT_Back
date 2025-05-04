package com.editor.Controller;

import com.editor.dto.*;
import com.editor.model.Document;
import com.editor.model.Operation;
import com.editor.model.User;
import com.editor.model.crdt.CRDTCharacter;
import com.editor.service.DocumentService;
import com.editor.service.SessionService;
import com.editor.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class DocumentController {
    private final DocumentService documentService;
    private final UserService userService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public DocumentController(DocumentService documentService,
                              UserService userService,
                              SessionService sessionService,
                              SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.userService = userService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * WebSocket endpoint to create a document.
     *
     * @param //request Document creation request DTO
     */
     @MessageMapping("/documents/create")
     public void createDocumentWs(@Payload Map<String, Object> payload) {
        try {
            String userName = (String) payload.get("userName");
            String name = (String) payload.get("name");
            String requestId = (String) payload.get("requestId");  // Make sure to extract the requestId

            System.out.println("Creating document via WebSocket: " + name + " for user: " + userName);

            // Create user first
            User user = userService.createUser(userName, User.UserRole.EDITOR);

            // Create document with user as owner
            Document document = documentService.createDocument(name, user.getId());

            // Add user to session
            sessionService.addUserToSession(document.getId(), user);

            // Convert to DTO
            DocumentDTO documentDTO = documentService.convertToDTO(document, user.getId());

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("document", documentDTO);
            response.put("requestId", requestId);  // Include the requestId in the response

            System.out.println("Preparing to send response for userId: " + user.getId());

            // Try both methods of sending the response

            // Method 1: User-specific queue (original method)
            messagingTemplate.convertAndSendToUser(
                    user.getId(),
                    "/queue/document",
                    response
            );
            System.out.println("Response sent to user queue: /user/" + user.getId() + "/queue/document");

            // Method 2: General topic with document ID
            messagingTemplate.convertAndSend(
                    "/topic/document/response/" + requestId,
                    response
            );
            System.out.println("Response also sent to topic: /topic/document/response/" + requestId);

        } catch (Exception e) {
            System.err.println("Error creating document: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * WebSocket endpoint to import a document with content
     * //@param //request Document import request DTO
     */
    @MessageMapping("/documents/import")
    public void importDocumentWs(@Payload Map<String, Object> payload) {
        try {
            String userName = (String) payload.get("userName");
            String name = (String) payload.get("name");
            String content = (String) payload.get("content");
            String requestId = (String) payload.get("requestId");

            // Create user first
            User user = userService.createUser(userName, User.UserRole.EDITOR);

            // Import document with user as owner
            Document document = documentService.importDocument(name, user.getId(), content);

            // Add user to session
            sessionService.addUserToSession(document.getId(), user);

            // Convert to DTO
            DocumentDTO documentDTO = documentService.convertToDTO(document, user.getId());

            // IMPORTANT: Broadcast imported content to all users
            Map<String, Object> contentUpdate = new HashMap<>();
            contentUpdate.put("documentId", document.getId());
            contentUpdate.put("content", content);

            messagingTemplate.convertAndSend(
                    "/topic/document/" + document.getId() + "/content",
                    contentUpdate
            );

            // Send response to original user
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("document", documentDTO);
            response.put("requestId", requestId);

            messagingTemplate.convertAndSendToUser(
                    user.getId(),
                    "/queue/document",
                    response
            );

            messagingTemplate.convertAndSend(
                    "/topic/document/response/" + requestId,
                    response
            );
        } catch (Exception e) {
            System.err.println("Error importing document: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @MessageMapping("/documents/{documentId}/importContent")
    public void importContentToDocument(@DestinationVariable String documentId,
                                        @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String content = (String) payload.get("content");
        String parentId = (String) payload.get("parentId");

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            return;
        }

        // Use parentId for insertion point (default to ROOT if not provided)
        String currentParentId = parentId != null ? parentId : "ROOT";

        System.out.println("Importing " + content.length() + " characters starting from parent: " + currentParentId);

        // Insert characters at the specified position
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);

            // Add a small delay between characters for unique timestamps
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Insert character - the CRDT system handles mid-document insertion automatically
            CRDTCharacter character = document.getCrdtTree().insertCharacter(
                    userId, c, currentParentId);

            // Create operation
            Operation operation = Operation.insert(
                    userId, character.getId(), character, currentParentId);

            // Record operation
            document.recordOperation(userId, operation);

            // Broadcast operation to all clients
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/operation",
                    operation
            );

            // Update parent for next character
            currentParentId = character.getId();
        }

        // Update document timestamp
        document.updateTimestamp();
    }

    @MessageMapping("/documents/join")
    public void joinSession(@Payload Map<String, Object> payload) {
        try {
            String shareCode = (String) payload.get("shareCode");
            String userName = (String) payload.get("userName");
            String requestId = (String) payload.get("requestId");  // Extract the requestId

            System.out.println("Joining session via WebSocket with code: " + shareCode + " for user: " + userName);

            // Find document by share code
            Document document = documentService.getDocumentByShareCode(shareCode);
            if (document == null) {
                // Send error response
                messagingTemplate.convertAndSendToUser(
                        userName,
                        "/queue/errors",
                        "Invalid share code"
                );

                // Also send to topic
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid share code");
                errorResponse.put("requestId", requestId);
                messagingTemplate.convertAndSend(
                        "/topic/document/error/" + requestId,
                        errorResponse
                );
                return;
            }

            // Determine user role based on share code
            User.UserRole role = documentService.getUserRoleForDocument(shareCode);
            if (role == null) {
                // Send error response
                messagingTemplate.convertAndSendToUser(
                        userName,
                        "/queue/errors",
                        "Invalid share code"
                );

                // Also send to topic
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Invalid share code");
                errorResponse.put("requestId", requestId);
                messagingTemplate.convertAndSend(
                        "/topic/document/error/" + requestId,
                        errorResponse
                );
                return;
            }

            // Create user with appropriate role
            User user = userService.createUser(userName, role);

            // Add user to session
            sessionService.addUserToSession(document.getId(), user);

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("userId", user.getId());
            response.put("document", documentService.convertToDTO(document, user.getId()));
            response.put("role", role);
            response.put("requestId", requestId);  // Include the requestId in the response

            System.out.println("Preparing to send join session response for userId: " + user.getId());

            // Try both methods of sending the response

            // Method 1: User-specific queue (original method)
            messagingTemplate.convertAndSendToUser(
                    user.getId(),
                    "/queue/session",
                    response
            );
            System.out.println("Join response sent to user queue: /user/" + user.getId() + "/queue/session");

            // Method 2: General topic with requestId
            messagingTemplate.convertAndSend(
                    "/topic/document/response/" + requestId,
                    response
            );
            System.out.println("Join response also sent to topic: /topic/document/response/" + requestId);
        } catch (Exception e) {
            System.err.println("Error joining session: " + e.getMessage());
            e.printStackTrace();
        }
    }
}