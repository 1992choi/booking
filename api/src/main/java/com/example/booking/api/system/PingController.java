package com.example.booking.api.system;

import com.example.booking.core.PingService;
import com.example.booking.core.error.BusinessException;
import com.example.booking.core.error.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PingController {

    private final PingService pingService;

    @GetMapping("/ping")
    public String ping(@RequestParam(defaultValue = "false") boolean fail) {
        if (fail) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST);
        }

        return "[API] " + pingService.ping();
    }

}
