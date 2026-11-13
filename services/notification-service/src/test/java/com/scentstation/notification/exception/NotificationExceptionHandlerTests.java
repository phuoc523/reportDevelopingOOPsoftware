package com.scentstation.notification.exception;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationExceptionHandlerTests.TestController.class)
@Import({NotificationExceptionHandler.class, NotificationExceptionHandlerTests.TestController.class})
class NotificationExceptionHandlerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void mapsDomainNotFoundToSafeNotFoundResponse() throws Exception {
        mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Notification not found"))
            .andExpect(jsonPath("$.path").value("/test/not-found"));
    }

    @Test
    void mapsInvalidRequestToFieldErrorsWithoutRejectedValues() throws Exception {
        String invalidRequest = """
            {
              "tenantId": "",
              "title": "",
              "content": "",
              "notificationType": "",
              "priority": "",
              "source": "",
              "sourceEventId": null,
              "recipientIds": []
            }
            """;

        mockMvc.perform(post("/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").exists())
            .andExpect(jsonPath("$.validationErrors.recipientIds").exists())
            .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @RestController
    public static class TestController {

        @GetMapping("/test/not-found")
        Map<String, String> notFound() {
            throw new NotificationNotFoundException();
        }

        @PostMapping("/test/validate")
        Map<String, String> validate(@Valid @RequestBody CreateNotificationCommand command) {
            return Map.of("status", "accepted");
        }
    }
}
