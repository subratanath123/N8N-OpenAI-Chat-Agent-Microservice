package net.ai.chatbot.service.team;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Sends an email when a team member is invited, including a link to the web app.
 * Requires {@code spring.mail.host} (and credentials) plus {@code app.frontend-url}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamInviteNotificationService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.frontend-url:}")
    private String frontendBaseUrl;

    @Value("${app.frontend-invite-path:/ai-chatbots}")
    private String invitePath;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Value("${spring.mail.username:}")
    private String springMailUsername;

    /**
     * Fire-and-forget style call; failures are logged and never thrown to the caller.
     */
    public void notifyInvited(String memberEmail, String inviterEmail, String roleDisplay) {
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.debug("Team invite email skipped: JavaMailSender not configured (set SMTP_HOST / spring.mail.host).");
            return;
        }

        String appLink = buildInviteUrl();
        if (!StringUtils.hasText(appLink)) {
            log.warn("Team invite email skipped: app.frontend-url is not set (FRONTEND_URL / APP_FRONTEND_URL).");
            return;
        }

        String from = resolveFromAddress();
        if (!StringUtils.hasText(from)) {
            log.warn("Team invite email skipped: set app.mail.from or spring.mail.username as the sender address.");
            return;
        }

        String safeInviter = escapeHtml(inviterEmail);
        String safeRole = escapeHtml(roleDisplay);

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(memberEmail);
            helper.setSubject("You have been invited to collaborate");

            String text = String.format(
                    "Hello,%n%n%s has added you to their team on the platform with the role: %s.%n%n"
                            + "Open the app to get started:%n%s%n%n"
                            + "If you did not expect this message, you can ignore it.%n",
                    inviterEmail, roleDisplay, appLink);

            String html = String.format(
                    "<html><body style=\"font-family:system-ui,Segoe UI,sans-serif;line-height:1.5;color:#0f172a;\">"
                            + "<p>Hello,</p>"
                            + "<p><strong>%s</strong> has added you to their team with the role: <strong>%s</strong>.</p>"
                            + "<p><a href=\"%s\" style=\"color:#2563eb;\">Open the app</a></p>"
                            + "<p style=\"font-size:13px;color:#64748b;\">If the button does not work, copy this link into your browser:<br/>"
                            + "<span style=\"word-break:break-all;\">%s</span></p>"
                            + "<p style=\"font-size:13px;color:#64748b;\">If you did not expect this message, you can ignore it.</p>"
                            + "</body></html>",
                    safeInviter, safeRole, escapeHtml(appLink), escapeHtml(appLink));

            helper.setText(text, html);
            sender.send(message);
            log.info("Sent team invite email to {}", memberEmail);
        } catch (MessagingException e) {
            log.error("Failed to send team invite email to {}: {}", memberEmail, e.getMessage());
        }
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(mailFrom)) {
            return mailFrom.trim();
        }
        if (StringUtils.hasText(springMailUsername)) {
            return springMailUsername.trim();
        }
        return null;
    }

    private String buildInviteUrl() {
        if (!StringUtils.hasText(frontendBaseUrl)) {
            return null;
        }
        String base = frontendBaseUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = StringUtils.hasText(invitePath) ? invitePath.trim() : "/ai-chatbots";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
