package com.filer.mapper;

import com.filer.model.FileRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FileMapper {
    void insert(FileRecord record);
    FileRecord findByFileId(@Param("fileId") String fileId);
    List<FileRecord> findAll();
    void deleteByFileId(@Param("fileId") String fileId);
}
