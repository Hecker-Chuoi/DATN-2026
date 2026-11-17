package com.datn.notification.controller;

import com.datn.common.exception.ResourceNotFoundException;
import com.datn.notification.config.SecurityConfig;
import com.datn.notification.dto.NotificationPage;
import com.datn.notification.exception.GlobalExceptionHandler;
import com.datn.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    private static final String SUB = "44444444-4444-4444-4444-444444444444";
    private static final UUID ME = UUID.fromString(SUB);

    @Autowired MockMvc mvc;
    @MockitoBean NotificationService service;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        verify(service, never()).list(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void list_usesKeycloakSubOfCaller() throws Exception {
        when(service.list(eq(ME), eq(NotificationService.Filter.UNREAD), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(new NotificationPage(List.of(), null, 3, 1));
        mvc.perform(get("/api/v1/notifications").param("filter", "unread").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.actionCount").value(1));
    }

    @Test
    void invalidFilter_is400() throws Exception {
        mvc.perform(get("/api/v1/notifications").param("filter", "xyz").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void markUnread_passesFalse() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/v1/notifications/" + id).with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"read\": false}"))
                .andExpect(status().isOk());
        verify(service).setRead(ME, id, false);
    }

    @Test
    void markRead_withoutBody_defaultsToRead() throws Exception {
        UUID id = UUID.randomUUID();
        mvc.perform(patch("/api/v1/notifications/" + id).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).setRead(ME, id, true);
    }

    @Test
    void notFound_is404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.setRead(ME, id, true)).thenThrow(new ResourceNotFoundException("Không tìm thấy thông báo"));
        mvc.perform(patch("/api/v1/notifications/" + id).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void readAll_and_deleteRead() throws Exception {
        when(service.counts(ME)).thenReturn(new NotificationService.Counts(0, 0));
        mvc.perform(post("/api/v1/notifications/read-all").param("filter", "action").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).readAll(ME, NotificationService.Filter.ACTION, null, null);

        mvc.perform(delete("/api/v1/notifications").param("filter", "read").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());
        verify(service).deleteAllRead(ME);

        mvc.perform(delete("/api/v1/notifications").param("filter", "all").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void internalEmailEndpoint_staysOpenForOtherServices() throws Exception {
        // Không có controller email trong lát cắt này: chỉ cần security không chặn (404 thay vì 401).
        mvc.perform(post("/internal/notifications/email").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
