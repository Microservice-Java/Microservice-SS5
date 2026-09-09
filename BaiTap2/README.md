# SPRING-CLOUD-S05-EX02: Sửa Lỗi Sai Tên Service-ID Trong Cấu Hình Route

Báo cáo phân tích nguyên nhân lỗi HTTP Status `503 Service Unavailable` và giải pháp khắc phục sự không nhất quán Service ID giữa Spring Cloud Gateway và Eureka Service Registry.

---

## 1. Phân Tích Nguyên Nhân Lỗi `503 Service Unavailable`

### Sự không nhất quán giữa hai cấu hình:

| Thành Phần | Giá Trị Cấu Hình | Mô Tả |
| :--- | :--- | :--- |
| **API Gateway Route** | `uri: lb://orders-service` | Sử dụng Service ID thừa ký tự `s` (`orders-service`) |
| **Order Service App** | `spring.application.name: order-service` | Tên đăng ký chính thức với Eureka là `order-service` |

### Quá trình tra cứu service qua Eureka & LoadBalancer:

1. **Khớp Predicate**: Request `GET /api/orders/123` đi qua Gateway và khớp với predicate `Path=/api/orders/**` của route `order-service-route`.
2. **Truy vấn Eureka Registry**: Gateway chuyển Service ID phía sau `lb://` (ở đây là `orders-service`) sang cho **Spring Cloud LoadBalancerClient** để tra cứu danh sách instances khả dụng từ Eureka Server.
3. **Không tìm thấy Instance (Empty Instance List)**: Eureka Server quét Service Registry nhưng chỉ có dịch vụ đăng ký dưới tên `ORDER-SERVICE`. Không có instance nào khớp với tên `ORDERS-SERVICE`.
4. **Trả về lỗi HTTP 503**: Do danh sách IP/Port trả về bị rỗng, Gateway không thể định tuyến request tới bất kỳ instance nào và lập tức ném ra phản hồi **HTTP 503 Service Unavailable** (Dịch vụ không khả dụng).

---

## 2. Mối Quan Hệ Giữa `uri: lb://...` Và `spring.application.name`

- **Tên đăng ký dịch vụ (Service ID)**: Thuộc tính `spring.application.name` trong file cấu hình của từng Microservice đóng vai trò định danh duy nhất (Service ID) khi service đó khởi chạy và gửi heartbeat lên Eureka Server.
- **Quy tắc khớp chính xác**: Chuỗi ký tự đứng sau giao thức `lb://` trong file `application.yml` của API Gateway phải khớp **chính xác 100%** với `spring.application.name` của microservice tương ứng (Eureka tự động chuyển sang chữ hoa nhưng không phân biệt hoa thường khi tra cứu).
- Nếu khác biệt dù chỉ 1 ký tự (như trường hợp `orders-service` vs `order-service`), cơ chế cân bằng tải động sẽ thất bại hoàn toàn.

---

## 3. Cấu Hình `application.yml` Hoàn Chỉnh Của `api-gateway`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway

  cloud:
    gateway:
      routes:
        # Product Service Route
        - id: product-service-route
          uri: lb://product-service
          predicates:
            - Path=/api/products/**

        # Voucher Service Route
        - id: voucher-service-route
          uri: lb://voucher-service
          predicates:
            - Path=/api/vouchers/**

        # Order Service Route (Đã sửa chính xác Service ID: order-service)
        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**

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

## 4. Kết Quả Sau Khi Sửa Lỗi

- Request `GET http://localhost:8080/api/orders` được Gateway định tuyến chính xác sang instance khả dụng của `order-service` đăng ký trên Eureka.
- Trạng thái trả về: **HTTP 200 OK**.
