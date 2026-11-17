package com.datn.notification.controller;

import com.datn.common.exception.BadRequestException;
import com.datn.notification.config.SecurityConfig;
import com.datn.notification.dto.NotificationPreferencesView;
import com.datn.notification.dto.NotificationPreferencesView.CategoryView;
import com.datn.notification.exception.GlobalExceptionHandler;
import com.datn.notification.service.NotificationPreferenceService;
import com.datn.notification.service.NotificationPreferenceService.CategoryChange;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationPreferenceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class NotificationPreferenceControllerTest {

    private static final String SUB = "44444444-4444-4444-4444-444444444444";
    private static final UUID ME = UUID.fromString(SUB);
    private static final UUID PROJECT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired MockMvc mvc;
    @MockitoBean NotificationPreferenceService service;
    @MockitoBean JwtDecoder jwtDecoder;

    private static NotificationPreferencesView view() {
        return new NotificationPreferencesView(
                List.of(new CategoryView("COMMENT", false, true, false, true, List.of("Được nhắc tên"))), List.of());
    }

    @Test
    void withoutToken_is401() throws Exception {
        mvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isUnauthorized());
        verify(service, never()).view(any());
    }

    @Test
    void get_returnsCallersPreferences() throws Exception {
        when(service.view(ME)).thenReturn(view());
        mvc.perform(get("/api/v1/notifications/preferences").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].category").value("COMMENT"))
                .andExpect(jsonPath("$.data.categories[0].inApp").value(false))
                .andExpect(jsonPath("$.data.categories[0].alwaysInApp[0]").value("Được nhắc tên"));
    }

    @Test
    void put_passesChanges() throws Exception {
        when(service.update(eq(ME), any())).thenReturn(view());
        mvc.perform(put("/api/v1/notifications/preferences").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categories\":[{\"category\":\"COMMENT\",\"inApp\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Đã lưu cài đặt thông báo"));
        verify(service).update(ME, List.of(new CategoryChange("COMMENT", false, null)));
    }

    @Test
    void put_invalidCategory_is400() throws Exception {
        when(service.update(eq(ME), any())).thenThrow(new BadRequestException("Nhóm thông báo không hợp lệ: X"));
        mvc.perform(put("/api/v1/notifications/preferences").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categories\":[{\"category\":\"X\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void muteAndUnmuteProject() throws Exception {
        when(service.mute(eq(ME), eq(PROJECT), eq("Mộc Coffee"), isNull())).thenReturn(view());
        when(service.unmute(ME, PROJECT)).thenReturn(view());

        mvc.perform(put("/api/v1/notifications/preferences/projects/" + PROJECT + "/mute").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"projectName\":\"Mộc Coffee\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/notifications/preferences/projects/" + PROJECT + "/mute").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk());

        verify(service).mute(ME, PROJECT, "Mộc Coffee", null);
        verify(service).unmute(ME, PROJECT);
    }
}
