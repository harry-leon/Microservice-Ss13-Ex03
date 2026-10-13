# Identity Service: Triển khai Stateless Authentication Flow với Spring Security (Bài tập 3)

Dự án Identity Service triển khai xác thực người dùng không lưu trạng thái (Stateless Authentication Flow) với Spring Security, đối soát mật khẩu an toàn bằng `BCryptPasswordEncoder` và sinh mã Access Token bằng JWT (`JwtUtils`).

---

## 1. Mục tiêu & Yêu cầu kỹ thuật
- **Kiến trúc Stateless**: Cấu hình Spring Security với `SessionCreationPolicy.STATELESS` để vô hiệu hóa việc tạo `HttpSession` và `JSESSIONID`. Mỗi request là hoàn toàn độc lập.
- **Mã hóa & Đối soát mật khẩu**:
  - Đăng ký `BCryptPasswordEncoder` làm Bean.
  - Sử dụng phương thức `matches(rawPassword, encodedPassword)` của `PasswordEncoder` trong tầng Service để so khớp mật khẩu. Không so sánh plain-text hay sử dụng `==`/`equals`.
- **Bảo mật thông tin**: Trả về thông báo lỗi chung chung (`Bad credentials`) khi sai thông tin đăng nhập thay vì thông báo cụ thể "Sai mật khẩu" hay "Không tìm thấy người dùng".
- **API Endpoint**:
  - `POST /api/auth/login`: Nhận `LoginRequest` (username, password).
  - Trả về mã trạng thái `200 OK` kèm `JwtResponse` (chứa `accessToken`, `tokenType`, `username`, v.v.) nếu đăng nhập đúng.
  - Trả về mã trạng thái `401 Unauthorized` nếu sai thông tin đăng nhập.
  - Cấu hình `SecurityFilterChain` cho phép truy cập công khai (`permitAll`) vào `/api/auth/login`.

---

## 2. Kiến trúc & Cấu trúc thư mục

Mã nguồn được tổ chức chặt chẽ theo mô hình **Controller - Service - Repository**:

```
src/main/java/com/rikkei/microservice/identity/
├── IdentityServiceApplication.java
├── config/
│   └── WebSecurityConfig.java             # Cấu hình Stateless, BCrypt, FilterChain & permitAll
├── controller/
│   ├── AuthController.java                # Endpoint /api/auth/login & /api/auth/register
│   └── TestController.java                # Endpoint demo kiểm tra quyền truy cập Token
├── dto/
│   ├── JwtResponse.java                   # DTO trả về chứa Access Token
│   ├── LoginRequest.java                  # DTO nhận username, password
│   └── RegisterRequest.java               # DTO đăng ký người dùng
├── entity/
│   └── User.java                          # Entity người dùng triển khai UserDetails
├── exception/
│   └── GlobalExceptionHandler.java        # Xử lý AuthenticationException trả về 401 Bad credentials
├── repository/
│   └── UserRepository.java                # Truy vấn User từ database
├── security/
│   ├── AuthEntryPointJwt.java             # Xử lý lỗi 401 khi truy cập endpoint cần xác thực
│   ├── AuthTokenFilter.java               # Filter trích xuất và xác thực JWT
│   └── JwtUtils.java                      # Tiện ích sinh và kiểm tra JWT token
└── service/
    ├── AuthService.java                   # Interface tầng xác thực nghiệp vụ
    ├── AuthServiceImpl.java               # Triển khai xác thực với BCrypt & JwtUtils
    └── UserDetailsServiceImpl.java        # Tích hợp UserDetailsService cho Spring Security
```

---

## 3. Cấu hình & Chạy ứng dụng

### Cấu hình `application.yml`
```yaml
server:
  port: 8080

spring:
  application:
    name: identity-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:identity_db}
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

app:
  jwt:
    secret: "vS6K5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2zB4xR9mQ3pL1wT7yC5vH8N2z"
    access-token-expiration: 86400000 # 24 hours
```

### Chạy ứng dụng
```bash
mvn spring-boot:run
```

### Chạy Test Suite tự động
```bash
mvn test
```
Tất cả các bài kiểm tra tích hợp trong `AuthIntegrationTest` đảm bảo:
1. Đăng nhập thành công trả về `200 OK`, JWT Access Token hợp lệ, không sinh session/JSESSIONID cookie.
2. Đăng nhập sai mật khẩu trả về `401 Unauthorized` kèm thông báo `Bad credentials`.
3. Đăng nhập với username không tồn tại trả về `401 Unauthorized` kèm thông báo `Bad credentials`.

---

## 4. Hướng dẫn kiểm thử qua Postman

### Bước 1: Đăng ký tài khoản mẫu
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/auth/register`
- **Headers**: `Content-Type: application/json`
- **Body (raw JSON)**:
  ```json
  {
    "username": "user123",
    "email": "user123@example.com",
    "password": "mypassword",
    "roles": ["USER"]
  }
  ```
- **Response**: `200 OK`

### Bước 2: Đăng nhập thành công (Thử đúng)
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/auth/login`
- **Headers**: `Content-Type: application/json`
- **Body (raw JSON)**:
  ```json
  {
    "username": "user123",
    "password": "mypassword"
  }
  ```
- **Kết quả mong đợi**:
  - Status: `200 OK`
  - Body:
    ```json
    {
      "token": "eyJhbGciOiJIUzI1NiJ9...",
      "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
      "tokenType": "Bearer",
      "id": 1,
      "username": "user123",
      "email": "user123@example.com",
      "roles": ["USER"]
    }
    ```
  - Headers: **Không có** cookie `Set-Cookie: JSESSIONID=...` (đảm bảo hoàn toàn Stateless).

### Bước 3: Đăng nhập thất bại (Thử sai)
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/auth/login`
- **Body (raw JSON)**:
  ```json
  {
    "username": "user123",
    "password": "wrongpassword"
  }
  ```
- **Kết quả mong đợi**:
  - Status: `401 Unauthorized`
  - Body:
    ```json
    {
      "error": "Unauthorized",
      "message": "Bad credentials"
    }
    ```
