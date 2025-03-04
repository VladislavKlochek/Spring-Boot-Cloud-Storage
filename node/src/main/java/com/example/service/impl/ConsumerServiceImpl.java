package com.example.service.impl;

import com.example.service.ConsumerService;
import com.example.service.MainService;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import org.telegram.telegrambots.meta.api.objects.Update;

@RequiredArgsConstructor
@Service
@Log4j
public class ConsumerServiceImpl implements ConsumerService {
    private final MainService mainService;

    @Override
    @RabbitListener(queues = "${spring.rabbitmq.queues.text-message-update}")
    public void consumeTextMessageUpdate(Update update) {
        log.debug("NODE: Text message is received");
        mainService.processTextMessage(update);
    }

    @Override
    @RabbitListener(queues = "${spring.rabbitmq.queues.doc-message-update}")
    public void consumeDocMessageUpdate(Update update) {
        log.debug("NODE: Doc message is received");
        mainService.processDocMessage(update);
    }

    @Override
    @RabbitListener(queues = "${spring.rabbitmq.queues.photo-message-update}")
    public void consumePhotoMessageUpdate(Update update) {
        log.debug("NODE: Photo message is received");
        mainService.processPhotoMessage(update);
    }
}
