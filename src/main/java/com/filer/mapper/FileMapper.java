package com.filer.mapper;

import com.filer.model.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Optional;

@Mapper
public interface FileMapper {
    void insert(FileRecord record);
    Optional<FileRecord> findByFileId(String fileId);
    Optional<FileRecord> findById(Long id);
    List<FileRecord> findAll();
    List<FileRecord> findByUserId(Long userId);
}
