package com.rikkei.microservice.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rikkei.microservice.identity.dto.LoginRequest;
import com.rikkei.microservice.identity.dto.RegisterRequest;
import com.rikkei.microservice.identity.repository.UserRepository;
import com.rikkei.microservice.identity.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Đăng nhập thành công trả về 200 OK và JWT Token hợp lệ, không tạo Session")
    void testLoginSuccessReturnsJwtTokenAndNoSession() throws Exception {
        // 1. Đăng ký tài khoản trước
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("testuser")
                .email("testuser@example.com")
                .password("password123")
                .roles(Set.of("USER"))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // 2. Đăng nhập đúng thông tin
        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser")
                .password("password123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(header().doesNotExist("Set-Cookie")) // Xác nhận không sinh session cookie JSESSIONID
                .andReturn();

        // 3. Kiểm tra Session không tồn tại trong request/response
        assertThat(result.getRequest().getSession(false)).isNull();

        // 4. Kiểm tra Access Token hợp lệ qua JwtUtils
        String responseContent = result.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(responseContent).get("accessToken").asText();

        assertThat(jwtUtils.validateJwtToken(accessToken)).isTrue();
        assertThat(jwtUtils.getUsernameFromJwtToken(accessToken)).isEqualTo("testuser");

        // 5. Sử dụng Access Token để truy cập endpoint được bảo vệ
        mockMvc.perform(get("/api/test/user")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Đăng nhập sai mật khẩu trả về 401 Unauthorized kèm thông báo Bad credentials")
    void testLoginWithWrongPasswordReturns401Unauthorized() throws Exception {
        RegisterRequest registerRequest = RegisterRequest.builder()
                .username("testuser2")
                .email("testuser2@example.com")
                .password("correct_password")
                .roles(Set.of("USER"))
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        LoginRequest loginRequest = LoginRequest.builder()
                .username("testuser2")
                .password("wrong_password")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }

    @Test
    @DisplayName("Đăng nhập với username không tồn tại trả về 401 Unauthorized kèm thông báo Bad credentials")
    void testLoginWithNonExistentUserReturns401Unauthorized() throws Exception {
        LoginRequest loginRequest = LoginRequest.builder()
                .username("non_existent_user")
                .password("any_password")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Bad credentials"));
    }
}
