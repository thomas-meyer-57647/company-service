package de.innologic.companyservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdFilterIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void givenCorrelationIdHeader_returnsSameHeader() throws Exception {
        mockMvc.perform(get("/actuator/health")
                        .header("X-Correlation-Id", "test-correlation"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-Id", "test-correlation"));
    }

    @Test
    void whenHeaderMissing_responseGeneratesCorrelationIdAndBodyMatches() throws Exception {
        MvcResult result = mockMvc.perform(get("/companies/does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-Id");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("correlationId").asText()).isEqualTo(correlationId);
    }
}
