package io.github.springxpose.sample;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringXposeTest
@DirtiesContext
class RestCrudIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void fullCrudLifecycle() throws Exception {
        // CREATE
        String createBody = """
            {"name":"TestProduct","price":19.99,"description":"A crud test product"}
            """;
        String response = mockMvc.perform(post("/api/products")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("TestProduct"))
            .andReturn().getResponse().getContentAsString();

        // Extract id from response
        int idStart = response.indexOf("\"id\":") + 5;
        int idEnd = response.indexOf(",", idStart);
        String id = response.substring(idStart, idEnd).trim();

        // GET by id
        mockMvc.perform(get("/api/products/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("TestProduct"));

        // GET all
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        // UPDATE
        String updateBody = """
            {"name":"UpdatedProduct","price":29.99,"description":"Updated"}
            """;
        mockMvc.perform(put("/api/products/" + id)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("UpdatedProduct"));

        // DELETE
        mockMvc.perform(delete("/api/products/" + id).with(csrf()))
            .andExpect(status().isNoContent());

        // Confirm gone
        mockMvc.perform(get("/api/products/" + id))
            .andExpect(status().isNotFound());
    }
}

