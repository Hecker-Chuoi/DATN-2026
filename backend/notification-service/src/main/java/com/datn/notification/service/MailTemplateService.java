package com.datn.notification.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Render template HTML trong src/main/resources/templates/{name}.html bằng cách thay {{key}}.
 * Không dùng Thymeleaf: 2 template và vài placeholder không đáng thêm dependency.
 * Giá trị được escape HTML để tên người dùng không thể chèn thẻ vào mail.
 */
@Service
public class MailTemplateService {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String render(String template, Map<String, String> vars) {
        String html = cache.computeIfAbsent(template, this::load);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            html = html.replace("{{" + e.getKey() + "}}", escape(e.getValue()));
        }
        return html;
    }

    private String load(String name) {
        if (!name.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("Tên template không hợp lệ: " + name);
        }
        try {
            return new ClassPathResource("templates/" + name + ".html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException("Không tìm thấy template: " + name);
        }
    }

    private static String escape(String v) {
        if (v == null) return "";
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
