package com.datn.workservice.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter mã hoá / giải mã token GitHub trong {@code user_github_tokens}. Hibernate lấy converter qua
 * SpringBeanContainer (Spring Boot cấu hình sẵn) nên được inject {@link GitHubTokenCipher}.
 */
@Converter
public class GitHubTokenConverter implements AttributeConverter<String, String> {

    private final GitHubTokenCipher cipher;

    public GitHubTokenConverter(GitHubTokenCipher cipher) {
        this.cipher = cipher;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
