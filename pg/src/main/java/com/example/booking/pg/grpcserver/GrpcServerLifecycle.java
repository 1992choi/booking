package com.example.booking.pg.grpcserver;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class GrpcServerLifecycle implements SmartLifecycle {

    private final PgGrpcService pgGrpcService;

    @Value("${grpc.server.port}")
    private int port;

    private Server server;
    private volatile boolean running = false;

    @Override
    public void start() {
        try {
            server = ServerBuilder.forPort(port)
                    .addService(pgGrpcService)
                    .build()
                    .start();
            log.info("gRPC 서버 시작 port={}", port);
        } catch (IOException e) {
            throw new IllegalStateException("gRPC 서버 시작 실패", e);
        }

        running = true;
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            try {
                server.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

}
