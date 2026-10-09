package com.example.booking.core.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.handler.TracingObservationHandler;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.filter.ServerHttpObservationFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class RequestLoggingFilterTest {

    RequestLoggingFilter filter = new RequestLoggingFilter();
    Logger logbackLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void setUp() {
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logbackLogger.detachAppender(appender);
        MDC.clear();
    }

    @Test
    @DisplayName("Observation 컨텍스트에 스팬이 있으면 로그 이벤트의 MDC에 traceId/spanId가 담긴다")
    void doFilter_withTracingContext_putsTraceAndSpanIdsOnLogEvent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        setObservationSpan(request, response, "trace-123", "span-456");

        FilterChain chain = (req, res) -> {};
        filter.doFilterInternal(request, response, chain);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).containsEntry("traceId", "trace-123");
        assertThat(event.getMDCPropertyMap()).containsEntry("spanId", "span-456");
    }

    @Test
    @DisplayName("로깅 이후 MDC는 다음 요청에 영향을 주지 않도록 정리된다")
    void doFilter_withTracingContext_clearsMdcAfterLogging() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        setObservationSpan(request, response, "trace-123", "span-456");

        FilterChain chain = (req, res) -> {};
        filter.doFilterInternal(request, response, chain);

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }

    @Test
    @DisplayName("Observation 컨텍스트가 없으면(트레이싱 미설정 서비스) 예외 없이 로그만 남긴다")
    void doFilter_withoutObservationContext_logsWithoutTraceIds() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        FilterChain chain = (req, res) -> {};
        filter.doFilterInternal(request, response, chain);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("traceId");
        assertThat(event.getMDCPropertyMap()).doesNotContainKey("spanId");
    }

    @Test
    @DisplayName("체인에서 예외가 발생해도(인증 실패 등) 요청은 로깅된다")
    void doFilter_whenChainThrows_stillLogsBeforeRethrowing() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/merchants");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        FilterChain chain = (req, res) -> {
            throw new RuntimeException("auth failure");
        };

        try {
            filter.doFilterInternal(request, response, chain);
        } catch (Exception ignored) {
            // 체인 예외는 재전파되는 게 맞다 — 이 테스트는 "그래도 로깅은 됐는지"만 확인한다
        }

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("POST /api/v1/merchants -> 401");
    }

    private void setObservationSpan(MockHttpServletRequest request, MockHttpServletResponse response,
                                     String traceId, String spanId) {
        ServerRequestObservationContext observationContext = new ServerRequestObservationContext(request, response);

        TraceContext traceContext = mock(TraceContext.class);
        given(traceContext.traceId()).willReturn(traceId);
        given(traceContext.spanId()).willReturn(spanId);

        Span span = mock(Span.class);
        given(span.context()).willReturn(traceContext);

        TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
        tracingContext.setSpan(span);
        observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);

        request.setAttribute(ServerHttpObservationFilter.CURRENT_OBSERVATION_CONTEXT_ATTRIBUTE, observationContext);
    }

}
