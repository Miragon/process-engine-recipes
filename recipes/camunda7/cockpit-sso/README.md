# camunda7-cockpit-sso

Camunda 7's Cockpit, Tasklist and Admin bring their own form login, so users get one more password next to your identity provider. This recipe replaces that login with OAuth2/OIDC against an external provider such as Keycloak, and only lets in users who hold a configured client role.

**Serves:** teams running Camunda 7 on Spring Boot who already have an OIDC identity provider.

## Ingredients

| Component | Version |
| --- | --- |
| Camunda 7 | 7.24.0 |
| Spring Boot | 3.5.16 |
| Java | 21 |

You need a JDK 21, Podman or Docker with Compose, and an OAuth2/OIDC identity provider (the local stack brings Keycloak). Maven does not need to be installed: `./mvnw` downloads the pinned version. Versions are pinned in `pom.xml`.

## How it works

The `cockpit-sso-starter` module configures a Spring Security filter chain that:

1. Redirects unauthenticated web-app requests to the identity provider (`oauth2Login`) and validates bearer tokens on the REST API (`oauth2ResourceServer`).
2. Requires the `application.web-app-role` role for `/app/**`, `/api/**`, `/assets/**` and `/lib/**`.
3. Maps the token's roles into Spring `GrantedAuthorities` and hands the user to Camunda through `ContainerBasedAuthenticationFilter` and a read-only identity provider, so the engine needs no user store of its own.
4. Honours `X-Forwarded-*` headers, so redirect URLs are correct behind HTTPS termination such as an Ingress or OpenShift route.

See [`CamundaWebAppsSecurityConfiguration`](cockpit-sso-starter/src/main/java/io/miragon/camunda/sso/config/CamundaWebAppsSecurityConfiguration.java) for the full filter chain.

| Module | What it does |
| --- | --- |
| [`cockpit-sso-starter`](cockpit-sso-starter) | The Spring Security filter chain and the Camunda identity glue. |
| [`cockpit-sso-service`](cockpit-sso-service) | A runnable example application on port `8082` that wires in the starter. |
| [`sso-stack`](sso-stack) | A local Keycloak and PostgreSQL for development. |
| [`bruno`](bruno) | The [Bruno](https://www.usebruno.com/) scenarios used as the proof. |

## Cook it

The stack calls Keycloak by the hostname `keycloak`, so add it to your hosts file (`/etc/hosts`, or `C:\Windows\System32\Drivers\etc\hosts` on Windows):

```
127.0.0.1 localhost keycloak
```

Then run everything with `./e2e.sh`, or step by step:

1. Start Keycloak, PostgreSQL and a migration job that seeds the `testrealm`, an `engine` client and a `johndoe` test user:

   ```bash
   docker compose -f sso-stack/docker-compose.yml up -d
   ```

2. Run the example service. The first `install` publishes `cockpit-sso-starter` to your local Maven repository; rerun it only after changing the starter:

   ```bash
   set -a && source sso-stack/local-docker.env && set +a
   ./mvnw install -DskipTests
   ./mvnw -f cockpit-sso-service/pom.xml spring-boot:run
   ```

3. Open <http://localhost:8082/app/welcome/default/> and log in as `johndoe` / `test`.

## Taste test

`./e2e.sh` starts the SSO stack and the example service, then runs the Bruno collection in `bruno/`:

1. Keycloak serves the OpenID configuration of `testrealm`.
2. `johndoe` gets a token.
3. With that token, the Camunda REST API returns the engine version.
4. With that token, the REST API lists the process definitions.

Run the scenarios alone against a running stack with `npx @usebruno/cli run bruno --env Local`.

## Take it home

`cockpit-sso-starter` is not published and not auto-configured. Pick one way:

- **Copy the configuration (recommended).** Copy the classes under [`cockpit-sso-starter/.../config`](cockpit-sso-starter/src/main/java/io/miragon/camunda/sso/config) into your Camunda Spring Boot project and adapt them.
- **Depend on it locally.** Run `./mvnw install`, add the dependency and make component scanning find the configuration:

  ```xml
  <dependency>
      <groupId>io.miragon.camunda</groupId>
      <artifactId>cockpit-sso-starter</artifactId>
      <version>1.0.0-SNAPSHOT</version>
  </dependency>
  ```

  ```java
  @SpringBootApplication
  @ComponentScan(basePackages = {"com.your.app", "io.miragon.camunda.sso.config"})
  public class YourApplication { }
  ```

Either way, configure the identity provider with the standard Spring Security properties plus two of the recipe's own:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${SSO_BASE_URL}/realms/${SSO_REALM}
      client:
        provider:
          keycloak:
            issuer-uri: ${SSO_BASE_URL}/realms/${SSO_REALM}
            user-name-attribute: sub
        registration:
          keycloak:
            provider: keycloak
            client-id: ${SSO_ENGINE_CLIENT_ID}
            client-secret: ${SSO_ENGINE_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/app/{action}/oauth2/code/{registrationId}"

application:
  web-app-role: "${SSO_ENGINE_CLIENT_ID}:${WEBAPP_REQUIRED_ROLE}"
  registration: keycloak
```

| Property | Description | Example |
| --- | --- | --- |
| `application.web-app-role` | Client role required to access the web apps, as `<client-id>:<role>`. | `engine:webapp-user` |
| `application.registration` | The OAuth2 client registration id used for the login redirect. | `keycloak` |
| `spring.security.oauth2.client.*` | Standard Spring Security OAuth2 client configuration (login). | — |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | Issuer used to validate bearer tokens on the REST API. | — |

## Kitchen notes

- **Adding the module as a dependency does nothing on its own.** Despite its name, `cockpit-sso-starter` has no auto-configuration. Without the `@ComponentScan` above, the form login stays.
- **The issuer must resolve the same way everywhere.** Tokens carry the issuer URL the browser saw. If `keycloak` resolves differently in the browser and in the service, token validation fails. That is why the hosts entry is needed.
- **Only `/app/**` is served.** The bare `/` is not exposed, so open the web apps under `/app/welcome/default/`.
- **`sso-stack` is for development only.** Its Keycloak and PostgreSQL run with demo credentials and are not meant for production.
