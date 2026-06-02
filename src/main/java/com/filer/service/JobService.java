package com.filer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filer.dto.JobRequest;
import com.filer.dto.JobResponse;
import com.filer.kafka.FileConversionProducer;
import com.filer.mapper.JobMapper;
import com.filer.model.ConversionJob;
import com.filer.model.enums.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private static final String REDIS_JOB_PREFIX = "filer:job:";
    private static final Duration JOB_TTL = Duration.ofDays(1);

    private final JobMapper jobMapper;
    private final FileConversionProducer producer;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public JobResponse createJob(JobRequest request) {
        String jobId = UUID.randomUUID().toString();

        ConversionJob job = new ConversionJob();
        job.setJobId(jobId);
        job.setSourceFileId(request.getFileId());
        job.setConversionType(request.getConversionType());
        job.setStatus(JobStatus.PENDING.name());
        job.setProgress(0);
        try {
            job.setOptions(objectMapper.writeValueAsString(request.getOptions()));
        } catch (Exception e) {
            job.setOptions("{}");
        }
        jobMapper.insert(job);

        // Cache in Redis
        cacheJob(job);

        // Build payload for Kafka
        Map<String, Object> payload = new HashMap<>();
        payload.put("fileId", request.getFileId());
        if (request.getWidth() != null) payload.put("width", request.getWidth());
        if (request.getHeight() != null) payload.put("height", request.getHeight());
        if (request.getQuality() != null) payload.put("quality", request.getQuality());
        if (request.getAngle() != null) payload.put("angle", request.getAngle());
        if (request.getWatermarkText() != null) payload.put("watermarkText", request.getWatermarkText());
        if (request.getSplitPage() != null) payload.put("splitPage", request.getSplitPage());
        if (request.getPassword() != null) payload.put("password", request.getPassword());
        if (request.getFileIds() != null) payload.put("fileIds", request.getFileIds());
        if (request.getLanguage() != null) payload.put("language", request.getLanguage());
        if (request.getOptions() != null) payload.putAll(request.getOptions());

        producer.sendConversionTask(jobId, request.getConversionType(), payload);

        return toResponse(job);
    }

    public JobResponse getJob(String jobId) {
        // Try Redis first
        Object cached = redisTemplate.opsForValue().get(REDIS_JOB_PREFIX + jobId);
        if (cached != null) {
            try {
                ConversionJob job = objectMapper.convertValue(cached, ConversionJob.class);
                return toResponse(job);
            } catch (Exception ignored) {}
        }
        ConversionJob job = jobMapper.findByJobId(jobId);
        if (job == null) throw new IllegalArgumentException("Job not found: " + jobId);
        return toResponse(job);
    }

    public List<JobResponse> listAll() {
        return jobMapper.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public void updateProgress(String jobId, int progress) {
        jobMapper.updateStatus(jobId, JobStatus.PROCESSING.name(), progress, null, null);
        ConversionJob job = jobMapper.findByJobId(jobId);
        if (job != null) cacheJob(job);
    }

    public void markCompleted(String jobId, String outputFileId) {
        jobMapper.markCompleted(jobId, outputFileId);
        ConversionJob job = jobMapper.findByJobId(jobId);
        if (job != null) cacheJob(job);
    }

    public void markFailed(String jobId, String errorMessage) {
        jobMapper.markFailed(jobId, errorMessage);
        ConversionJob job = jobMapper.findByJobId(jobId);
        if (job != null) cacheJob(job);
    }

    private void cacheJob(ConversionJob job) {
        try {
            redisTemplate.opsForValue().set(
                    REDIS_JOB_PREFIX + job.getJobId(), job, JOB_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache job in Redis: {}", e.getMessage());
        }
    }

    private JobResponse toResponse(ConversionJob job) {
        return JobResponse.builder()
                .jobId(job.getJobId())
                .sourceFileId(job.getSourceFileId())
                .outputFileId(job.getOutputFileId())
                .conversionType(job.getConversionType())
                .status(job.getStatus())
                .progress(job.getProgress())
                .errorMessage(job.getErrorMessage())
                .createdAt(job.getCreatedAt())
                .completedAt(job.getCompletedAt())
                .downloadUrl(job.getOutputFileId() != null
                        ? "/api/files/" + job.getOutputFileId() + "/download" : null)
                .build();
    }
}
