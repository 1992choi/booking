package com.example.booking.api.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChaosController {

    @GetMapping("/api/v1/test/error500")
    public String error500() {
        throw new RuntimeException("테스트용 강제 500 에러 (Grafana 알림 테스트)");
    }

}
