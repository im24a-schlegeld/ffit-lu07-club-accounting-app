package ch.bzz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountingApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String projectName;
    private String token;

    @BeforeEach
    void createProjectAndLogin() throws Exception {
        projectName = "club-" + UUID.randomUUID();
        String request = objectMapper.writeValueAsString(
                Map.of("project_name", projectName, "password", "secret"));

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        token = json.get("access_token").asText();
        assertThat(token).isNotBlank();
    }

    @Test
    void helloEndpointRemainsAvailable() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World!"));
    }

    @Test
    void accountAndBookingDataAreScopedToAuthenticatedProject() throws Exception {
        String authorization = "Bearer " + token;
        mockMvc.perform(put("/accounts")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accounts":[
                                  {"number":1000,"name":"Kasse"},
                                  {"number":1020,"name":"Bank"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/bookings")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[{
                                  "date":"2025-10-21",
                                  "text":"Bargeld abgehoben",
                                  "debit":1000,
                                  "credit":1020,
                                  "amount":200.00
                                }]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/accounts").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1000))
                .andExpect(jsonPath("$[1].name").value("Bank"));

        mockMvc.perform(get("/bookings").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value(1))
                .andExpect(jsonPath("$[0].debit").value(1000))
                .andExpect(jsonPath("$[0].amount").value(200.0));

        mockMvc.perform(put("/accounts")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accounts":[{"number":1000,"name":"Bargeld"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/bookings")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[{"id":1,"text":"Aktualisierte Buchung"}]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/accounts").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Bargeld"));
        mockMvc.perform(get("/bookings").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].text").value("Aktualisierte Buchung"));

        String otherProjectRequest = objectMapper.writeValueAsString(
                Map.of("project_name", projectName + "-other", "password", "secret"));
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherProjectRequest))
                .andExpect(status().isCreated());
        String otherLoginResponse = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(otherProjectRequest))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String otherToken = objectMapper.readTree(otherLoginResponse).get("access_token").asText();

        mockMvc.perform(get("/accounts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsInvalidCredentialsAndMissingBearerToken() throws Exception {
        String wrongPassword = objectMapper.writeValueAsString(
                Map.of("project_name", projectName, "password", "wrong"));
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPassword))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/bookings").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidProjectAndBookingUpdatesWithoutPartialChanges() throws Exception {
        String authorization = "Bearer " + token;
        String duplicateProject = objectMapper.writeValueAsString(
                Map.of("project_name", projectName, "password", "another-secret"));
        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateProject))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/accounts")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accounts":[
                                  {"number":1000,"name":"Kasse"},
                                  {"number":1020,"name":"Bank"}
                                ]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/bookings")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"entries":[
                                  {"date":"2025-10-21","text":"Valid","debit":1000,"credit":1020,"amount":10},
                                  {"date":"2025-10-22","text":"Invalid account","debit":9999,"credit":1020,"amount":20}
                                ]}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/bookings").header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
