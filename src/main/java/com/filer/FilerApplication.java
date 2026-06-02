package com.filer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.filer.mapper")
@EnableAsync
public class FilerApplication {
    public static void main(String[] args) {
        SpringApplication.run(FilerApplication.class, args);
    }
}
