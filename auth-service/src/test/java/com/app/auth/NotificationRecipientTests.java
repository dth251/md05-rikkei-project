package com.app.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.app.auth.entity.User;
import com.app.auth.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "notification.recipient-token=public-fixture-notification-credential-123456")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class NotificationRecipientTests {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepository users;

    @Test
    void privateCredentialReturnsOnlyRecipientAndNoStore() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var user = users.saveAndFlush(new User("Fixture", email, "fixture-hash"));
        mvc.perform(get("/internal/notification-recipients/" + user.getId())
                        .header("X-Notification-Credential", "public-fixture-notification-credential-123456"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.fullName").doesNotExist());
    }

    @Test
    void missingOrWrongServiceCredentialCannotEnumerateRecipients() throws Exception {
        mvc.perform(get("/internal/notification-recipients/9223372036854775807"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/notification-recipients/9223372036854775807")
                        .header("X-Notification-Credential", "incorrect"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/notification-recipients/9223372036854775807")
                        .header("X-Notification-Credential", "public-fixture-notification-credential-123456"))
                .andExpect(status().isNotFound());
    }
}
