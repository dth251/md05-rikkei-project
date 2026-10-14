package com.app.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.app.notification.dto.response.EmailTask;
import com.app.notification.mapper.EmailMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmailMapperTests {

    @Test
    void everyOrderStageRendersActualOrderAndItsOwnStatusWithoutDemoData() throws Exception {
        var mapper = new EmailMapper();
        var events = List.of("order.confirmed", "order.cancelled", "order.expired", "order.shipped", "order.completed");
        var statuses = List.of("đã xác nhận", "đã hủy", "đã hủy do quá hạn thanh toán", "đang giao hàng", "đã hoàn tất");
        Path preview = Path.of("build", "email-preview");
        Files.createDirectories(preview);
        for (int index = 0; index < events.size(); index++) {
            var content = mapper.toContent(new EmailTask(UUID.randomUUID().toString(), 123L, 999L, events.get(index), 1));
            assertTrue(content.subject().contains("#123"));
            assertTrue(content.text().contains(statuses.get(index)));
            assertTrue(content.html().contains(statuses.get(index)));
            assertTrue(content.html().contains("#123"));
            assertFalse(content.html().contains("{{"));
            assertFalse(content.html().contains("<script"));
            assertFalse(content.html().contains("https://"));
            assertFalse(content.html().contains("125.000"));
            assertFalse(content.html().contains("999"));
            Files.writeString(preview.resolve(events.get(index) + ".html"), content.html());
        }
        assertThrows(IllegalArgumentException.class, () -> mapper.toContent(
                new EmailTask(UUID.randomUUID().toString(), 123L, 1L, "<script>alert(1)</script>", 1)));
    }
}
