# Mega Bike - Security and Login Plan

## 1. Goal

The first security goal is to build login for the internal Mega Bike business application.

The system should support:

- administrator-created employee accounts
- login with email and password
- BCrypt password storage
- JWT access tokens
- refresh tokens
- role-based authorization first
- permission-based authorization later

Initial authentication endpoints:

```text
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/auth/me
```

## 2. Spring Security Mental Model

Spring Security answers two separate questions.

Authentication:

```text
Who is this user?
```

Example:

```text
An employee logs in with email and password.
```

Authorization:

```text
What is this user allowed to do?
```

Example:

```text
An admin can manage users.
An employee can view products but cannot manage users.
```

The planned request flow:

```text
POST /api/auth/login
email + password
        |
        v
load user_account by email
        |
        v
verify BCrypt password
        |
        v
return access token + refresh token
        |
        v
frontend sends Authorization: Bearer <access-token>
        |
        v
Spring Security validates token on each request
        |
        v
controller/service checks roles or permissions
```

## 3. Project Structure

Use the identity module from the modular monolith plan.

Recommended package root:

```text
com.megabike
```

The application package has been refactored to this root. The identity module should live under:

```text
src/main/java/com/megabike/identity
```

Recommended identity structure:

```text
identity
├── api
│   ├── AuthController
│   ├── LoginRequest
│   ├── LoginResponse
│   └── CurrentUserResponse
├── application
│   ├── AuthService
│   └── CurrentUserService
├── domain
│   ├── UserAccount
│   ├── Role
│   ├── Permission
│   └── RefreshToken
└── infrastructure
    ├── SecurityConfig
    ├── JwtService
    ├── JwtAuthenticationFilter
    └── JpaUserDetailsService
```

## 4. Database Model

Use Liquibase XML changelogs.

Recommended files:

```text
src/main/resources/db/changelog/db.changelog-master.xml
src/main/resources/db/changelog/db.changelog-dev.xml
src/main/resources/db/changelog/identity/001-create-identity-tables.xml
src/main/resources/db/changelog/identity/002-insert-dev-identity-data.xml
```

Initial tables:

```text
user_account
role
permission
user_role
role_permission
refresh_token
```

Suggested `user_account` fields:

```text
id
email
password_hash
display_name
enabled
created_at
updated_at
created_by
updated_by
version
```

Suggested `role` fields:

```text
id
name
created_at
updated_at
```

Suggested `permission` fields:

```text
id
name
created_at
updated_at
```

Suggested `refresh_token` fields:

```text
id
user_account_id
token_hash
expires_at
revoked_at
created_at
```

Store refresh token hashes, not plain refresh tokens.

## 5. Roles and Permissions

Start with simple roles:

```text
ADMIN
MANAGER
EMPLOYEE
```

Longer-term permissions:

```text
USER_MANAGE
PRODUCT_READ
PRODUCT_WRITE
SUPPLIER_MANAGE
PURCHASE_ORDER_CREATE
PURCHASE_ORDER_APPROVE
STOCK_RECEIVE
STOCK_ADJUST
REPORT_VIEW
```

Recommended approach:

- First login slice can authorize by role.
- Product management should introduce permissions.
- Long term, roles should be collections of permissions.

Example:

```java
@PreAuthorize("hasAuthority('PRODUCT_WRITE')")
@PostMapping("/api/internal/products")
public ProductResponse createProduct(...) {
    ...
}
```

## 6. API Access Rules

Current implemented security policy:

```text
/api/auth/**        public for now
/api/admin/**       ADMIN
/api/internal/**    ADMIN, MANAGER, EMPLOYEE
any other request   authenticated
```

`/api/auth/**` is temporarily public because the auth endpoints do not exist yet.
When `AuthController`, JWT validation, refresh, and logout are implemented, this should become more specific.

Planned final auth policy:

```text
/api/auth/login      public
/api/auth/refresh    public, but requires a valid refresh token
/api/auth/logout     authenticated
/api/auth/me         authenticated
```

Spring Security configuration should be stateless for API requests:

```text
SessionCreationPolicy.STATELESS
```

Implemented in:

```text
src/main/java/com/megabike/identity/infrastructure/SecurityConfig.java
```

## 7. Core Spring Security Components

`SecurityConfig`

Implemented. Defines:

