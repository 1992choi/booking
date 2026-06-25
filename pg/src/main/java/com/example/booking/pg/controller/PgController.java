package com.example.booking.pg.controller;

import com.example.booking.pg.dto.ApproveRequest;
import com.example.booking.pg.dto.ApproveResponse;
import com.example.booking.pg.dto.CancelRequest;
import com.example.booking.pg.dto.CancelResponse;
import com.example.booking.pg.service.PgService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PgController {

    private final PgService pgService;

    @PostMapping("/pg/approve")
    public ApproveResponse approve(@Valid @RequestBody ApproveRequest request) {
        return pgService.approve(request);
    }

    @PostMapping("/pg/cancel")
    public CancelResponse cancel(@Valid @RequestBody CancelRequest request) {
        return pgService.cancel(request);
    }

}
