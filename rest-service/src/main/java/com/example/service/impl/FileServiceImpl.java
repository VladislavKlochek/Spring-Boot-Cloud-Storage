package com.example.service.impl;

import com.example.dao.AppDocumentDAO;
import com.example.dao.AppPhotoDAO;
import com.example.entity.AppDocument;
import com.example.entity.AppPhoto;
import com.example.service.FileService;

import com.example.utils.Decoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;


import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
@Log4j
public class FileServiceImpl implements FileService {
    private final AppDocumentDAO appDocumentDAO;
    private final AppPhotoDAO appPhotoDAO;
    private final Decoder decoder;

    @Override
    public AppDocument getDocument(String hash) {
        var id = decoder.idOf(hash);
        if(id == null){
            return null;
        }
        return appDocumentDAO.findById(id).orElse(null);
    }

    @Override
    public AppPhoto getPhoto(String hash) {
        var id = decoder.idOf(hash);
        if(id == null){
            return null;
        }
        return appPhotoDAO.findById(id).orElse(null);
    }

}
