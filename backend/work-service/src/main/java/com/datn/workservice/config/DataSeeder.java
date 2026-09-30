package com.datn.workservice.config;

import com.datn.workservice.service.template.TemplateDataSeeder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final TemplateDataSeeder templateDataSeeder;

    @Override
    public void run(String... args) {
        log.info("Work Service DataSeeder initialized.");
        try {
            templateDataSeeder.seedDefaultTemplates();
        } catch (Exception e) {
            log.error("Lỗi khi tự động seed các dự án mẫu: {}", e.getMessage(), e);
        }
    }
}
