package com.ipos.config;

import com.ipos.security.IntegrationPuInboundApiKeyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IntegrationPuProperties.class)
public class IntegrationPuConfig {

    @Bean
    public IntegrationPuInboundApiKeyFilter integrationPuInboundApiKeyFilter(IntegrationPuProperties properties) {
        return new IntegrationPuInboundApiKeyFilter(properties);
    }

    @Bean
    public RestClient integrationPuRestClient() {
        return RestClient.create();
    }
}
