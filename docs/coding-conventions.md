# Quy ước Java theo MD03

Tham khảo repository [RikkeiProject_MD03](https://github.com/dth251/RikkeiProject_MD03), commit `aef9162b49f8fdd045a85fa711e897af2e0e671d`.

Mỗi service là một ứng dụng Spring Boot độc lập, có cấu trúc:

```text
controller/
service/
service/impl/
repository/
entity/
dto/request/
dto/response/
mapper/
config/exception/
security/
```

- Controller tiếp nhận request, gọi service interface, trả `ResponseEntity<ApiResponse<T>>`. Nghiệp vụ và transaction nằm trong `*ServiceImpl`.
- Service interface định nghĩa các thao tác; implementation dùng constructor injection, biến mô tả rõ như `userRepository`, `passwordEncoder`, `userMapper`.
- DTO request chứa validation; DTO response chứa dữ liệu public. DTO dùng Java record theo quy ước xuống dòng của kế hoạch MD05.
- Mapper riêng chuyển entity thành DTO. Controller không trả JPA entity hay password hash.
- `GlobalExceptionHandler` thống nhất response lỗi. Security trả 401/403 theo cùng dạng `ApiResponse`.
- HTTP status thể hiện lỗi thực tế; không chuyển mọi `RuntimeException` thành 400 hoặc trả nguyên lỗi nội bộ ra client.
- Giữ `@Transactional` tại service implementation, UNIQUE constraint ở database và xử lý 409 khi đăng ký email trùng, kể cả request đồng thời.
- Mỗi tham số, thuộc tính DTO, dependency và annotation quan trọng xuống dòng rõ ràng. Không tạo thư viện/module `shared` giữa các service.
- Java 21, Spring Boot 3.5.6, Gradle Groovy; PostgreSQL riêng từng service. Không sao chép phiên bản Java 17/Spring Boot 3.2.5 của MD03.

Nguồn đối chiếu: [AuthController](https://github.com/dth251/RikkeiProject_MD03/blob/main/src/main/java/ra/edu/controller/AuthController.java), [AuthServiceImpl](https://github.com/dth251/RikkeiProject_MD03/blob/main/src/main/java/ra/edu/service/impl/AuthServiceImpl.java), [UserMapper](https://github.com/dth251/RikkeiProject_MD03/blob/main/src/main/java/ra/edu/mapper/UserMapper.java), [ApiResponse](https://github.com/dth251/RikkeiProject_MD03/blob/main/src/main/java/ra/edu/dto/response/ApiResponse.java).
