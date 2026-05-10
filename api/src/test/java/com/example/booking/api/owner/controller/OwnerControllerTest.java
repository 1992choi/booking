package com.example.booking.api.owner.controller;

import com.example.booking.api.auth.dto.LoginRequest;
import com.example.booking.api.auth.dto.SignupRequest;
import com.example.booking.api.auth.dto.TokenResponse;
import com.example.booking.api.owner.domain.OwnerType;
import com.example.booking.api.owner.dto.OwnerCreateRequest;
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

import com.example.booking.api.resource.domain.ResourceRepository;
import com.example.booking.api.resource.dto.ResourceCreateRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
class OwnerControllerTest {

    @Autowired
    WebApplicationContext wac;

    @Autowired
    FilterChainProxy springSecurityFilterChain;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ResourceRepository resourceRepository;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void register_success() throws Exception {
        String token = signupAndLogin("owner1@example.com");

        OwnerCreateRequest request = new OwnerCreateRequest(
                "Sunset Pension",
                "02-1111-2222",
                OwnerType.PENSION
        );

        mockMvc.perform(post("/api/v1/owners")
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

        Long userId = userRepository.findByEmail("owner1@example.com").orElseThrow().getId();
        assertThat(userRepository.findById(userId).orElseThrow().getRole().name()).isEqualTo("OWNER");
    }

    @Test
    void register_already_exists() throws Exception {
        String token = signupAndLogin("owner2@example.com");

        OwnerCreateRequest request = new OwnerCreateRequest(
                "First Pension",
                "02-1111-2222",
                OwnerType.PENSION
        );
        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        OwnerCreateRequest second = new OwnerCreateRequest(
                "Second Class",
                "02-3333-4444",
                OwnerType.CLASS
        );
        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_003"));
    }

    @Test
    void register_unauthorized() throws Exception {
        OwnerCreateRequest request = new OwnerCreateRequest(
                "No Auth",
                "02-9999-9999",
                OwnerType.PENSION
        );

        mockMvc.perform(post("/api/v1/owners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void register_invalid_token() throws Exception {
        String validToken = signupAndLogin("owner_invalid@example.com");
        String corruptedToken = validToken + "tampered";

        OwnerCreateRequest request = new OwnerCreateRequest(
                "Invalid Token",
                "02-0000-0000",
                OwnerType.PENSION
        );

        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + corruptedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("AUTH_001"));
    }

    @Test
    void getMyOwner_success() throws Exception {
        String token = signupAndLogin("owner3@example.com");

        OwnerCreateRequest request = new OwnerCreateRequest(
                "My Class",
                "02-5555-6666",
                OwnerType.CLASS
        );
        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/owners/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Class"))
                .andExpect(jsonPath("$.type").value("CLASS"));
    }

    @Test
    void getMyOwner_not_found() throws Exception {
        String token = signupAndLogin("owner4@example.com");

        mockMvc.perform(get("/api/v1/owners/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_004"));
    }

    @Test
    void getOwners_success() throws Exception {
        String token1 = signupAndLogin("owner7@example.com");
        String token2 = signupAndLogin("owner8@example.com");

        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OwnerCreateRequest("Pension A", "02-1111-1111", OwnerType.PENSION))))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OwnerCreateRequest("Class B", "02-2222-2222", OwnerType.CLASS))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/owners")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Pension A')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Class B')]").exists());
    }

    @Test
    void getOwner_success() throws Exception {
        String token = signupAndLogin("owner5@example.com");

        String ownerResponse = mockMvc.perform(post("/api/v1/owners")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new OwnerCreateRequest("Beach Pension", "02-7777-8888", OwnerType.PENSION))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long ownerId = objectMapper.readTree(ownerResponse).get("id").asLong();

        ResourceCreateRequest resource = new ResourceCreateRequest("별채 A", "2인실", 100000L, 2);
        mockMvc.perform(post("/api/v1/owners/{ownerId}/resources", ownerId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resource)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/owners/{ownerId}", ownerId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerId))
                .andExpect(jsonPath("$.name").value("Beach Pension"))
                .andExpect(jsonPath("$.type").value("PENSION"))
                .andExpect(jsonPath("$.resources.length()").value(1))
                .andExpect(jsonPath("$.resources[0].name").value("별채 A"));
    }

    @Test
    void getOwner_not_found() throws Exception {
        String token = signupAndLogin("owner6@example.com");

        mockMvc.perform(get("/api/v1/owners/{ownerId}", 9999L)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("API_004"));
    }

    private String signupAndLogin(String email) throws Exception {
        SignupRequest signup = new SignupRequest("Owner", email, "010-1234-5678", "password123");
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
