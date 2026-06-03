package com.filer.mapper;

import com.filer.model.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface FileMapper {
    void insert(FileRecord record);
    FileRecord findByFileId(String fileId);
    FileRecord findById(Long id);
    List<FileRecord> findAll();
    List<FileRecord> findByUserId(Long userId);
}
