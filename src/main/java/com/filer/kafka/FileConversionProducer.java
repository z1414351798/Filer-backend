package com.filer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filer.config.KafkaConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileConversionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendConversionTask(String jobId, String conversionType, Map<String, Object> payload) {
        try {
            payload.put("jobId", jobId);
            payload.put("conversionType", conversionType);
            String message = objectMapper.writeValueAsString(payload);
            kafkaTemplate.send(KafkaConfig.TOPIC_FILE_CONVERSION, jobId, message);
            log.info("Sent conversion task: jobId={} type={}", jobId, conversionType);
        } catch (Exception e) {
            log.error("Failed to send Kafka message for job {}: {}", jobId, e.getMessage());
            throw new RuntimeException("Failed to queue conversion job", e);
        }
    }
}
