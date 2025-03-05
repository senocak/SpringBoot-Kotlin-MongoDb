# Spring Boot Kotlin MongoDB Redis Authentication Service

A modern authentication service built with Spring Boot 3.2.4 and Kotlin, featuring MongoDB for data persistence, Redis for caching, and JWT for secure authentication.

## Features

- 🔐 JWT-based Authentication & Authorization
- 📦 MongoDB Integration for Data Persistence
- 🚀 Redis Caching
- 📧 Email Service with Thymeleaf Templates
- 📝 OpenAPI/Swagger Documentation
- ✅ Input Validation
- 🔒 Spring Security Integration
- 🧪 Comprehensive Test Suite with TestContainers

## Prerequisites

- JDK 17 or later
- MongoDB 4.x or later
- Redis 6.x or later
- Docker (optional, for running with docker-compose)

## Technology Stack

- **Framework:** Spring Boot 3.2.4
- **Language:** Kotlin 1.9.23
- **Database:** MongoDB
- **Cache:** Redis
- **Security:** Spring Security, JWT
- **Documentation:** SpringDoc OpenAPI
- **Testing:** JUnit, Mockito, TestContainers
- **Build Tool:** Gradle

## Configuration

The application can be configured using environment variables or the `application.yml` file:

### Core Configuration
```yaml
server:
  port: 8081  # default
spring:
  application:
    name: skmb-service
```

### MongoDB Configuration
```yaml
# Environment variables with default values
MONGO_USER: "anil"
MONGO_PASSWORD: "senocak"
SERVER_IP: "localhost"
MONGO_PORT: "27017"
MONGO_DB: "boilerplate"
```

### Redis Configuration
```yaml
# Environment variables with default values
REDIS_HOST: "localhost"
REDIS_PORT: "6379"
REDIS_PASSWORD: "senocak"
REDIS_TIMEOUT: "300"
```

### Email Configuration
```yaml
# Environment variables with default values
MAIL_HOST: "smtp.ethereal.email"
MAIL_PORT: "587"
MAIL_PROTOCOL: "smtp"
MAIL_USERNAME: "your_email"
MAIL_PASSWORD: "your_password"
```

## Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/SpringBoot-Kotlin-MongoDb.git
cd SpringBoot-Kotlin-MongoDb
```

2. Configure the application:
   - Copy `src/main/resources/application.yml` to `application-local.yml`
   - Update the configuration values as needed

3. Build the project:
```bash
./gradlew clean build
```

4. Run the application:
```bash
./gradlew bootRun
```

## Docker Setup

The project includes a `docker-compose.yml` file that sets up MongoDB and Redis services.

1. Start the services:
```bash
docker-compose up -d
```

This will start:
- MongoDB (available at `localhost:27017`)
  - Username: anil
  - Password: senocak
- Redis (available at `localhost:6379`)
  - Password: senocak

2. Stop the services:
```bash
docker-compose down
```

To remove all data volumes:
```bash
docker-compose down -v
```

## API Documentation

Once the application is running, you can access the API documentation at:
- Swagger UI: `http://localhost:8081/swagger-ui.html`
- OpenAPI Docs: `http://localhost:8081/api/v1/swagger`

## Testing

The project includes both unit and integration tests. To run the tests:

```bash
# Run all tests
./gradlew test

# Run unit tests only
./gradlew test -Pprofile=unit

# Run integration tests only
./gradlew test -Pprofile=integration
```

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.
