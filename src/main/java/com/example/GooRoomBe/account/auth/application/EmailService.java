package com.example.GooRoomBe.account.auth.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender javaMailSender;

    @Value("${app.frontend.base-url}")
    private String defaultFrontendUrl;

    @Async
    public void sendVerificationEmail(String email, String token, String redirectPage) {
        try {
            MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String path;

            if (redirectPage == null || redirectPage.isBlank()) {
                path = "/verify-email";
            } else {
                path = redirectPage.trim();
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
            }

            String link = defaultFrontendUrl + path + "?token=" + token;

            log.info("인증 이메일 리다이렉트 페이지: {}", redirectPage);
            log.info("최종 인증 이메일 리다이렉트 링크: {}", link);

            String htmlContent = """
                <!DOCTYPE html>
                <html>
                <head>
                <meta charset="UTF-8">
                <title>GooRoom 이메일 인증</title>
                </head>
                <body style="margin: 0; padding: 0; background-color: #f4f4f4; font-family: 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif;">
                    <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="background-color: #f4f4f4; padding: 20px 0;">
                        <tr>
                            <td align="center">
                                <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="600" style="background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 5px rgba(0,0,0,0.1);">
                                    <tr>
                                        <td align="center" style="padding: 30px 20px 20px 20px; background-color: #007bff;">
                                            <h1 style="color: #ffffff; margin: 0; font-size: 24px;">GooRoom에 오신 것을 환영합니다!</h1>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding: 30px 40px;">
                                            <p style="color: #333333; font-size: 16px; line-height: 1.6; margin-top: 0;">
                                                안녕하세요.<br>
                                                GooRoom 서비스 이용을 위해 아래 버튼을 클릭하여 이메일 인증을 완료해주세요.
                                            </p>
                                            <table role="presentation" border="0" cellpadding="0" cellspacing="0" width="100%%" style="margin: 30px 0;">
                                                <tr>
                                                    <td align="center">
                                                        <a href="%s" target="_blank" style="display: inline-block; padding: 14px 30px; background-color: #007bff; color: #ffffff; text-decoration: none; border-radius: 5px; font-weight: bold; font-size: 16px;">
                                                            이메일 인증하기
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>
                                            <p style="color: #666666; font-size: 14px; margin-bottom: 0;">
                                                본인이 요청하지 않은 경우 이 메일을 무시하셔도 됩니다.
                                            </p>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td align="center" style="padding: 20px; background-color: #f9f9f9; color: #888888; font-size: 12px;">
                                            &copy; GooRoom. All rights reserved.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(link);

            helper.setTo(email.toString());
            helper.setSubject("GooRoom 회원가입 이메일 인증");
            helper.setText(htmlContent, true);

            javaMailSender.send(mimeMessage);
            log.info("인증 이메일 전송 완료: {}", email);

        } catch (MailException | MessagingException e) {
            log.error("인증 이메일 전송 실패 (메일 서버 오류): {}", email, e);

        } catch (Exception e) {
            log.error("인증 이메일 전송 중 예상치 못한 오류 발생: {}", email, e);
        }
    }
}