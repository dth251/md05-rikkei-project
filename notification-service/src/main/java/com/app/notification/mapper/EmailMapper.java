package com.app.notification.mapper;

import com.app.notification.dto.response.EmailContent;
import com.app.notification.dto.response.EmailTask;
import org.springframework.stereotype.Component;

@Component
public class EmailMapper {

    public EmailContent toContent(
            EmailTask task
    ) {
        String status = switch (task.eventType()) {
            case "order.confirmed" -> "đã xác nhận";
            case "order.cancelled" -> "đã hủy";
            case "order.shipped" -> "đang giao hàng";
            case "order.completed" -> "đã hoàn tất";
            default -> throw new IllegalArgumentException("Unsupported email event");
        };
        return new EmailContent("Đơn hàng #" + task.orderId() + " " + status,
                "Đơn hàng #" + task.orderId() + " của bạn " + status + ".\n"
                        + "Vui lòng đăng nhập để xem thông tin đơn hàng.");
    }
}
