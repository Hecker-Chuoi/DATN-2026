package com.datn.workservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * work-service. {@code @EnableScheduling} cho OutboxRelay (gửi sự kiện lên Kafka mỗi giây)
 * và các job thông báo theo lịch (nhắc hạn, chu kỳ quá hạn, tổng hợp hằng ngày).
 */
@SpringBootApplication
@EnableFeignClients
@EnableAsync
@EnableScheduling
public class WorkServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkServiceApplication.class, args);
    }
}
