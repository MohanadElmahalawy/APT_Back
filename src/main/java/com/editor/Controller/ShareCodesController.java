package com.editor.Controller;

import com.editor.dto.UserDTO;
import com.editor.model.Document;
import com.editor.model.User;
import com.editor.service.DocumentService;
import com.editor.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ShareCodesController {

    private final DocumentService documentService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public ShareCodesController(DocumentService documentService,
                                SessionService sessionService,
                                SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/documents/{documentId}/share")
    public void getShareCodes(@DestinationVariable String documentId, @Payload Map<String, Object> payload) {
        String userId = (String) payload.get("userId");
        String requestId = (String) payload.get("requestId");

        System.out.println("Received request for share codes. DocumentId: " + documentId +
                ", UserId: " + userId);

        // Get the document
        Document document = documentService.getDocument(documentId);
        if (document == null) {
            System.err.println("Document not found: " + documentId);
            sendErrorResponse(userId, requestId, "Document not found");
            return;
        }

        // Check if user is an editor (or owner)
        // Owner is always an editor
        boolean isEditor = document.getOwnerId().equals(userId);

        if (!isEditor) {
            // Check if the user is an editor by looking up in active sessions
            List<UserDTO> activeUsers = sessionService.getActiveUsers(documentId);

            for (UserDTO userDTO : activeUsers) {
                if (userDTO.getId().equals(userId) &&
                        userDTO.getRole() == User.UserRole.EDITOR) {
                    isEditor = true;
                    break;
                }
            }
        }

        if (!isEditor) {
            System.err.println("User " + userId + " is not an editor of document " + documentId);
            sendErrorResponse(userId, requestId, "Only document editors can get share codes");
            return;
        }

        // Create response with share codes
        Map<String, String> codes = new HashMap<>();
        codes.put("editorCode", document.getEditorCode());
        codes.put("viewerCode", document.getViewerCode());

        System.out.println("Sending share codes for document " + documentId +
                ": Editor=" + document.getEditorCode() +
                ", Viewer=" + document.getViewerCode());

        // Send to user-specific queue
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/share-codes",
                codes
        );

        // Also send to topic if requestId is provided
        if (requestId != null) {
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/share/" + requestId,
                    codes
            );
        }
    }

    private void sendErrorResponse(String userId, String requestId, String errorMessage) {
        Map<String, String> error = new HashMap<>();
        error.put("error", errorMessage);

        // Send to user-specific queue
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/errors",
                error
        );

        // Also send to topic if requestId is provided
        if (requestId != null) {
            messagingTemplate.convertAndSend(
                    "/topic/document/error/" + requestId,
                    error
            );
        }
    }
}