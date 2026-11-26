package de.starwit.application.config;

import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.starwit.service.ConnectionCheckService;

/**
 * Reports the last connection checks as the components cronist and dave of the health check. They only read the last
 * result, the health check itself doesn't call the services.
 */
@Configuration
public class ConnectionHealthConfig {

    @Bean
    HealthIndicator cronistHealthIndicator(ConnectionCheckService checks) {
        return () -> checks.health(ConnectionCheckService.CRONIST);
    }

    @Bean
    HealthIndicator daveHealthIndicator(ConnectionCheckService checks) {
        return () -> checks.health(ConnectionCheckService.DAVE);
    }
}
