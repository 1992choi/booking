package com.example.booking.api.user.service;

import com.example.booking.api.user.domain.User;
import com.example.booking.api.user.domain.UserRepository;
import com.example.booking.api.user.dto.UserUpdateRequest;
import com.example.booking.api.user.event.UserDeletedDomainEvent;
import com.example.booking.api.user.event.UserUpdatedDomainEvent;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Transactional
    public User update(Long userId, UserUpdateRequest request) {
        User user = getById(userId);
        user.update(request.name(), request.phone());
        eventPublisher.publishEvent(new UserUpdatedDomainEvent(user.getId(), user.getName(), user.getEmail(), user.getPhone()));
        return user;
    }

    @Transactional
    public void delete(Long userId) {
        User user = getById(userId);
        userRepository.delete(user);
        eventPublisher.publishEvent(new UserDeletedDomainEvent(userId));
    }
}
