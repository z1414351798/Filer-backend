package com.filer.service;

import com.filer.mapper.FileMapper;
import com.filer.mapper.ShareLinkMapper;
import com.filer.model.FileRecord;
import com.filer.model.ShareLink;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareLinkService {

    private final ShareLinkMapper shareLinkMapper;
    private final FileMapper fileMapper;

    @Value("${filer.share-link-ttl-hours:24}")
    private int ttlHours;

    public String createShareLink(String fileId) {
        FileRecord file = fileMapper.findByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        ShareLink link = new ShareLink();
        link.setToken(UUID.randomUUID().toString());
        link.setFileId(file.getId());
        link.setExpiresAt(LocalDateTime.now().plusHours(ttlHours));
        shareLinkMapper.insert(link);
        return link.getToken();
    }

    public FileRecord resolveShareLink(String token) {
        ShareLink link = shareLinkMapper.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired share link"));
        if (link.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Share link expired");
        return fileMapper.findById(link.getFileId())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
    }

    @Scheduled(fixedDelay = 3_600_000)
    public void cleanupExpired() {
        shareLinkMapper.deleteExpired();
    }
}
