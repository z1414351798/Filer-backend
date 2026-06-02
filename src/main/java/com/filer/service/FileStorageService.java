package com.filer.service;

import com.filer.config.AppConfig;
import com.filer.dto.UploadResponse;
import com.filer.mapper.FileMapper;
import com.filer.model.FileRecord;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileMapper fileMapper;
    private final AppConfig appConfig;

    public UploadResponse store(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String originalName = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalName).toLowerCase();
        String storedName = fileId + (extension.isEmpty() ? "" : "." + extension);

        Path destPath = Paths.get(appConfig.getUploadDir()).resolve(storedName);
        Files.copy(file.getInputStream(), destPath, StandardCopyOption.REPLACE_EXISTING);

        FileRecord record = new FileRecord();
        record.setFileId(fileId);
        record.setOriginalName(originalName);
        record.setStoredName(storedName);
        record.setFilePath(destPath.toString());
        record.setFileSize(file.getSize());
        record.setMimeType(file.getContentType());
        record.setExtension(extension);
        fileMapper.insert(record);

        return UploadResponse.builder()
                .fileId(fileId)
                .originalName(originalName)
                .fileSize(file.getSize())
                .mimeType(file.getContentType())
                .extension(extension)
                .build();
    }

    public FileRecord getRecord(String fileId) {
        return fileMapper.findByFileId(fileId);
    }

    public Path getFilePath(String fileId) {
        FileRecord record = fileMapper.findByFileId(fileId);
        if (record == null) throw new IllegalArgumentException("File not found: " + fileId);
        return Paths.get(record.getFilePath());
    }

    public Resource loadAsResource(String fileId) throws MalformedURLException {
        Path path = getFilePath(fileId);
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) throw new IllegalArgumentException("File not found: " + fileId);
        return resource;
    }

    public FileRecord saveOutputFile(Path outputPath, String originalName, String mimeType) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String extension = FilenameUtils.getExtension(outputPath.getFileName().toString()).toLowerCase();

        // Move to output dir if not already there
        Path destPath = Paths.get(appConfig.getOutputDir()).resolve(outputPath.getFileName());
        if (!outputPath.equals(destPath)) {
            Files.move(outputPath, destPath, StandardCopyOption.REPLACE_EXISTING);
        }

        FileRecord record = new FileRecord();
        record.setFileId(fileId);
        record.setOriginalName(originalName);
        record.setStoredName(destPath.getFileName().toString());
        record.setFilePath(destPath.toString());
        record.setFileSize(Files.size(destPath));
        record.setMimeType(mimeType);
        record.setExtension(extension);
        fileMapper.insert(record);

        return record;
    }

    public List<FileRecord> listAll() {
        return fileMapper.findAll();
    }
}
