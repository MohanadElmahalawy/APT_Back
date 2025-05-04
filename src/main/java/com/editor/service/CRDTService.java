package com.editor.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;


@Service
public class CRDTService {
    private final DocumentService documentService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public CRDTService(DocumentService documentService,
                       SessionService sessionService,
                       SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }





}