- public endpoints
- protected endpoints
- stateless API security
- password encoder
- method security
- `AuthenticationManager` bean for the upcoming login service

Not implemented yet:

- JWT filter registration
- JWT token validation

`PasswordEncoder`

Implemented with BCrypt:

```java
@Bean
PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

`JpaUserDetailsService`

Implemented. Loads `UserAccount` by email and converts roles/permissions into Spring Security authorities.

Role authorities are exposed with Spring Security's `ROLE_` prefix:

```text
ROLE_ADMIN
ROLE_MANAGER
ROLE_EMPLOYEE
```

Permission authorities are exposed directly:

```text
USER_MANAGE
PRODUCT_READ
PRODUCT_WRITE
```

`JwtService`

Not implemented yet.

Responsible for:

- creating access tokens
- validating access tokens
- extracting user identity
- extracting authorities if they are embedded in the token

`JwtAuthenticationFilter`

Not implemented yet.

Reads:

```http
Authorization: Bearer <token>
```

Then:

- validates the token
- loads the user
- creates the Spring Security authentication object
- places it into the security context

`AuthService`

Not implemented yet.

Responsible for:

- login
- refresh
- logout
- current user lookup

## 8. First Vertical Slice

Build login before catalog/product work.

Step order and current status:

1. Add identity Liquibase changelog. Done.
2. Create identity entities. Done.
3. Create repositories. Done.
4. Add `JpaUserDetailsService`. Done.
5. Add `SecurityConfig`. Done.
6. Add BCrypt password handling. Done.
7. Add `POST /api/auth/login`. Not started.
8. Add JWT access-token generation. Not started.
9. Add refresh-token persistence. Schema exists, service not started.
10. Add `GET /api/auth/me`. Not started.
11. Add an initial admin-user strategy. Dev seed exists, production bootstrap not started.
12. Add integration tests for login success, login failure, and protected endpoint access. Not started.

## 9. Initial Admin User

There are two reasonable options.

Development seed:

- Liquibase inserts a known local admin user.
- Simple for local development.
- Must not use a real production password.
- Loaded only through the Spring `dev` profile.
- Current dev users are `admin@megabike.local`, `manager@megabike.local`, and `employee@megabike.local`.
- Current dev password for all three users is `password`.

Startup bootstrap:

- Application creates an admin user from environment variables if no users exist.
- Better for deployment.
- Requires configuration such as `MEGABIKE_ADMIN_EMAIL` and `MEGABIKE_ADMIN_PASSWORD`.

Recommended:

```text
Use a development seed first.
Move to startup bootstrap before production deployment.
```

## 10. Testing Plan

Minimum tests:

```text
POST /api/auth/login returns access token for valid credentials
POST /api/auth/login rejects invalid password
GET /api/auth/me rejects missing token
GET /api/auth/me returns current user with valid token
/api/admin/** rejects non-admin users
```

Later tests:

```text
expired access token is rejected
refresh token can issue a new access token
revoked refresh token is rejected
disabled user cannot log in
permission-protected endpoint rejects missing permission
```

## 11. Current Implementation State

Implemented files:

```text
src/main/java/com/megabike/identity/domain/UserAccount.java
src/main/java/com/megabike/identity/domain/Role.java
src/main/java/com/megabike/identity/domain/Permission.java
src/main/java/com/megabike/identity/domain/RefreshToken.java
src/main/java/com/megabike/identity/domain/UserAccountRepository.java
src/main/java/com/megabike/identity/domain/RoleRepository.java
src/main/java/com/megabike/identity/domain/PermissionRepository.java
src/main/java/com/megabike/identity/domain/RefreshTokenRepository.java
src/main/java/com/megabike/identity/infrastructure/JpaUserDetailsService.java
src/main/java/com/megabike/identity/infrastructure/SecurityConfig.java
```

Implemented infrastructure:

```text
Liquibase identity schema
dev-only mock identity data
BCrypt password encoder
stateless Spring Security filter chain
route rules for auth, admin, and internal APIs
method security support through @EnableMethodSecurity
database-backed UserDetailsService
```

Next implementation step:

```text
Create AuthService and AuthController for POST /api/auth/login.
```

That will connect the configured `AuthenticationManager`, `JpaUserDetailsService`, and `PasswordEncoder` into a real login endpoint.

After that, implement the Java identity domain around the schema.
