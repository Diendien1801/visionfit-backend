package com.visionfit.identity.exception;

import com.visionfit.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AuthService authService;

    @Test
    void aBodyThatIsNotJsonIsTheCallersMistake() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anUnexpectedFailureLeavesItsStackTraceInTheLog(CapturedOutput output) throws Exception {
        when(authService.login(any())).thenThrow(new IllegalStateException("database is down"));

        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"an@example.com\",\"password\":\"secret\"}"))
                .andExpect(status().isInternalServerError());

        assertTrue(output.getOut().contains("java.lang.IllegalStateException: database is down"));
    }
}
