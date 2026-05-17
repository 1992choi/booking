package com.example.booking.api.internal.controller;

import com.example.booking.api.internal.dto.AvailableTimeSnapshot;
import com.example.booking.api.internal.dto.ResourceSnapshot;
import com.example.booking.api.internal.dto.UserSnapshot;
import com.example.booking.api.resource.domain.AvailableTime;
import com.example.booking.api.resource.domain.AvailableTimeRepository;
import com.example.booking.api.resource.service.ResourceService;
import com.example.booking.api.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InternalController {

    private final ResourceService resourceService;
    private final UserService userService;
    private final AvailableTimeRepository availableTimeRepository;

    @GetMapping("/api/v1/internal/resources/{id}")
    public ResourceSnapshot getResource(@PathVariable Long id) {
        return ResourceSnapshot.from(resourceService.getById(id));
    }

    @GetMapping("/api/v1/internal/users/{id}")
    public UserSnapshot getUser(@PathVariable Long id) {
        return UserSnapshot.from(userService.getById(id));
    }

    @GetMapping("/api/v1/internal/available-times")
    public List<AvailableTimeSnapshot> getAvailableTimes(@RequestParam List<Long> ids) {
        return availableTimeRepository.findAllById(ids).stream()
                .map(AvailableTimeSnapshot::from)
                .toList();
    }

    @PutMapping("/api/v1/internal/available-times/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void blockAvailableTimes(@RequestBody List<Long> ids) {
        List<AvailableTime> times = availableTimeRepository.findAllById(ids);
        times.forEach(AvailableTime::block);
        availableTimeRepository.saveAll(times);
    }

    @PutMapping("/api/v1/internal/available-times/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void releaseAvailableTimes(@RequestBody List<Long> ids) {
        List<AvailableTime> times = availableTimeRepository.findAllById(ids);
        times.forEach(AvailableTime::release);
        availableTimeRepository.saveAll(times);
    }
}