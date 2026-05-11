package com.example.ragbilibili.config;

import com.example.ragbilibili.auth.AuthSessionManager;
import com.example.ragbilibili.controller.AuthController;
import com.example.ragbilibili.controller.VideoController;
import com.example.ragbilibili.dto.response.UserResponse;
import com.example.ragbilibili.service.UserService;
import com.example.ragbilibili.service.VideoService;
import cn.dev33.satoken.filter.SaTokenContextFilterForJakartaServlet;
import cn.dev33.satoken.spring.SaBeanInject;
import cn.dev33.satoken.spring.SaBeanRegister;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({VideoController.class, AuthController.class})
@Import({
        WebConfig.class,
        AuthSessionManager.class,
        SaBeanRegister.class,
        SaBeanInject.class,
        SaTokenContextForSpringInJakartaServlet.class,
        SaTokenContextFilterForJakartaServlet.class
})
class AuthInterceptorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VideoService videoService;

    @MockBean
    private UserService userService;

    @Test
    void accessProtectedEndpointWithoutTokenReturns1004() throws Exception {
        mockMvc.perform(get("/api/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void accessProtectedEndpointWithInvalidTokenReturns1004() throws Exception {
        mockMvc.perform(get("/api/videos")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1004))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void accessRegisterEndpointWithoutTokenIsNotIntercepted() throws Exception {
        UserResponse response = new UserResponse();
        response.setId(1L);
        response.setUsername("testuser");
        when(userService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }
}
