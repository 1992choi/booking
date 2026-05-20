package com.example.booking.api.merchant.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.merchant.domain.MerchantType;
import com.example.booking.api.merchant.dto.MerchantCreateRequest;
import com.example.booking.api.merchant.dto.MerchantUpdateRequest;
import com.example.booking.api.resource.dto.ResourceCreateRequest;
import com.example.booking.api.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class MerchantControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void register_success() throws Exception {
        String token = signupAndLogin("merchant1@example.com");

        MerchantCreateRequest request = new MerchantCreateRequest(
                "Sunset Pension", "02-1111-2222", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.name").value("Sunset Pension"))
                .andExpect(jsonPath("$.phone").value("02-1111-2222"))
                .andExpect(jsonPath("$.type").value("PENSION"))
                .andExpect(jsonPath("$.createdAt").exists());

        Long userId = userRepository.findByEmail("merchant1@example.com").orElseThrow().getId();
        assertThat(userRepository.findById(userId).orElseThrow().getRole().name()).isEqualTo("MERCHANT");
    }

    @Test
    void register_allows_multiple() throws Exception {
        String token = signupAndLogin("merchant2@example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("First Pension", "02-1111-2222", MerchantType.PENSION))))
                .andExpect(status().isCreated());

        // 1:N relationship — same user can register multiple merchants
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Second Class", "02-3333-4444", MerchantType.CLASS))))
                .andExpect(status().isCreated());
    }

    @Test
    void register_unauthorized() throws Exception {
        MerchantCreateRequest request = new MerchantCreateRequest(
                "No Auth", "02-9999-9999", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void register_invalid_token() throws Exception {
        String validToken = signupAndLogin("merchant_invalid@example.com");
        String corruptedToken = validToken + "tampered";

        MerchantCreateRequest request = new MerchantCreateRequest(
                "Invalid Token", "02-0000-0000", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + corruptedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void getMyMerchants_success() throws Exception {
        String token = signupAndLogin("merchant3@example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("My Class", "02-5555-6666", MerchantType.CLASS))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("My Class"))
                .andExpect(jsonPath("$[0].type").value("CLASS"));
    }

    @Test
    void getMyMerchants_empty() throws Exception {
        String token = signupAndLogin("merchant4@example.com");

        mockMvc.perform(get("/api/v1/merchants/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getMerchants_success() throws Exception {
        String token1 = signupAndLogin("merchant7@example.com");
        String token2 = signupAndLogin("merchant8@example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Pension A", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Class B", "02-2222-2222", MerchantType.CLASS))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Pension A')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Class B')]").exists());
    }

    @Test
    void getMerchant_success() throws Exception {
        String token = signupAndLogin("merchant5@example.com");

        String merchantResponse = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Beach Pension", "02-7777-8888", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(merchantResponse).get("id").asLong();

        ResourceCreateRequest resource = new ResourceCreateRequest("별채 A", "2인실", 100000L, 2);
        mockMvc.perform(post("/api/v1/merchants/{merchantId}/resources", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId))
                .andExpect(jsonPath("$.name").value("Beach Pension"))
                .andExpect(jsonPath("$.type").value("PENSION"))
                .andExpect(jsonPath("$.resources.length()").value(1))
                .andExpect(jsonPath("$.resources[0].name").value("별채 A"));
    }

    @Test
    void getMerchant_not_found() throws Exception {
        String token = signupAndLogin("merchant6@example.com");

        mockMvc.perform(get("/api/v1/merchants/{merchantId}", 9999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_003"));
    }

    @Test
    void update_success() throws Exception {
        String token = signupAndLogin("merchant_update1@example.com");
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Original Name", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(response).get("id").asLong();

        MerchantUpdateRequest update = new MerchantUpdateRequest("Updated Name", "02-9999-9999", MerchantType.CLASS);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("02-9999-9999"))
                .andExpect(jsonPath("$.type").value("CLASS"));
    }

    @Test
    void update_notFound() throws Exception {
        String token = signupAndLogin("merchant_update2@example.com");

        MerchantUpdateRequest update = new MerchantUpdateRequest("Name", "02-1111-1111", MerchantType.PENSION);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", 9999L)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("API_003"));
    }

    @Test
    void update_forbidden() throws Exception {
        String merchantToken = signupAndLogin("merchant_update3@example.com");
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + merchantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Other's Merchant", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(response).get("id").asLong();

        String otherToken = signupAndLogin("merchant_update4@example.com");
        MerchantUpdateRequest update = new MerchantUpdateRequest("Hijack", "02-0000-0000", MerchantType.CLASS);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_invalidInput_blankName() throws Exception {
        String token = signupAndLogin("merchant_val1@example.com");

        MerchantCreateRequest request = new MerchantCreateRequest("", "02-1111-2222", MerchantType.PENSION);

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void register_invalidInput_nullType() throws Exception {
        String token = signupAndLogin("merchant_val2@example.com");

        mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Valid Name\",\"phone\":\"02-1111-2222\",\"type\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    void getMerchant_unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/merchants/{merchantId}", 1L))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void update_invalidInput_blankName() throws Exception {
        String token = signupAndLogin("merchant_val3@example.com");
        String response = mockMvc.perform(post("/api/v1/merchants")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new MerchantCreateRequest("Original", "02-1111-1111", MerchantType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long merchantId = objectMapper.readTree(response).get("id").asLong();

        MerchantUpdateRequest update = new MerchantUpdateRequest("", "02-9999-9999", MerchantType.CLASS);
        mockMvc.perform(put("/api/v1/merchants/{merchantId}", merchantId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    private String signupAndLogin(String email) throws Exception {
        SignupRequest signup = new SignupRequest("User", email, "010-1234-5678", "password123");
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest(email, "password123");
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(response, TokenResponse.class).accessToken();
    }
}
