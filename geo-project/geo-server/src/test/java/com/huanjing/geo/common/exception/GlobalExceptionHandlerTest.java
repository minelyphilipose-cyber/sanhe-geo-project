package com.huanjing.geo.common.exception;

import com.huanjing.geo.common.result.R;
import com.huanjing.geo.module.presale.dto.request.LlmPromptQuestionGenerateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void invalidEnumMapKeyReturns400() throws Exception {
        mockMvc.perform(post("/test/llm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brandName": "A",
                                  "industry": "restaurant",
                                  "industryRole": "chain_brand",
                                  "region": "全国",
                                  "totalCount": 1,
                                  "categoryCounts": {"BAD_CATEGORY": 1}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @RestController
    static class TestController {
        @PostMapping("/test/llm")
        R<Void> create(@RequestBody LlmPromptQuestionGenerateRequest request) {
            return R.ok();
        }
    }
}
