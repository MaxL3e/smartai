package com.smartai.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.smartai.core.platform.web.RequestContext;
import com.smartai.core.platform.api.ApiProblemWriter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class SecurityBaselineTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ApiProblemWriter problemWriter;

	@Test
	void exposesOnlyHealthWithRequestMetadata() throws Exception {
		UUID requestId = UUID.randomUUID();
		mockMvc.perform(get("/actuator/health")
				.header(RequestContext.REQUEST_ID_HEADER, requestId)
				.header(RequestContext.CORRELATION_ID_HEADER, "release-check-1"))
			.andExpect(status().isOk())
			.andExpect(header().string(RequestContext.REQUEST_ID_HEADER, requestId.toString()))
			.andExpect(header().string(RequestContext.CORRELATION_ID_HEADER, "release-check-1"))
			.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void deniesInfoUsingTheUnifiedProblemEnvelope() throws Exception {
		mockMvc.perform(get("/actuator/info"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(header().exists(RequestContext.REQUEST_ID_HEADER))
			.andExpect(jsonPath("$.requestId").isString())
			.andExpect(jsonPath("$.error.code").value("AUTH_FAILED"))
			.andExpect(jsonPath("$.error.retryable").value(false))
			.andExpect(jsonPath("$.error.retryAfterSeconds").doesNotExist());
	}

	@Test
	void limitsProblemTraceIdsToTheAuditContract() throws Exception {
		UUID requestId = UUID.randomUUID();
		mockMvc.perform(get("/actuator/info")
				.header(RequestContext.REQUEST_ID_HEADER, requestId)
				.header(RequestContext.CORRELATION_ID_HEADER, "a".repeat(65)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.requestId").value(requestId.toString()))
			.andExpect(jsonPath("$.error.traceId").value(requestId.toString()));
	}

	@Test
	void deniesHealthComponentPathsOutsideTheExplicitProbeAllowlist() throws Exception {
		mockMvc.perform(get("/actuator/health/db"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.error.code").value("AUTH_FAILED"));
	}

	@Test
	void writesRetryMetadataUsingTheJsonErrorContract() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();

		problemWriter.write(request, response, 503, "DEPENDENCY_UNAVAILABLE", "Try again", true, 30);

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON)).isTrue();
		assertThat(response.getHeader("Retry-After")).isEqualTo("30");
		assertThat(response.getContentAsString()).contains("\"retryAfterSeconds\":30");
	}
}
