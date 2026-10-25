package com.example.booking.notification.internal;

import com.example.booking.notification.internal.dto.DltReplayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DltReplayController {

    private final DltReplayService dltReplayService;

    @PostMapping("/api/v1/internal/dlt/{dltTopic}/replay")
    public DltReplayResponse replay(@PathVariable String dltTopic) {
        int replayedCount = dltReplayService.replay(dltTopic);

        return new DltReplayResponse(dltTopic, replayedCount);
    }

}
