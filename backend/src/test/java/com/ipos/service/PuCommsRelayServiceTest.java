package com.ipos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.config.IntegrationPuProperties;
import com.ipos.security.IntegrationPuInboundApiKeyFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

class PuCommsRelayServiceTest {

    @Test
    void relayEmail_postsJsonWithIntegrationHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();

        server.expect(requestTo("http://localhost:8082/api/integration-sa/relay-email"))
                .andExpect(method(POST))
                .andExpect(header(IntegrationPuInboundApiKeyFilter.INTEGRATION_KEY_HEADER, "k"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json(
                        "{\"to\":\"a@b.com\",\"subject\":\"Subj\",\"body\":\"Hi\"}", false))
                .andRespond(withNoContent());

        IntegrationPuProperties props = new IntegrationPuProperties();
        PuCommsRelayService svc = new PuCommsRelayService(props, client, new ObjectMapper());
        svc.relayEmail("a@b.com", "Subj", "Hi", "http://localhost:8082", "k");

        server.verify();
    }
}
