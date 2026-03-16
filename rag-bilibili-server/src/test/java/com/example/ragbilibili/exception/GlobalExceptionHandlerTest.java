package com.example.ragbilibili.exception;

import com.example.ragbilibili.controller.AuthController;
import com.example.ragbilibili.dto.request.RegisterRequest;
import com.example.ragbilibili.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证 GlobalExceptionHandler 对 DuplicateKeyException 的处理
 */
@WebMvcTest(AuthController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void testDuplicateKeyExceptionReturnsUserAlreadyExists() throws Exception {
        // Mock UserService.register() 抛出 DuplicateKeyException（模拟数据库唯一键冲突）
        when(userService.register(any())).thenThrow(
                new DuplicateKeyException("Duplicate entry 'testuser' for key 'username'"));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    void testUnsupportedMediaTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message").value("小网站求你们别测试了 (っ °Д °;)っ"));
    }
}
