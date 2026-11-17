package com.datn.workservice.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubTokenCipherTest {

    private final GitHubTokenCipher cipher = new GitHubTokenCipher("unit-test-key", "");

    @Test
    void encryptsAndDecryptsRoundTrip() {
        String stored = cipher.encrypt("gho_abc123");
        assertThat(stored).startsWith(GitHubTokenCipher.PREFIX).doesNotContain("gho_abc123");
        assertThat(cipher.decrypt(stored)).isEqualTo("gho_abc123");
    }

    @Test
    void sameTokenEncryptsDifferentlyEachTime() {
        assertThat(cipher.encrypt("gho_abc123")).isNotEqualTo(cipher.encrypt("gho_abc123"));
    }

    @Test
    void legacyPlaintextIsReadAsIs() {
        assertThat(cipher.decrypt("gho_legacy")).isEqualTo("gho_legacy");
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void wrongKeyReturnsNullInsteadOfThrowing() {
        String stored = cipher.encrypt("gho_abc123");
        assertThat(new GitHubTokenCipher("another-key", "").decrypt(stored)).isNull();
    }

    @Test
    void fallsBackToClientSecretAsKeyMaterial() {
        GitHubTokenCipher fromSecret = new GitHubTokenCipher("", "client-secret");
        assertThat(fromSecret.decrypt(fromSecret.encrypt("t"))).isEqualTo("t");
    }

    @Test
    void withoutAnyKeyStoresPlaintext() {
        GitHubTokenCipher none = new GitHubTokenCipher("", "");
        assertThat(none.encrypt("t")).isEqualTo("t");
    }
}
