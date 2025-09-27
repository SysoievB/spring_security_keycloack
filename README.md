# Keycloak Setup Instructions

## 1. Start the Services

```bash
# Build and start the containers
docker-compose up --build

# Or start in detached mode
docker-compose up -d --build
```

## 2. Configure Keycloak

1. **Access Keycloak Admin Console**
    - URL: http://localhost:8080
    - Username: `admin`
    - Password: `admin123`

2. **Create a Realm**
    - Click on "Create Realm"
    - Name: `spring-keycloack-app`
    - Click "Create"

3. **Create Roles**
    - Go to "Realm roles"
    - Create role: `ADMIN`
    - Create role: `USER`

4. **Create a Client**
    - Go to "Clients" → "Create client"
    - Client ID: `spring-webflux-app`
    - Client type: `OpenID Connect`
    - Click "Next"
    - Client authentication: `ON`
    - Authorization: `ON`
    - Click "Next"
    - Valid redirect URIs: `http://localhost:8090/*`
    - Web origins: `http://localhost:8090`
    - Click "Save"

5. **Get Client Secret**
    - In the client settings, go to "Credentials" tab
    - Copy the "Client secret" value

6. **Create Users**

   **Admin User:**
    - Go to "Users" → "Add user"
    - Username: `admin_user`
    - Email: `admin@example.com`
    - First name: `Admin`
    - Last name: `User`
    - Click "Create"
    - Go to "Credentials" tab → Set password (temporary: OFF)
    - Go to "Role mappings" tab → Assign "ADMIN" role

   **Regular User:**
    - Username: `regular_user`
    - Email: `user@example.com`
    - First name: `Regular`
    - Last name: `User`
    - Assign "USER" role

## 3. Test the API Endpoints

### Get Access Token

```bash
# For Admin User
curl -X POST http://localhost:8080/realms/spring-app/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=spring-webflux-app" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "username=admin_user" \
  -d "password=admin_password"

# For Regular User
curl -X POST http://localhost:8080/realms/spring-app/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=spring-webflux-app" \
  -d "client_secret=YOUR_CLIENT_SECRET" \
  -d "username=regular_user" \
  -d "password=user_password"
```

### Test API Endpoints

```bash
# Public endpoint (no token required)
curl http://localhost:8090/api/public/health

# User endpoint (requires USER or ADMIN role)
curl -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  http://localhost:8090/api/user/profile

# Admin endpoint (requires ADMIN role)
curl -H "Authorization: Bearer YOUR_ADMIN_ACCESS_TOKEN" \
  http://localhost:8090/api/admin/users
```

## 4. Directory Structure

```
project/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/example/springwebfluxkeycloak/
│       │       ├── SpringWebfluxKeycloakApplication.java
│       │       ├── config/
│       │       │   └── SecurityConfig.java
│       │       ├── controller/
│       │       │   └── UserController.java
│       │       ├── model/
│       │       │   └── User.java
│       │       └── service/
│       │           └── UserService.java
│       └── resources/
│           └── application.properties
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

## 5. Role-Based Access Control

- **Public endpoints** (`/api/public/**`): No authentication required
- **User endpoints** (`/api/user/**`): Requires USER or ADMIN role
- **Admin endpoints** (`/api/admin/**`): Requires ADMIN role only

## 6. Environment Variables

When running with Docker Compose, the Spring application automatically uses:
- `KEYCLOAK_URL=http://keycloak:8080`

For local development, use:
- `KEYCLOAK_URL=http://localhost:8080`

## 7. Troubleshooting

- Ensure Keycloak is fully started before the Spring application
- Check the realm name matches in both Keycloak and application.properties
- Verify client configuration in Keycloak
- Check logs with `docker-compose logs spring-app`
  - hMsHPVsQ7fXL5dMBGejtlcRcknFWio4Z