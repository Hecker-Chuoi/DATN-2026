package com.datn.workservice.client;

import com.datn.workservice.client.dto.UserResponse;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IdentityUsersTest {

    @Test
    void indexesByBothIdentityIdAndKeycloakSub() {
        UUID identityId = UUID.randomUUID();
        UUID sub = UUID.randomUUID();
        UserResponse u = UserResponse.builder().id(identityId).keycloakId(sub.toString()).username("an").build();

        Map<UUID, UserResponse> map = IdentityUsers.indexByAnyId(List.of(u));

        assertThat(map.get(sub)).isSameAs(u);
        assertThat(map.get(identityId)).isSameAs(u);
    }

    @Test
    void toleratesMissingOrBrokenIdsAndNullInput() {
        UUID identityId = UUID.randomUUID();
        UserResponse brokenSub = UserResponse.builder().id(identityId).keycloakId("khong-phai-uuid").build();
        UUID sub = UUID.randomUUID();
        UserResponse noIdentityId = UserResponse.builder().keycloakId(sub.toString()).build();

        Map<UUID, UserResponse> map = IdentityUsers.indexByAnyId(Arrays.asList(brokenSub, null, noIdentityId));

        assertThat(map).containsOnlyKeys(identityId, sub);
        assertThat(IdentityUsers.indexByAnyId(null)).isEmpty();
    }
}
