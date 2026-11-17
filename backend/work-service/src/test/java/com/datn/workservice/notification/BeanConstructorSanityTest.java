package com.datn.workservice.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean có nhiều constructor phải đánh dấu @Autowired, nếu không Spring báo "No default constructor found". */
class BeanConstructorSanityTest {

    @Test
    void everyBeanHasAnUnambiguousConstructor() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        List<String> problems = new ArrayList<>();
        for (BeanDefinition def : scanner.findCandidateComponents("com.datn.workservice")) {
            Class<?> type = Class.forName(def.getBeanClassName());
            Constructor<?>[] ctors = type.getDeclaredConstructors();
            boolean ok = ctors.length == 1
                    || Arrays.stream(ctors).filter(c -> c.isAnnotationPresent(Autowired.class)).count() == 1
                    || Arrays.stream(ctors).anyMatch(c -> c.getParameterCount() == 0);
            if (!ok) problems.add(type.getName());
        }
        assertThat(problems).isEmpty();
    }
}
