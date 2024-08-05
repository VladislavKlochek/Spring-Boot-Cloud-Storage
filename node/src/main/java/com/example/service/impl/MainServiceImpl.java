package com.example.service.impl;

import com.example.dao.AppUserDAO;
import com.example.dao.RawDataDAO;
import com.example.entity.AppDocument;
import com.example.entity.AppPhoto;
import com.example.entity.AppUser;
import com.example.entity.RawData;
import com.example.exceptions.UploadFileException;
import com.example.service.AppUserService;
import com.example.service.FileService;
import com.example.service.MainService;
import com.example.service.ProducerService;
import com.example.service.enums.LinkType;
import com.example.service.enums.ServiceCommands;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import static com.example.entity.enums.UserState.BASIC_STATE;
import static com.example.entity.enums.UserState.WAIT_FOR_EMAIL_STATE;
import static com.example.service.enums.ServiceCommands.*;

@RequiredArgsConstructor
@Component
@Log4j
public class MainServiceImpl implements MainService {
    private final RawDataDAO rawDataDAO;
    private final ProducerService producerService;
    private final AppUserDAO appUserDAO;
    private final FileService fileService;
    private final AppUserService appUserService;

    @Transactional
    @Override
    public void processTextMessage(Update update) {
        saveRawData(update);

        var appUser = findOrSaveAppUser(update);
        var userState = appUser.getState();
        var text = update.getMessage().getText();
        var output = "";

        var serviceCommands = ServiceCommands.fromValue(text);
        if(CANCEL.equals(serviceCommands)){
            output = cancelProcess(appUser);
        }
        else if (BASIC_STATE.equals(userState)){
            output = processServiceCommand(appUser, text);
        }
        else if (WAIT_FOR_EMAIL_STATE.equals(userState)){
            output = appUserService.setEmail(appUser, text);
        }
        else {
            log.error("Unknown user state: " + userState);
            output = "Неизвестная ошибка, введите /cancel и попробуйте снова.";
        }

        var chatId = update.getMessage().getChatId();
        sendAnswer(output, chatId);
    }

    @Override
    public void processDocMessage(Update update) {
        saveRawData(update);

        var appUser = findOrSaveAppUser(update);
        var chatId = update.getMessage().getChatId();
        if(isNotAllowToSendContent(chatId, appUser)){
            return;
        }
        try{
            AppDocument doc = fileService.processDoc(update.getMessage());
            String link = fileService.generateLink(doc.getId(), LinkType.GET_DOC);
            var answer = "Документ успешно загружен! "
                    + "Ссылка для скачивания: " + link;
            sendAnswer(answer, chatId);
        }
        catch (UploadFileException ex){
            log.error(ex);
            String error = "К сожалению, загрузка файла не удалась. Повторите попытку позже.";
            sendAnswer(error, chatId);
        }
    }

    @Override
    public void processPhotoMessage(Update update) {
        saveRawData(update);

        var appUser = findOrSaveAppUser(update);
        var chatId = update.getMessage().getChatId();
        if(isNotAllowToSendContent(chatId, appUser)){
            return;
        }
        try{
            AppPhoto photo = fileService.processPhoto(update.getMessage());
            String link = fileService.generateLink(photo.getId(), LinkType.GET_PHOTO);
            var answer = "Фото успешно загружено! Ссылка для скачивания: "  + link;
            sendAnswer(answer, chatId);
        }
        catch(UploadFileException ex){
            log.error(ex);
            String error = "К сожалению, загрузка файла не удалась. Повторите попытку позже.";
            sendAnswer(error, chatId);
        }
    }

    private boolean isNotAllowToSendContent(Long chatId, AppUser appUser) {
        var userState = appUser.getState();
        if(!appUser.getIsActivated()){
            var error = "Заригестрируйтесь или активируйте свою учетную запись для загрузки контена.";
            sendAnswer(error, chatId);
            return true;
        }
        else if (!BASIC_STATE.equals(userState)){
            var error = "Отмените текущую команду с помощью /cancel для отправки файлов.";
            sendAnswer(error, chatId);
            return true;
        }
        return false;
    }

    private void sendAnswer(String output, Long chatId) {
        SendMessage senMessage = new SendMessage();
        senMessage.setChatId(chatId);
        senMessage.setText(output);
        producerService.producerAnswer(senMessage);
    }

    private String processServiceCommand(AppUser appUser, String cmd) {
        ServiceCommands command = ServiceCommands.fromValue(cmd);
        if(REGISTRATION.equals(command)){
            return appUserService.registerUser(appUser);
        }
        else if (HELP.equals(command)){
            return help();
        }
        else if (START.equals(command)){
            return "Приветствую. Чтобы посмотреть список доступных команд введите /help.";
        }
        else {
            return "Неизвестная команда! Чтобы посмотреть список доступных команд введите /help.";
        }
    }

    private String help() {
        return "Список доступных команд:\n" +
                "/cancel - отмена выполнения текущей команды;\n" +
                "/registration - регистрация пользователя.";
    }

    private String cancelProcess(AppUser appUser) {
        appUser.setState(BASIC_STATE);
        appUserDAO.save(appUser);
        return "Комана отменена.";
    }

    private AppUser findOrSaveAppUser(Update update){
        User telegramUser = update.getMessage().getFrom();
        var appUserOpt = appUserDAO.findByTelegramUserId(telegramUser.getId());
        if(appUserOpt.isEmpty()){
            var transientAppUser = AppUser.builder()
                    .telegramUserId(telegramUser.getId())
                    .username(telegramUser.getUserName())
                    .firstName(telegramUser.getFirstName())
                    .lastName(telegramUser.getLastName())
                    .isActivated(false)
                    .state(BASIC_STATE)
                    .build();
            return appUserDAO.save(transientAppUser);
        }
        return appUserOpt.get();
    }

    private void saveRawData(Update update) {
        RawData rawData = RawData.builder()
                .event(update)
                .build();

        rawDataDAO.save(rawData);
    }
}
