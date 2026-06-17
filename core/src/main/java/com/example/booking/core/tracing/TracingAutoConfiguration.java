package com.example.booking.core.tracing;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationRegistryCustomizer;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.handler.DefaultTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingReceiverTracingObservationHandler;
import io.micrometer.tracing.handler.PropagatingSenderTracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelPropagator;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({OpenTelemetry.class, OtelTracer.class})
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry openTelemetry(
            @Value("${spring.application.name:application}") String appName,
            @Value("${management.otlp.tracing.endpoint:http://localhost:9411/v1/traces}") String endpoint) {

        ZipkinSpanExporter exporter = ZipkinSpanExporter.builder()
                .setEndpoint(endpoint)
                .build();

        Resource resource = Resource.getDefault().toBuilder()
                .put(AttributeKey.stringKey("service.name"), appName)
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setSampler(Sampler.alwaysOn())
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public OtelCurrentTraceContext otelCurrentTraceContext() {
        return new OtelCurrentTraceContext();
    }

    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public OtelTracer otelTracer(OpenTelemetry openTelemetry, OtelCurrentTraceContext currentTraceContext) {
        return new OtelTracer(
                openTelemetry.getTracer("io.micrometer.micrometer-tracing"),
                currentTraceContext,
                event -> {}
        );
    }

    @Bean
    public ObservationRegistryCustomizer<ObservationRegistry> tracingObservationHandlerCustomizer(
            Tracer tracer, OpenTelemetry openTelemetry) {

        OtelPropagator propagator = new OtelPropagator(
                openTelemetry.getPropagators(),
                openTelemetry.getTracer("io.micrometer.micrometer-tracing")
        );

        return registry -> registry.observationConfig()
                .observationHandler(new PropagatingReceiverTracingObservationHandler<>(tracer, propagator))
                .observationHandler(new PropagatingSenderTracingObservationHandler<>(tracer, propagator))
                .observationHandler(new DefaultTracingObservationHandler(tracer));
    }

}
