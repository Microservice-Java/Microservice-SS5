package com.vietmart.gateway.config;

import com.vietmart.loadbalancer.OrderServiceZoneConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Configuration;

/**
 * Lớp cấu hình đăng ký LoadBalancer Clients cho Gateway.
 * Sử dụng @LoadBalancerClient để chỉ định lớp cấu hình OrderServiceZoneConfig áp dụng CHỈ DÀNH RIÊNG cho "order-service".
 */
@Configuration
@LoadBalancerClient(name = "order-service", configuration = OrderServiceZoneConfig.class)
public class GatewayLoadBalancerConfig {
}
