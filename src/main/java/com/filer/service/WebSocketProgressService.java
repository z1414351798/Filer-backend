package com.filer.service;

import com.filer.dto.JobResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WebSocketProgressService {

    private final SimpMessagingTemplate messagingTemplate;

    public void sendProgress(String jobId, JobResponse response) {
        messagingTemplate.convertAndSend("/topic/job/" + jobId, response);
    }
}
