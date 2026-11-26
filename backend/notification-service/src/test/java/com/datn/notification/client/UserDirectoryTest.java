package com.datn.notification.client;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserDirectoryTest {

    private static final String SUB = "14f45571-7ec4-4270-ab92-47d84cb3e774";

    @Test
    void found_isCached_andDisplayNameUsesFullName() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://identity");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://identity/internal/users/" + SUB))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"id":"x","keycloakId":"%s","username":"binh","email":"binh@example.com",
                         "firstName":"Bình","lastName":"Trần","roles":["USER"]}}""".formatted(SUB), MediaType.APPLICATION_JSON));
        UserDirectory dir = new UserDirectory(builder.build());

        assertThat(dir.displayName(SUB)).isEqualTo("Bình Trần");
        assertThat(dir.find(SUB)).get().extracting(UserDirectory.UserInfo::email).isEqualTo("binh@example.com");
        server.verify(); // gọi đúng 1 lần nhờ cache
    }

    @Test
    void notFound_fallsBackToGenericName() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://identity");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://identity/internal/users/" + SUB))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        UserDirectory dir = new UserDirectory(builder.build());

        assertThat(dir.displayName(SUB)).isEqualTo("Một thành viên");
        assertThat(dir.find(SUB)).isEmpty(); // không gọi lại trong 1 phút
        server.verify();
    }

    @Test
    void usernameUsedWhenNoName() {
        assertThat(new UserDirectory.UserInfo("1", SUB, "chi", "c@x.vn", null, " ").displayName()).isEqualTo("chi");
        assertThat(new UserDirectory.UserInfo("1", SUB, null, "c@x.vn", null, null).displayName()).isEqualTo("c@x.vn");
    }
}
