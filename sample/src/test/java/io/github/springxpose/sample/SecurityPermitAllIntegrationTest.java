package io.github.springxpose.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringXposeTest
class SecurityPermitAllIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // Products use AuthType.NONE in our sample, so no credentials needed
    @Test
    void findAll_noAuth_returns200() throws Exception {
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void findById_noAuth_returns404ForMissing() throws Exception {
        mockMvc.perform(get("/api/products/99999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_noAuth_withCsrf_returns201() throws Exception {
        mockMvc.perform(post("/api/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"NoAuthProduct\",\"price\":5.0}"))
            .andExpect(status().isCreated());
    }
}

