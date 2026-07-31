package com.smartai.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
	webEnvironment = WebEnvironment.RANDOM_PORT,
	properties = {
		"management.server.port=0",
		"management.health.rabbit.enabled=false",
		"spring.datasource.url=jdbc:h2:mem:smartai-production-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.flyway.user=sa",
		"spring.flyway.password=",
		"spring.flyway.locations=classpath:db/migration/common",
		"smartai.requirement-drafts.encryption-key=MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
	})
@ActiveProfiles("production")
class ProductionManagementSecurityTests {

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(5))
		.build();

	@LocalServerPort
	int applicationPort;

	@LocalManagementPort
	int managementPort;

	@Test
	void exposesOnlyHealthAndProbeEndpointsOnTheManagementPort() throws Exception {
		assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
		assertThat(get(managementPort, "/actuator/health/db").statusCode()).isEqualTo(401);
		assertThat(get(managementPort, "/actuator/info").statusCode()).isEqualTo(401);
	}

	@Test
	void keepsManagementEndpointsOffTheApplicationPort() throws Exception {
		HttpResponse<String> response = get(applicationPort, "/actuator/health");
		assertThat(response.statusCode()).isEqualTo(404);
		assertThat(response.body()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
	}

	@Test
	void rejectsAnonymousRequirementDraftCallsInProduction() throws Exception {
		HttpResponse<String> response = get(
			applicationPort,
			"/api/core/v1/requirement-drafts/00000000-0000-0000-0000-000000000001");
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.body()).contains("\"code\":\"AUTH_FAILED\"");
	}

	private HttpResponse<String> get(int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create("http://127.0.0.1:" + port + path))
			.timeout(Duration.ofSeconds(5))
			.GET()
			.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
