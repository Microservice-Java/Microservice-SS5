# SPRING-CLOUD-S05-EX01: Sửa và bổ sung Route cơ bản cho API Gateway của VietMart

Dự án khắc phục lỗi định tuyến `UnknownHostException` và bổ sung route mới cho `voucher-service` trong ứng dụng Spring Cloud Gateway của VietMart.

---

## 1. Phân Tích Nguyên Nhân Lỗi `UnknownHostException`

### Dòng cấu hình gây ra lỗi:
```yaml
uri: http://product-service
```

### Nguyên nhân chi tiết:
1. **Giao thức `http://` chuẩn**: Khi khai báo `uri: http://product-service`, Spring Cloud Gateway sẽ coi `product-service` là một tên miền/hostname DNS trực tiếp của hệ thống mạng.
2. **Thất bại trong tra cứu DNS**: Gateway cố gắng thực hiện phân giải hostname DNS `product-service` sang địa chỉ IP thực tế thông qua trình phân giải DNS cục bộ của hệ điều hành. Do `product-service` không phải là một hostname DNS hợp lệ trong mạng, hệ thống ném ra ngoại lệ `java.net.UnknownHostException: product-service`.
3. **Thiếu liên kết với Eureka Registry**: Giao thức `http://` thuần túy bỏ qua trình quản lý Service Discovery (Eureka Server), dẫn đến việc Gateway không tra cứu được địa chỉ động của các Microservice instance đã đăng ký.

---

## 2. Giải Pháp Khắc Phục & Vai Trò Của Tiền Tố `lb://`

### Cấu hình đã sửa đổi:
```yaml
uri: lb://product-service
```

### Cơ chế hoạt động của `lb://`:
- **Kích hoạt Load Balancer**: Tiền tố `lb://` (Load Balancer) báo cho Spring Cloud Gateway sử dụng module **Spring Cloud LoadBalancer**.
- **Tích hợp Eureka Service Discovery**: Thay vì tra cứu DNS mạng, Gateway sử dụng tên service-id (`product-service`) để gửi yêu cầu truy vấn danh sách IP và Port khả dụng từ **Eureka Discovery Server Registry**.
- **Cân bằng tải động (Dynamic Load Balancing)**: Gateway nhận danh sách các instance đang ở trạng thái `UP`, áp dụng thuật toán cân bằng tải (ví dụ: Round Robin) để định tuyến request thành công đến instance thích hợp.

---

## 3. Cấu Hình Bổ Sung Route Cho `voucher-service`

Tạo route mới định tuyến các request khuyến mãi có tiền tố path `/api/vouchers/**` sang `voucher-service`:

```yaml
- id: voucher-service-route
  uri: lb://voucher-service
  predicates:
    - Path=/api/vouchers/**
```

---

## 4. File `application.yml` Hoàn Chỉnh (`api-gateway`)

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        # Route 1: Product Service
        - id: product-service-route
          uri: lb://product-service
          predicates:
            - Path=/api/products/**

        # Route 2: Voucher Service (Bổ sung mới)
        - id: voucher-service-route
          uri: lb://voucher-service
          predicates:
            - Path=/api/vouchers/**

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
```

---

## 5. Hướng Dẫn Khởi Chạy Và Kiểm Thử

### Thứ tự khởi chạy:
1. **Eureka Server** (`http://localhost:8761`)
2. **Product Service** (Service ID: `product-service`)
3. **Voucher Service** (Service ID: `voucher-service`)
4. **API Gateway** (Port: `8080`)

### Các URL kiểm thử qua API Gateway:
- **Product Request**: `GET http://localhost:8080/api/products/1` -> Định tuyến thành công sang `lb://product-service/api/products/1`
- **Voucher Request**: `GET http://localhost:8080/api/vouchers/summer2026` -> Định tuyến thành công sang `lb://voucher-service/api/vouchers/summer2026`
