package com.example.booking.api.auth.filter;

import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
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
    void 토큰이_남아있으면_요청을_통과시킨다() throws Exception {
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
    void 토큰이_소진되면_429를_반환한다() throws Exception {
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
    void 초과_응답_바디에_에러코드가_포함된다() throws Exception {
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
    void 로그인_외_경로는_필터를_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/signup");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(proxyManager, never()).builder();
    }

    @Test
    void GET_로그인_경로는_필터를_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(proxyManager, never()).builder();
    }

    @Test
    @SuppressWarnings("unchecked")
    void X_Forwarded_For_헤더로_IP를_추출한다() throws Exception {
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
