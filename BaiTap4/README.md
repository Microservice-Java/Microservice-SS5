# SPRING-CLOUD-S05-EX04: Phân Tích Sự Cố Cấu Hình Zone Preference Bị Áp Dụng Nhầm Toàn Hệ Thống

Báo cáo phân tích nguyên nhân gốc rễ (Root Cause Analysis - RCA) và đề xuất kiến trúc package chuẩn cho Spring Cloud LoadBalancer trong ứng dụng API Gateway của VietMart.

---

## 1. Phân Tích Nguyên Nhân Gốc Rễ (Root Cause Analysis - RCA)

### 1.1. Tình huống sự cố
Sau khi cấu hình Zone Preference cho `order-service`, dịch vụ `user-service` (không liên quan tới Zone Hà Nội) bất ngờ bị ảnh hưởng: Toàn bộ request gọi tới `user-service` bị dồn hết vào 1 trong 2 instance thuộc zone HCM, bỏ qua hoàn toàn 2 instance thuộc zone Hà Nội.

### 1.2. Nguyên nhân cốt lõi (Root Cause)

#### 1. Cơ Chế Spring Component Scanning
Main class `ApiGatewayApplication.java` được đặt tại package `com.vietmart.gateway` và mang annotation `@SpringBootApplication`.
Mặc định, `@SpringBootApplication` kích hoạt `@ComponentScan` quét **toàn bộ các lớp và package con** nằm bên dưới `com.vietmart.gateway` (bao gồm cả package `com.vietmart.gateway.loadbalancer`).

#### 2. Việc Khai Báo `@Configuration` Trong Vùng Quét
Lớp `ZonePreferenceConfig.java` được đặt tại `com.vietmart.gateway.loadbalancer` và mang annotation `@Configuration`:
```java
package com.vietmart.gateway.loadbalancer;

@Configuration
public class ZonePreferenceConfig {
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
```
Do nằm trong vùng quét của `@ComponentScan`, Spring Boot khởi tạo lớp này ngay từ bước khởi động và đăng ký Bean `ServiceInstanceListSupplier` (mang cấu hình Zone Preference) trực tiếp vào **Root Application Context (Parent Context)** của ứng dụng API Gateway.

#### 3. Mô Hình Khai Thác Container Phân Cấp Trong Spring Cloud LoadBalancer (`NamedContextFactory`)
- Spring Cloud LoadBalancer khởi tạo một **Child Application Context (Context con)** riêng biệt cho mỗi service-id khi thực hiện load balancing (ví dụ: context riêng cho `order-service`, context riêng cho `user-service`).
- Khi một Child Context tìm kiếm Bean định cấu hình LoadBalancer (như `ServiceInstanceListSupplier`), nếu Child Context đó không tự khai báo Bean riêng, nó sẽ thực hiện cơ chế **Fallback (kế thừa)** tìm kiếm lên **Root Application Context (Context cha)**.
- **Hệ quả**: Do Bean `ServiceInstanceListSupplier` (Zone Preference) đã bị đăng ký nhầm vào Root Context, nên mọi microservice client khi được gọi qua Gateway (bao gồm `user-service`, `product-service`, `voucher-service`) chưa tự khai báo cấu hình riêng đều bị bắt buộc kế thừa và áp dụng Zone Preference toàn cục!

---

## 2. Giải Pháp Khắc Phục & Đề Xuất Cấu Trúc Package Đúng

### 2.1. Đề xuất quy tắc khắc phục
Để một cấu hình LoadBalancer chỉ áp dụng riêng cho 1 service chỉ định:
1. **Loại bỏ `@Configuration` khỏi lớp cấu hình chi tiết**, HOẶC
2. **Đưa lớp cấu hình ra NẰM NGOÀI vùng quét Component Scan** của main application class.

### 2.2. Đề xuất cấu trúc Package sửa lỗi cho `order-service`

