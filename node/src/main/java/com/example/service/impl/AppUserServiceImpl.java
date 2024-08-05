package com.example.service.impl;

import com.example.dao.AppUserDAO;
import com.example.dto.MailParams;
import com.example.entity.AppUser;
import com.example.service.AppUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;

import org.hashids.Hashids;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import static com.example.entity.enums.UserState.BASIC_STATE;
import static com.example.entity.enums.UserState.WAIT_FOR_EMAIL_STATE;

@RequiredArgsConstructor
@Service
@Log4j
public class AppUserServiceImpl implements AppUserService {
    private final AppUserDAO appUserDAO;
    private final Hashids hashids;
    @Value("${spring.rabbitmq.queues.registration-mail}")
    private String registrationMailQueue;

    private final RabbitTemplate rabbitTemplate;
    @Override
    public String registerUser(AppUser appUser) {
        if(appUser.getIsActivated()){
            return "Вы уже зарегистрированы!";
        }
        else if (appUser.getEmail() != null) {
            return "Вам на почту уже отправлено письмо." +
                    " Перейдите по ссылке в письме для подтверждения регистрации.";
        }
        appUser.setState(WAIT_FOR_EMAIL_STATE);
        appUserDAO.save(appUser);
        return "Введите ваш email: ";
    }

    @Override
    public String setEmail(AppUser appUser, String email) {
        try{
            InternetAddress emailAddress = new InternetAddress(email);
            emailAddress.validate();
        } catch (AddressException e) {
            return "Пожалуйста, введите корректный email адрес. Для отменты команды введите /cancel";
        }
        var appUserOpt = appUserDAO.findByEmail(email);
        if(appUserOpt.isEmpty()){
            appUser.setEmail(email);
            appUser.setState(BASIC_STATE);
            appUser = appUserDAO.save(appUser);
            var cryptoUserId = hashids.encode(appUser.getId());
            sendRegistrationMail(cryptoUserId, email);
            return "Вам на почту было отправлено письмо." +
                    " Перейдите по ссылке в письме для подтверждения регистрации.";
        }
        else {
            return "Этот email уже используется. Введите корректный email." +
                    " Для отмены команды введите /cancel.";
        }
    }

    private void sendRegistrationMail(String cryptoUserId, String email){
        var mailParams = MailParams.builder()
                .id(cryptoUserId)
                .emailTo(email)
                .build();

        rabbitTemplate.convertAndSend(registrationMailQueue, mailParams);
    }
}
