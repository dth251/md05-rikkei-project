package com.app.notification.service.impl;

import com.app.notification.dto.response.EmailTask;
import com.app.notification.mapper.EmailMapper;
import com.app.notification.service.EmailTransport;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailTransportImpl implements EmailTransport {

    private final JavaMailSender sender;
    private final EmailMapper mapper;
    private final String from;

    public EmailTransportImpl(
            JavaMailSender sender,
            EmailMapper mapper,
            @Value("${notification.email.from:}") String from
    ) {
        this.sender = sender;
        this.mapper = mapper;
        this.from = from;
        validateAddress(from);
    }

    @Override
    public void send(
            EmailTask task,
            String recipient
    ) {
        validateAddress(recipient);
        try {
            // Keep Message-ID stable across retries. SMTP does not guarantee recipient deduplication.
            var original = sender.createMimeMessage();
            var message = new MimeMessage(original.getSession()) {
                @Override
                protected void updateMessageID() throws MessagingException {
                    setHeader("Message-ID", "<" + task.deliveryId() + "@notification.omnichannel>");
                }
            };
            var helper = new MimeMessageHelper(message, "UTF-8");
            var content = mapper.toContent(task);
            helper.setFrom(from);
            helper.setTo(recipient);
            helper.setSubject(content.subject());
            helper.setText(content.text(), false);
            sender.send(message);
        } catch (MessagingException exception) {
            throw new MailPreparationException("Email preparation unavailable");
        }
    }

    private static void validateAddress(
            String address
    ) {
        try {
            if (address == null || address.length() > 150 || address.contains("\r") || address.contains("\n")) {
                throw new IllegalArgumentException("Invalid recipient");
            }
            var parsed = new InternetAddress(address, true);
            parsed.validate();
            if (!address.equals(parsed.getAddress()) || parsed.getPersonal() != null) {
                throw new IllegalArgumentException("Invalid recipient");
            }
        } catch (jakarta.mail.internet.AddressException exception) {
            throw new IllegalArgumentException("Invalid recipient");
        }
    }
}
