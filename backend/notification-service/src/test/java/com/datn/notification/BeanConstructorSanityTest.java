package com.datn.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring chỉ tự chọn được constructor khi bean có đúng một constructor, hoặc một constructor gắn @Autowired.
 * Test này bắt lỗi "No default constructor found" mà unit test dùng mock không phát hiện.
 */
class BeanConstructorSanityTest {

    @Test
    void everyBeanHasAnUnambiguousConstructor() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> problems = new ArrayList<>();
        for (BeanDefinition def : scanner.findCandidateComponents("com.datn.notification")) {
            Class<?> type = Class.forName(def.getBeanClassName());
            Constructor<?>[] ctors = type.getDeclaredConstructors();
            boolean ok = ctors.length == 1
                    || Arrays.stream(ctors).filter(c -> c.isAnnotationPresent(Autowired.class)).count() == 1
                    || Arrays.stream(ctors).anyMatch(c -> c.getParameterCount() == 0);
            if (!ok) problems.add(type.getName());
        }
        assertThat(problems).as("bean có nhiều constructor nhưng không đánh dấu @Autowired").isEmpty();
    }
}
