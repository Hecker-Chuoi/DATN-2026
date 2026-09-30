package com.datn.workservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả kiểm tra toàn vẹn của một dự án mẫu (hoặc dự án vừa sinh ra từ mẫu).
 * ERROR chặn việc dùng mẫu; WARNING chỉ hiển thị để người dùng cân nhắc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateIntegrityReport {

    @Builder.Default
    private List<Issue> errors = new ArrayList<>();

    @Builder.Default
    private List<Issue> warnings = new ArrayList<>();

    public boolean isValid() {
        return errors.isEmpty();
    }

    public void error(String code, String message) {
        errors.add(new Issue(code, message));
    }

    public void warning(String code, String message) {
        warnings.add(new Issue(code, message));
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Issue {
        private String code;
        private String message;
    }
}