```text
d:\microservice\BaiTap\SS5\BaiTap4\api-gateway\src\main\java
│
├── com.vietmart.gateway                       <-- Package chính (được @ComponentScan quét)
│   ├── ApiGatewayApplication.java             
│   └── config/
│       └── GatewayLoadBalancerConfig.java     <-- Khai báo @LoadBalancerClient chỉ định
│
└── com.vietmart.loadbalancer                  <-- Package NẰM NGOÀI vùng quét của Gateway
    └── OrderServiceZoneConfig.java            <-- Cấu hình Zone Preference riêng cho order-service
```

#### Mã nguồn `GatewayLoadBalancerConfig.java`:
```java
package com.vietmart.gateway.config;

import com.vietmart.loadbalancer.OrderServiceZoneConfig;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClient(name = "order-service", configuration = OrderServiceZoneConfig.class)
public class GatewayLoadBalancerConfig {
}
```

---

## 3. Kiến Trúc Tổ Chức Package Cho Hệ Thống Nhiều Service (5+ Services)

Khi VietMart mở rộng hệ thống với 5+ Microservices áp dụng các chiến lược LoadBalancer khác nhau, kiến trúc package được thiết kế theo dạng **Modular Out-of-Scan Client Configuration**:

```text
com.vietmart.gateway                            <-- Root Package (Application Context Chính)
  ├── ApiGatewayApplication.java
  └── config/
        └── LoadBalancerClientConfig.java      <-- Đăng ký tập trung các client mapping

com.vietmart.loadbalancer                       <-- Client LoadBalancer Package (Tách biệt hoàn toàn)
  ├── order/
  │     └── OrderServiceZoneConfig.java        <-- Zone Preference riêng cho Order Service
  ├── product/
  │     └── ProductServiceRandomConfig.java    <-- Random LoadBalancer riêng cho Product Service
  ├── user/
  │     └── UserServiceWeightedConfig.java      <-- Weighted LoadBalancer riêng cho User Service
  ├── voucher/
  │     └── VoucherServiceLeastConnConfig.java  <-- Least Connections riêng cho Voucher Service
  └── payment/
        └── PaymentServiceCustomConfig.java    <-- Custom Algorithm riêng cho Payment Service
```

### Lớp đăng ký tập trung `LoadBalancerClientConfig.java`:
```java
package com.vietmart.gateway.config;

import com.vietmart.loadbalancer.order.OrderServiceZoneConfig;
import com.vietmart.loadbalancer.product.ProductServiceRandomConfig;
import com.vietmart.loadbalancer.user.UserServiceWeightedConfig;
import com.vietmart.loadbalancer.voucher.VoucherServiceLeastConnConfig;
import com.vietmart.loadbalancer.payment.PaymentServiceCustomConfig;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClients({
    @LoadBalancerClient(name = "order-service", configuration = OrderServiceZoneConfig.class),
    @LoadBalancerClient(name = "product-service", configuration = ProductServiceRandomConfig.class),
    @LoadBalancerClient(name = "user-service", configuration = UserServiceWeightedConfig.class),
    @LoadBalancerClient(name = "voucher-service", configuration = VoucherServiceLeastConnConfig.class),
    @LoadBalancerClient(name = "payment-service", configuration = PaymentServiceCustomConfig.class)
})
public class LoadBalancerClientConfig {
}
```

### Lợi ích của kiến trúc đề xuất:
1. **Cách ly ngữ cảnh (Context Isolation)**: Đảm bảo các cấu hình LoadBalancer client không bị lọt vào Root Context, giải quyết triệt để lỗi Global Configuration Bleed.
2. **Dễ đọc & Dễ quản lý**: Mỗi microservice có một thư mục riêng biệt cho chiến lược cân bằng tải của mình.
3. **Mở rộng linh hoạt (Scalable)**: Dễ dàng thêm, bớt hoặc điều chỉnh chiến lược LoadBalancer cho bất kỳ service nào mà không sợ làm ảnh hưởng tới các service khác.
