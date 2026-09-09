package com.vietmart.gateway.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClient(name = "product-service", configuration = RandomLoadBalancerConfig.class)
public class ProductServiceLoadBalancerConfig {
}
