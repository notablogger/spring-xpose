package io.github.springxpose.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringXposeTest
class RestNotFoundTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void getByIdNotFound_returns404() throws Exception {
        mockMvc.perform(get("/api/products/99999"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getByIdNotFound_category_returns404() throws Exception {
        mockMvc.perform(get("/api/categories/99999"))
            .andExpect(status().isNotFound());
    }
}

