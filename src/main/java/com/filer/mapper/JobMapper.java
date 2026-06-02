package com.filer.mapper;

import com.filer.model.ConversionJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface JobMapper {
    void insert(ConversionJob job);
    ConversionJob findByJobId(@Param("jobId") String jobId);
    List<ConversionJob> findBySourceFileId(@Param("sourceFileId") String sourceFileId);
    List<ConversionJob> findAll();
    void updateStatus(@Param("jobId") String jobId,
                      @Param("status") String status,
                      @Param("progress") int progress,
                      @Param("errorMessage") String errorMessage,
                      @Param("outputFileId") String outputFileId);
    void markCompleted(@Param("jobId") String jobId,
                       @Param("outputFileId") String outputFileId);
    void markFailed(@Param("jobId") String jobId,
                    @Param("errorMessage") String errorMessage);
}
