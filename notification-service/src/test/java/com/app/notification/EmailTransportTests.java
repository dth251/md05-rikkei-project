package com.app.notification;

import static org.junit.jupiter.api.Assertions.*;

import com.app.notification.dto.response.EmailTask;
import com.app.notification.mapper.EmailMapper;
import com.app.notification.service.impl.EmailTransportImpl;
import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

class EmailTransportTests {

    @Test
    void actualSmtpReceivesUtf8ContentAndRetryKeepsMessageId() throws Exception {
        var smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        try {
            var transport = transport(smtp.getSmtp().getPort());
            var task = new EmailTask(UUID.randomUUID().toString(), 123L, 1L, "order.shipped", 1);
            transport.send(task, "recipient@example.com");
            transport.send(task, "recipient@example.com");
            assertTrue(smtp.waitForIncomingEmail(5000, 2));
            var messages = smtp.getReceivedMessages();
            assertEquals("recipient@example.com", messages[0].getAllRecipients()[0].toString());
            assertEquals("Đơn hàng #123 đang giao hàng", messages[0].getSubject());
            assertTrue(messages[0].getContent().toString().contains("Vui lòng đăng nhập"));
            assertEquals(messages[0].getMessageID(), messages[1].getMessageID());
            assertFalse(messages[0].getContent().toString().contains("recipient@example.com"));
        } finally {
            smtp.stop();
        }
    }

    @Test
    void smtpUnavailableIsBoundedAndHeaderInjectionIsRejected() {
        var task = new EmailTask(UUID.randomUUID().toString(), 123L, 1L, "order.confirmed", 1);
        var transport = transport(1);
        assertThrows(MailException.class, () -> transport.send(task, "recipient@example.com"));
        assertThrows(IllegalArgumentException.class, () -> transport.send(task, "a@example.com\r\nBcc: b@example.com"));
        assertThrows(IllegalArgumentException.class, () -> transport.send(task, "a@example.com,b@example.com"));
    }

    @Test
    void smtpReceivesConfirmationCancellationAndUnpaidExpiryEmails() throws Exception {
        var smtp = new GreenMail(new ServerSetup(0, "127.0.0.1", ServerSetup.PROTOCOL_SMTP));
        smtp.start();
        try {
            var transport = transport(smtp.getSmtp().getPort());
            String[] events = {"order.confirmed", "order.cancelled", "order.expired"};
            String[] subjects = {"đã xác nhận", "đã hủy", "đã hủy do quá hạn thanh toán"};
            for (int index = 0; index < events.length; index++) {
                transport.send(new EmailTask(UUID.randomUUID().toString(), 456L, 1L, events[index], 1),
                        "recipient@example.com");
            }
            assertTrue(smtp.waitForIncomingEmail(5000, 3));
            var messages = smtp.getReceivedMessages();
            for (int index = 0; index < subjects.length; index++) {
                assertEquals("Đơn hàng #456 " + subjects[index], messages[index].getSubject());
                assertEquals("recipient@example.com", messages[index].getAllRecipients()[0].toString());
            }
        } finally {
            smtp.stop();
        }
    }

    private EmailTransportImpl transport(
            int port
    ) {
        var sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1");
        sender.setPort(port);
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "1000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "1000");
        return new EmailTransportImpl(sender, new EmailMapper(), "orders@example.com");
    }
}
