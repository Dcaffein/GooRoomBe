package com.example.DunbarHorizon.account.adapter.out.email;

import com.example.DunbarHorizon.account.application.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailAdapter implements EmailPort {

    private final JavaMailSender javaMailSender;

    @Value("${app.frontend.base-url}")
    private String defaultFrontendUrl;

    @Override
    @Async
    public void sendSignupVerificationEmail(String email, String token, String redirectPage) {
        send(email, "DunbarHorizon 회원가입 이메일 인증",
                getVerificationHtml(buildVerificationLink(token, redirectPage)));
    }

    @Override
    @Async
    public void sendAlreadyRegisteredEmail(String email) {
        send(email, "DunbarHorizon 회원가입 안내", getAlreadyRegisteredHtml(defaultFrontendUrl));
    }

    private void send(String email, String subject, String htmlContent) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setTo(email);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("이메일 전송 완료: {} ({})", email, subject);

        } catch (MailException | MessagingException e) {
            log.error("이메일 전송 실패 (메일 서버 오류): {}", email, e);
        } catch (Exception e) {
            log.error("이메일 전송 중 예상치 못한 오류 발생: {}", email, e);
        }
    }

    private String buildVerificationLink(String token, String redirectPage) {
        String path = (redirectPage == null || redirectPage.isBlank()) ? "/verify-email" : redirectPage.trim();
        if (!path.startsWith("/")) path = "/" + path;
        return defaultFrontendUrl + path + "?token=" + token;
    }

    private String getVerificationHtml(String link) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin: 0; padding: 0; background-color: #f4f4f4; font-family: sans-serif;">
                <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="padding: 20px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="600" style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">
                                <tr><td align="center" style="padding: 30px; background-color: #007bff;"><h1 style="color: #ffffff; margin: 0;">DunbarHorizon</h1></td></tr>
                                <tr><td style="padding: 40px;"><p>아래 버튼을 눌러 이메일 인증을 완료하면 비밀번호를 설정하고 가입을 마칠 수 있습니다.</p>
                                <p style="color: #888; font-size: 13px;">이 링크는 1시간 후 만료됩니다. 만료되면 이메일 입력부터 다시 시작해주세요.</p>
                                <div style="text-align: center; margin: 30px;"><a href="%s" style="padding: 14px 30px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px;">이메일 인증하고 가입 계속하기</a></div>
                                <p style="color: #888; font-size: 13px;">본인이 요청하지 않았다면 이 메일을 무시하세요. 계정은 만들어지지 않습니다.</p>
                                </td></tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(link);
    }

    private String getAlreadyRegisteredHtml(String loginUrl) {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="UTF-8"></head>
            <body style="margin: 0; padding: 0; background-color: #f4f4f4; font-family: sans-serif;">
                <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="padding: 20px 0;">
                    <tr>
                        <td align="center">
                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="600" style="background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">
                                <tr><td align="center" style="padding: 30px; background-color: #007bff;"><h1 style="color: #ffffff; margin: 0;">DunbarHorizon</h1></td></tr>
                                <tr><td style="padding: 40px;"><p>이 이메일로는 이미 가입된 계정이 있습니다. 새로 가입하실 필요 없이 로그인해주세요.</p>
                                <div style="text-align: center; margin: 30px;"><a href="%s" style="padding: 14px 30px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px;">로그인하러 가기</a></div>
                                <p style="color: #888; font-size: 13px;">본인이 요청하지 않았다면 이 메일을 무시하세요. 계정에는 아무 변화가 없습니다.</p>
                                </td></tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(loginUrl);
    }
}