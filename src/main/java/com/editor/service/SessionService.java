package com.editor.service;

import com.editor.dto.UserDTO;

import com.editor.model.Operation;
import com.editor.model.Session;
import com.editor.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class SessionService {
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    // Maximum time for reconnection (in minutes)
    private static final int MAX_RECONNECT_MINUTES = 5;

    @Autowired
    public SessionService(DocumentService documentService, SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }

    public Session getOrCreateSession(String documentId) {
        return sessions.computeIfAbsent(documentId, Session::new);
    }

    public void addUserToSession(String documentId, User user) {
        Session session = getOrCreateSession(documentId);
        session.addUser(user);

        // Notify all users about the new user
        broadcastUserPresence(documentId);
    }

    public void removeUserFromSession(String documentId, String userId) {
        Session session = sessions.get(documentId);
        if (session != null) {
            session.removeUser(userId);

            // Clean up empty sessions
            if (session.isEmpty()) {
                sessions.remove(documentId);
            } else {
                // Notify users about the user leaving
                broadcastUserPresence(documentId);
            }
        }
    }


    public void reconnectUser(String documentId, String userId) {
        Session session = sessions.get(documentId);
        if (session != null) {
            session.reconnectUser(userId);

            // Send missed operations to the reconnected user
            List<Operation> missedOps = session.getMissedOperations(userId);
            if (!missedOps.isEmpty()) {
                // Send missed operations to the user
                messagingTemplate.convertAndSendToUser(
                        userId,
                        "/queue/missed-operations",
                        missedOps
                );
            }

            broadcastUserPresence(documentId);
        }
    }

    public void recordOperation(String documentId, Operation operation) {
        Session session = sessions.get(documentId);
        if (session != null) {
            session.addMissedOperation(operation);
        }
    }

    public List<UserDTO> getActiveUsers(String documentId) {
        Session session = sessions.get(documentId);
        if (session == null) {
            return new ArrayList<>();
        }

        return session.getActiveUsers().values().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private UserDTO convertToDTO(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setActive(user.isActive());
        dto.setRole(user.getRole());

        UserDTO.CursorDTO cursorDTO = new UserDTO.CursorDTO();
        cursorDTO.setLine(user.getCursorPosition().getLine());
        cursorDTO.setColumn(user.getCursorPosition().getColumn());
        dto.setCursor(cursorDTO);

        return dto;
    }



    // Continue from previous implementation

    public void broadcastUserPresence(String documentId) {
        List<UserDTO> activeUsers = getActiveUsers(documentId);
        messagingTemplate.convertAndSend(
                "/topic/document/" + documentId + "/users",
                activeUsers
        );
    }



    @Scheduled(fixedRate = 60000) // Run every minute
    public void cleanupReconnectingUsers() {
        LocalDateTime now = LocalDateTime.now();

        for (Session session : sessions.values()) {
            List<String> usersToRemove = new ArrayList<>();

            for (Map.Entry<String, User> entry : session.getReconnectingUsers().entrySet()) {
                User user = entry.getValue();
                if (ChronoUnit.MINUTES.between(user.getLastActivity(), now) > MAX_RECONNECT_MINUTES) {
                    usersToRemove.add(entry.getKey());
                }
            }

            // Remove users whose reconnection period has expired
            for (String userId : usersToRemove) {
                session.getReconnectingUsers().remove(userId);
                session.getMissedOperations().remove(userId);
            }

            // If any users were removed, broadcast updated presence
            if (!usersToRemove.isEmpty()) {
                broadcastUserPresence(session.getDocumentId());
            }

            // Clean up empty sessions
            if (session.isEmpty()) {
                sessions.remove(session.getDocumentId());
            }
        }
    }
}
