package com.eatcloud.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.eatcloud.logging.annotation.Loggable;

@Service
@RequiredArgsConstructor
@Loggable(level = Loggable.LogLevel.INFO, logParameters = true, logResult = true,maskSensitiveData = true)
public class MailService {

    private final JavaMailSender javaMailSender;

    public void sendMail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }
}
