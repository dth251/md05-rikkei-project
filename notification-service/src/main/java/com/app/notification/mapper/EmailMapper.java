package com.app.notification.mapper;

import com.app.notification.dto.response.EmailContent;
import com.app.notification.dto.response.EmailTask;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.util.HtmlUtils;
import org.springframework.stereotype.Component;

@Component
public class EmailMapper {

    private final String template;

    public EmailMapper() {
        try {
            template = new ClassPathResource("email/order-notification.html")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Order email template unavailable", exception);
        }
    }

    public EmailContent toContent(
            EmailTask task
    ) {
        String status = switch (task.eventType()) {
            case "order.confirmed" -> "đã xác nhận";
            case "order.cancelled" -> "đã hủy";
            case "order.expired" -> "đã hủy do quá hạn thanh toán";
            case "order.shipped" -> "đang giao hàng";
            case "order.completed" -> "đã hoàn tất";
            default -> throw new IllegalArgumentException("Unsupported email event");
        };
        String title = switch (task.eventType()) {
            case "order.confirmed" -> "Cảm ơn bạn đã đặt hàng.";
            case "order.cancelled" -> "Đơn hàng đã được hủy.";
            case "order.expired" -> "Đơn hàng đã quá hạn thanh toán.";
            case "order.shipped" -> "Đơn hàng đang trên đường đến bạn.";
            case "order.completed" -> "Đơn hàng đã hoàn tất.";
            default -> throw new IllegalArgumentException("Unsupported email event");
        };
        String message = switch (task.eventType()) {
            case "order.confirmed" -> "Đơn hàng của bạn đã được xác nhận. Bạn có thể xem thông tin đơn hàng trong tài khoản.";
            case "order.cancelled" -> "Đơn hàng đã được hủy và phần hàng giữ chỗ đã được trả lại kho.";
            case "order.expired" -> "Đơn hàng chưa hoàn tất thanh toán trong thời gian giữ hàng nên đã được hủy. Bạn có thể tạo đơn mới để tiếp tục mua sắm.";
            case "order.shipped" -> "Đơn hàng của bạn đang được giao. Vui lòng theo dõi tình trạng giao hàng trong tài khoản.";
            case "order.completed" -> "Hệ thống đã ghi nhận đơn hàng hoàn tất. Cảm ơn bạn đã mua sắm cùng MD05.";
            default -> throw new IllegalArgumentException("Unsupported email event");
        };
        String subject = "Đơn hàng #" + task.orderId() + " " + status;
        String text = "Đơn hàng #" + task.orderId() + " của bạn " + status + ".\n"
                + message + "\nVui lòng đăng nhập để xem thông tin đơn hàng.";
        boolean warning = task.eventType().equals("order.cancelled") || task.eventType().equals("order.expired");
        String html = template.replace("{{subject}}", escape(subject))
                .replace("{{title}}", escape(title))
                .replace("{{message}}", escape(message))
                .replace("{{order}}", escape("#" + task.orderId()))
                .replace("{{status}}", escape(status))
                .replace("{{badge}}", escape(status.toUpperCase(Locale.forLanguageTag("vi-VN"))))
                .replace("{{badgeBackground}}", warning ? "#fff1e7" : "#eaf4ee")
                .replace("{{badgeColor}}", warning ? "#8c481e" : "#23684e");
        return new EmailContent(subject, text, html);
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
