package com.filer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String TOPIC_FILE_CONVERSION = "file-conversion";
    public static final String TOPIC_CONVERSION_RESULT = "conversion-result";

    @Bean
    public NewTopic fileConversionTopic() {
        return TopicBuilder.name(TOPIC_FILE_CONVERSION)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic conversionResultTopic() {
        return TopicBuilder.name(TOPIC_CONVERSION_RESULT)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
