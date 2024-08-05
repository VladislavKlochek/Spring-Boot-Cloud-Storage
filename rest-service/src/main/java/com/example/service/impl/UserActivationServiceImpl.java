package com.example.service.impl;

import com.example.dao.AppUserDAO;
import com.example.service.UserActivationService;
import com.example.utils.Decoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j;
import org.springframework.stereotype.Service;

@Log4j
@RequiredArgsConstructor
@Service
public class UserActivationServiceImpl implements UserActivationService {

    private final AppUserDAO appUserDAO;
    private final Decoder decoder;

    @Override
    public Boolean activation(String cryptoUserId) {
        var userId = decoder.idOf(cryptoUserId);
        log.debug(String.format("User activation with user-id=%s", userId));
        if (userId == null) {
            return false;
        }
        var optional = appUserDAO.findById(userId);
        if(optional.isPresent()){
            var user = optional.get();
            user.setIsActivated(true);
            appUserDAO.save(user);
            return true;
        }
        return false;
    }
}
