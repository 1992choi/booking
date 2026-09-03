package com.example.booking.payment.config;

import com.example.booking.pg.grpc.PgServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "booking.pg.protocol", havingValue = "grpc")
public class PgGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel pgGrpcChannel(
            @Value("${booking.pg.grpc.host}") String host,
            @Value("${booking.pg.grpc.port}") int port) {

        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public PgServiceGrpc.PgServiceBlockingStub pgServiceBlockingStub(ManagedChannel pgGrpcChannel) {
        return PgServiceGrpc.newBlockingStub(pgGrpcChannel);
    }

}
