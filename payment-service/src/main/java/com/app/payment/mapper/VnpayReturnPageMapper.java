package com.app.payment.mapper;

import com.app.payment.dto.response.VnpayReturnResponse;
import com.app.payment.entity.PaymentStatus;
import com.app.payment.entity.RefundStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class VnpayReturnPageMapper {

    private final String template;

    public VnpayReturnPageMapper() {
        try {
            template = new ClassPathResource("payment/vnpay-return.html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Payment result template unavailable", exception);
        }
    }

    public String render(VnpayReturnResponse result, String nonce) {
        String title;
        String message;
        String tone;
        if (result.refundStatus() == RefundStatus.REFUNDED) {
            title = "Đã hoàn tiền";
            message = "Khoản thanh toán của đơn hàng đã được ghi nhận hoàn tiền.";
            tone = "neutral";
        } else if (result.reservationExpired() || result.refundStatus() == RefundStatus.REQUIRED) {
            title = "Đơn hàng không còn hiệu lực";
            message = result.paymentStatus() == PaymentStatus.SUCCESS
                    ? "Đã nhận kết quả thanh toán nhưng đơn không còn hiệu lực. Khoản tiền cần được xử lý hoàn; đơn không được xác nhận lại."
                    : "Thời gian giữ hàng đã hết. Đơn hàng không được xác nhận. Vui lòng tạo đơn mới.";
            tone = "warning";
        } else if (result.paymentStatus() == PaymentStatus.SUCCESS) {
            title = "Thanh toán thành công";
            message = "Hệ thống đã ghi nhận thanh toán cho đơn hàng. Đơn được xác nhận tự động qua sự kiện; thông báo được gửi tới email đã đăng ký.";
            tone = "success";
        } else if (result.paymentStatus() == PaymentStatus.FAILED) {
            title = "Thanh toán không thành công";
            message = "Hệ thống đã ghi nhận thanh toán thất bại. Bạn có thể tạo đơn mới để thử lại.";
            tone = "warning";
        } else {
            title = "Đang chờ xác nhận thanh toán";
            message = "Bạn đã quay về từ VNPay. Hệ thống đang đối chiếu kết quả thanh toán với VNPay; trang này sẽ tự cập nhật."
                    + ("FAILED".equals(result.providerOutcome()) ? " VNPay báo giao dịch chưa thành công hoặc đã hủy." : "");
            tone = "pending";
        }
        return page(title, message, tone, String.valueOf(result.orderId()), String.valueOf(result.paymentId()),
                NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(result.amount()) + " ₫",
                result.reference(), result.paymentStatus() == PaymentStatus.PENDING && !result.reservationExpired(), nonce);
    }

    public String renderError(String nonce) {
        return page("Chưa thể xác minh kết quả", "Liên kết không hợp lệ hoặc dịch vụ chưa sẵn sàng. Không có thanh toán nào được xác nhận từ trang này. Vui lòng kiểm tra đơn hàng trong tài khoản của bạn.",
                "warning", "—", "—", "—", "—", false, nonce);
    }

    private String page(String title, String message, String tone, String order, String payment,
                        String amount, String reference, boolean pending, String nonce) {
        return template.replace("{{title}}", escape(title))
                .replace("{{message}}", escape(message))
                .replace("{{tone}}", escape(tone))
                .replace("{{order}}", escape(order))
                .replace("{{payment}}", escape(payment))
                .replace("{{amount}}", escape(amount))
                .replace("{{reference}}", escape(reference))
                .replace("{{pending}}", Boolean.toString(pending))
                .replace("{{nonce}}", escape(nonce));
    }

    private String escape(String value) {
        return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name());
    }
}
