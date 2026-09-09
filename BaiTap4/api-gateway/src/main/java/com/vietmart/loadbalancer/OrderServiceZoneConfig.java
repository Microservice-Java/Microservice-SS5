package com.vietmart.loadbalancer;

import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Lớp cấu hình Zone Preference dành riêng cho Order Service.
 * CHÚ Ý: Đặt tại package com.vietmart.loadbalancer (nằm OUTSIDE package com.vietmart.gateway)
 * để tránh bị @SpringBootApplication ComponentScan tự động nhặt vào Root Context gây lỗi áp dụng toàn cục.
 */
@Configuration
public class OrderServiceZoneConfig {

    @Bean
    public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        return ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .withZonePreference()
                .withHealthChecks()
                .build(context);
    }
}
