package com.example.booking.api.auth.filter;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginRateLimitFilterTest {

    @Mock
    ProxyManager<String> proxyManager;

    @Mock
    @SuppressWarnings("rawtypes")
    RemoteBucketBuilder remoteBucketBuilder;

    @Mock
    BucketProxy bucket;

    @Mock
    FilterChain chain;

    LoginRateLimitFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        filter = new LoginRateLimitFilter(proxyManager, new ObjectMapper());
        lenient().when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        lenient().when(remoteBucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucket);
    }

    @Test
    @DisplayName("토큰이 남아있으면 요청을 통과시킨다")
    void tokenAvailable_passThrough() throws Exception {
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe consumed = mock(ConsumptionProbe.class);
        when(consumed.isConsumed()).thenReturn(true);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumed);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("토큰이 소진되면 429를 반환한다")
    void tokenExhausted_returns429() throws Exception {
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(TimeUnit.SECONDS.toNanos(30));
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentType()).contains("application/problem+json");
        assertThat(response.getHeader("Retry-After")).isEqualTo("31");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("초과 응답 바디에 에러코드가 포함된다")
    void tokenExhausted_responseBodyContainsErrorCode() throws Exception {
        MockHttpServletRequest request = loginRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe probe = mock(ConsumptionProbe.class);
        when(probe.isConsumed()).thenReturn(false);
        when(probe.getNanosToWaitForRefill()).thenReturn(0L);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(probe);

        filter.doFilterInternal(request, response, chain);

        String body = response.getContentAsString();
        assertThat(body).contains("API_003");
        assertThat(body).contains("요청이 너무 많습니다");
    }

    @Test
    @DisplayName("로그인 외 경로는 필터를 통과한다")
    void nonLoginPath_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(proxyManager, never()).builder();
    }

    @Test
    @DisplayName("GET 로그인 경로는 필터를 통과한다")
    void getMethodOnLoginPath_passThrough() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(proxyManager, never()).builder();
    }

    @Test
    @DisplayName("X-Forwarded-For 헤더로 IP를 추출한다")
    @SuppressWarnings("unchecked")
    void xForwardedForHeader_extractsClientIp() throws Exception {
        MockHttpServletRequest request = loginRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ConsumptionProbe consumed = mock(ConsumptionProbe.class);
        when(consumed.isConsumed()).thenReturn(true);
        when(bucket.tryConsumeAndReturnRemaining(1)).thenReturn(consumed);

        filter.doFilterInternal(request, response, chain);

        verify(remoteBucketBuilder).build(eq("rl:login:203.0.113.1"), any(Supplier.class));
    }

    private MockHttpServletRequest loginRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/login");
    }

}
