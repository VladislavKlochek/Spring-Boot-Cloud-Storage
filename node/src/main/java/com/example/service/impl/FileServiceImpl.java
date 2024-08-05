package com.example.service.impl;

import com.example.dao.AppDocumentDAO;
import com.example.dao.AppPhotoDAO;
import com.example.dao.BinaryContentDAO;
import com.example.entity.AppDocument;
import com.example.entity.AppPhoto;
import com.example.entity.BinaryContent;
import com.example.exceptions.UploadFileException;
import com.example.service.FileService;
import com.example.service.enums.LinkType;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;

import org.hashids.Hashids;
import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;

@RequiredArgsConstructor
@Service
@Log4j
public class FileServiceImpl implements FileService {
    @Value("${token}")
    private String botToken;
    @Value("${service.file_info.uri}")
    private String fileInfoUri;
    @Value("${service.file_storage.uri}")
    private String fileStorageUri;
    @Value("${link.address}")
    private String linkAddress;
    private final AppDocumentDAO appDocumentDAO;
    private final AppPhotoDAO appPhotoDAO;
    private final BinaryContentDAO binaryContentDAO;
    private final Hashids hashids;


    @Override
    public AppDocument processDoc(Message telegramMessage) {
        var telegramDoc = telegramMessage.getDocument();
        var fileId = telegramDoc.getFileId();
        var response = getFilePath(fileId);
        if(response.getStatusCode() == HttpStatus.OK){
            var persistentBinaryContent = getPersistentBinaryContent(response);
            var transientAppDoc = buildTransientAppDoc(telegramDoc, persistentBinaryContent);
            return appDocumentDAO.save(transientAppDoc);
        }
        else {
            throw new UploadFileException("Bad response from telegram service: " + response);
        }
    }

    private BinaryContent getPersistentBinaryContent(ResponseEntity<String> response) {
        var filePath = getFilePath(response);
        var fileInByte = downloadFile(filePath);
        var transientBinaryContent = BinaryContent.builder()
                .fileAsArrayOfBytes(fileInByte)
                .build();
        return binaryContentDAO.save(transientBinaryContent);
    }

    private String getFilePath(ResponseEntity<String> response) {
        var jsonObject = new JSONObject(response.getBody());
        return String.valueOf(jsonObject
                .getJSONObject("result")
                .getString("file_path"));
    }

    @Override
    public AppPhoto processPhoto(Message telegramMessage) {
        var photoSizeCount = telegramMessage.getPhoto().size();
        var photoIndex = photoSizeCount > 1 ? telegramMessage.getPhoto().size() - 1 : 0;
        var telegramPhoto = telegramMessage.getPhoto().get(photoIndex);
        var fileId = telegramPhoto.getFileId();
        var response = getFilePath(fileId);
        if(response.getStatusCode() == HttpStatus.OK){
            var persistentBinaryContent = getPersistentBinaryContent(response);
            var transientAppPhoto = buildTransientAppPhoto(telegramPhoto, persistentBinaryContent);
            return appPhotoDAO.save(transientAppPhoto);
        }
        else {
            throw new UploadFileException("Bad response from telegram service: " + response);
        }
    }

    private AppPhoto buildTransientAppPhoto(PhotoSize telegramPhoto, BinaryContent persistentBinaryContent) {
        return AppPhoto.builder()
                .telegramFieldId(telegramPhoto.getFileId())
                .binaryContent(persistentBinaryContent)
                .fileSize(telegramPhoto.getFileSize())
                .build();
    }

    private AppDocument buildTransientAppDoc(Document telegramDoc, BinaryContent persistentBinaryContent) {
        return AppDocument.builder()
                .telegramFieldId(telegramDoc.getFileId())
                .docName(telegramDoc.getFileName())
                .binaryContent(persistentBinaryContent)
                .mimeType(telegramDoc.getMimeType())
                .fileSize(telegramDoc.getFileSize())
                .build();
    }

    private byte[] downloadFile(String filePath) {
        var fullUri = fileStorageUri
                .replace("{token}", botToken)
                .replace("{filePath}", filePath);
        URL urlObj;
        try{
            urlObj = new URL(fullUri);
        }
        catch(MalformedURLException e){
            throw new UploadFileException(e);
        }
        //TODO поумать над оптимизацией
        try(InputStream is = urlObj.openStream()) {
            return is.readAllBytes();
        }
        catch (IOException e){
            throw new UploadFileException(urlObj.toExternalForm(), e);
        }
    }


    private ResponseEntity<String> getFilePath(String fileId) {
        var restTemplate = new RestTemplate();
        var headers = new HttpHeaders();
        var request = new HttpEntity<>(headers);

        return restTemplate.exchange(
                fileInfoUri,
                HttpMethod.GET,
                request,
                String.class,
                botToken,
                fileId
        );
    }

    @Override
    public String generateLink(Long docId, LinkType linkType) {
        var hash = hashids.encode(docId);
        return linkAddress + "/api/" + linkType + "?id=" + hash;
    }
}
