package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MatchingLifecycleApiTests {

	private static final String MERGE_PATCH = "application/merge-patch+json";
	private static final UUID STANDALONE_RESUME_CONNECTOR_ID =
		UUID.fromString("00000000-0000-0000-0000-000000000301");

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void uploadsAnAuthorizedStandaloneResumeAndMatchesItsExactVersion() throws Exception {
		UUID tenantId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			tenantId, TenantActorResolver.tenantKey(tenantId), "Standalone resume matching tenant");
		ApprovedPlan fixture = approvedPlan(tenantId);
		byte[] content = (
			"Name: Standalone Candidate\n"
				+ "Location: Beijing\n"
				+ "Education\nMaster\n"
				+ "Skills\nData governance, Master data, Data quality\n"
				+ "Experience\n7 years of professional experience leading master data governance and enterprise data standards delivery.\n")
			.getBytes(StandardCharsets.UTF_8);
		MvcResult uploaded = mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(new MockMultipartFile("file", "standalone-candidate.txt", MediaType.TEXT_PLAIN_VALUE, content))
				.header(TenantActorResolver.TENANT_HEADER, tenantId)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
			.andExpect(jsonPath("$.data.candidate.consentStatus").value("GRANTED"))
			.andReturn();
		JsonNode uploadedData = json(uploaded).path("data");
		UUID resumeVersionId = UUID.fromString(uploadedData.at("/resumeVersionRef/id").asText());
		long resumeVersion = uploadedData.at("/resumeVersionRef/version").asLong();

		MvcResult run = mockMvc.perform(withTenant(post(
				"/api/core/v1/recruitment-tasks/{taskId}/match-runs", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(matchRunBody(fixture, STANDALONE_RESUME_CONNECTOR_ID)), tenantId))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.data.metrics.scanned").value(1))
			.andExpect(jsonPath("$.data.metrics.scored").value(1))
			.andReturn();
		UUID runId = UUID.fromString(json(run).at("/data/id").asText());

		mockMvc.perform(withTenant(get("/api/core/v1/match-runs/{matchRunId}/results", runId), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].resumeVersionRef.id").value(resumeVersionId.toString()))
			.andExpect(jsonPath("$.data[0].resumeVersionRef.version").value(resumeVersion));
	}

	@Test
	void normalizesCandidatesAndProducesExplainableDeterministicResults() throws Exception {
		ApprovedPlan fixture = approvedPlan();
		UUID connectorId = UUID.randomUUID();
		UUID candidateKey = UUID.randomUUID();
		String candidateBody = candidateBody(
			connectorId, "candidate-001", "resume-v1", "Beijing", "7.0", "GRANTED");

		MvcResult imported = mockMvc.perform(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", candidateKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.sourceType").value("TALENT_POOL"))
			.andExpect(jsonPath("$.data.normalizerKind").value("DETERMINISTIC_NORMALIZER"))
			.andReturn();
		UUID candidateId = UUID.fromString(json(imported).at("/data/candidate/id").asText());

		mockMvc.perform(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", candidateKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.candidate.id").value(candidateId.toString()));

		mockMvc.perform(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", candidateKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody.replace("Candidate candidate-001", "Different candidate")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

		UUID runKey = UUID.randomUUID();
		String runBody = matchRunBody(fixture, connectorId);
		MvcResult runResult = mockMvc.perform(post(
			"/api/core/v1/recruitment-tasks/{taskId}/match-runs", fixture.taskId())
			.header("Idempotency-Key", runKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(runBody))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.data.generatorKind").value("DETERMINISTIC_RULES"))
			.andExpect(jsonPath("$.data.metrics.scanned").value(1))
			.andExpect(jsonPath("$.data.metrics.scored").value(1))
			.andExpect(jsonPath("$.data.metrics.hardFiltered").value(0))
			.andReturn();
		UUID runId = UUID.fromString(json(runResult).at("/data/id").asText());

		mockMvc.perform(post("/api/core/v1/recruitment-tasks/{taskId}/match-runs", fixture.taskId())
			.header("Idempotency-Key", runKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(runBody))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(runId.toString()));

		MvcResult resultsResponse = mockMvc.perform(get(
			"/api/core/v1/match-runs/{matchRunId}/results", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].candidate.id").value(candidateId.toString()))
			.andExpect(jsonPath("$.data[0].generatorKind").value("DETERMINISTIC_RULES"))
			.andExpect(jsonPath("$.data[0].hardFilterResult.passed").value(true))
			.andExpect(jsonPath("$.data[0].totalScore").value(org.hamcrest.Matchers.greaterThan(0.0)))
			.andExpect(jsonPath("$.data[0].criterionScores.length()").value(3))
			.andReturn();
		JsonNode result = json(resultsResponse).at("/data/0");
		UUID resultId = UUID.fromString(result.path("id").asText());
		for (JsonNode criterion : result.path("criterionScores")) {
			if (criterion.path("rawScore").decimalValue().compareTo(BigDecimal.ZERO) > 0) {
				assertThat(criterion.path("evidenceRefs").isEmpty()).isFalse();
				assertThat(criterion.at("/evidenceRefs/0/sourceVersionRef/type").asText())
					.isEqualTo("ResumeVersion");
				assertThat(criterion.at("/evidenceRefs/0/modelInvocationId").isNull()).isTrue();
				assertThat(criterion.at("/evidenceRefs/0/sourceLocator/section").asText()).isNotBlank();
			}
		}

		mockMvc.perform(get("/api/core/v1/match-results/{matchResultId}", resultId))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.rank").value(1));
		mockMvc.perform(get("/api/core/v1/recruitment-tasks/{taskId}/task-candidates", fixture.taskId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].sourceType").value("TALENT_POOL"))
			.andExpect(jsonPath("$.data[0].currentMatchResultRef.id").value(resultId.toString()));
		mockMvc.perform(get("/api/core/v1/recruitment-tasks/{taskId}", fixture.taskId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.businessStage").value("CANDIDATE_CONFIRMATION"));

		assertThat(jdbcTemplate.queryForObject(
			"SELECT generator_kind FROM match_run WHERE match_run_id = ?", String.class, runId))
			.isEqualTo("DETERMINISTIC_RULES");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT generator_kind FROM match_result WHERE match_result_id = ?", String.class, resultId))
			.isEqualTo("DETERMINISTIC_RULES");
		String auditPayload = jdbcTemplate.queryForObject(
			"SELECT payload FROM audit_event WHERE resource_id = ? AND action = 'DETERMINISTIC_MATCH_COMPLETED'",
			String.class, runId.toString());
		assertThat(auditPayload).contains("DETERMINISTIC_RULES").contains("\"modelInvocationId\":null");
	}

	@Test
	void appliesHardFiltersBeforeScoringAndHidesResourcesAcrossTenants() throws Exception {
		ApprovedPlan fixture = approvedPlan();
		UUID connectorId = UUID.randomUUID();
		mockMvc.perform(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody(connectorId, "candidate-hard-fail", "resume-v1", "Shanghai", "9", "GRANTED")))
			.andExpect(status().isCreated());

		MvcResult runResponse = mockMvc.perform(post(
			"/api/core/v1/recruitment-tasks/{taskId}/match-runs", fixture.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(matchRunBody(fixture, connectorId)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.metrics.hardFiltered").value(1))
			.andReturn();
		UUID runId = UUID.fromString(json(runResponse).at("/data/id").asText());
		MvcResult results = mockMvc.perform(get("/api/core/v1/match-runs/{matchRunId}/results", runId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].hardFilterResult.passed").value(false))
			.andExpect(jsonPath("$.data[0].totalScore").value(0))
			.andExpect(jsonPath("$.data[0].recommendationLevel").value("NOT_RECOMMENDED"))
			.andReturn();
		UUID resultId = UUID.fromString(json(results).at("/data/0/id").asText());

		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Other matching tenant");
		mockMvc.perform(withTenant(get("/api/core/v1/match-runs/{matchRunId}", runId), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(get("/api/core/v1/match-results/{matchResultId}", resultId), otherTenant))
			.andExpect(status().isNotFound());
	}

	@Test
	void rejectsMatchingUntilThePositionPlanIsApproved() throws Exception {
		TaskFixture task = createTask();
		mockMvc.perform(post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", task.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(generationBody(task)))
			.andExpect(status().isAccepted());
		JsonNode plan = json(mockMvc.perform(get(
			"/api/core/v1/recruitment-tasks/{taskId}/position-plan", task.taskId()))
			.andExpect(status().isOk()).andReturn()).path("data");
		ApprovedPlan unapproved = new ApprovedPlan(
			task.taskId(), UUID.fromString(plan.path("id").asText()), plan.path("version").asLong(),
			UUID.fromString(plan.at("/scorecard/id").asText()), plan.at("/scorecard/versionNo").asLong());
		mockMvc.perform(post("/api/core/v1/recruitment-tasks/{taskId}/match-runs", task.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(matchRunBody(unapproved, UUID.randomUUID())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("TASK_STAGE_CONFLICT"));
	}

	private ApprovedPlan approvedPlan() throws Exception {
		return approvedPlan(null);
	}

	private ApprovedPlan approvedPlan(UUID tenantId) throws Exception {
		TaskFixture task = createTask(tenantId);
		mockMvc.perform(inTenant(post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", task.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(generationBody(task)), tenantId))
			.andExpect(status().isAccepted());
		JsonNode plan = json(mockMvc.perform(inTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}/position-plan", task.taskId()), tenantId))
			.andExpect(status().isOk()).andReturn()).path("data");
		UUID planId = UUID.fromString(plan.path("id").asText());
		String inputHash = plan.path("contentHash").asText();
		MvcResult checkpointResponse = mockMvc.perform(inTenant(post(
			"/api/core/v1/position-plan-versions/{planVersionId}/review-requests", planId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"requiredRole", "RECRUITMENT_MANAGER",
				"inputHash", inputHash))), tenantId))
			.andExpect(status().isCreated())
			.andReturn();
		UUID checkpointId = UUID.fromString(json(checkpointResponse).at("/data/id").asText());
		mockMvc.perform(inTenant(post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"decision", "APPROVE",
				"inputHash", inputHash))), tenantId))
			.andExpect(status().isOk());
		JsonNode approved = json(mockMvc.perform(inTenant(get(
			"/api/core/v1/position-plan-versions/{planVersionId}", planId), tenantId))
			.andExpect(status().isOk()).andReturn()).path("data");
		return new ApprovedPlan(
			task.taskId(), planId, approved.path("version").asLong(),
			UUID.fromString(approved.at("/scorecard/id").asText()),
			approved.at("/scorecard/versionNo").asLong());
	}

	private TaskFixture createTask() throws Exception {
		return createTask(null);
	}

	private TaskFixture createTask(UUID tenantId) throws Exception {
		MvcResult created = mockMvc.perform(inTenant(post("/api/core/v1/requirement-drafts")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"input", "Create a recruitment task for a Data Governance Specialist in Beijing."))), tenantId))
			.andExpect(status().isCreated()).andReturn();
		UUID draftId = UUID.fromString(json(created).at("/data/id").asText());

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("positionName", confirmedField("Data Governance Specialist"));
		fields.put("organizationRef", confirmedField("Digital Technology Department"));
		fields.put("locations", confirmedField(List.of("Beijing")));
		fields.put("headcount", confirmedField(2));
		fields.put("coreRequirements", confirmedField(List.of(
			"Enterprise data governance experience", "Master data and data standards delivery")));
		MvcResult patched = mockMvc.perform(inTenant(patch("/api/core/v1/requirement-drafts/{draftId}", draftId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MERGE_PATCH)
			.content(objectMapper.writeValueAsString(Map.of("fields", fields))), tenantId))
			.andExpect(status().isOk()).andReturn();
		String inputHash = patched.getResponse().getHeader("X-SmartAI-Input-Hash");
		MvcResult converted = mockMvc.perform(inTenant(post("/api/core/v1/requirement-drafts/{draftId}/convert", draftId)
			.header("If-Match", "\"2\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"confirmation", Map.of("confirmed", true, "inputHash", inputHash),
				"ownerUserId", TenantActorResolver.DEMO_USER_ID,
				"participantUserIds", List.of()))), tenantId))
			.andExpect(status().isCreated()).andReturn();
		long draftVersion = json(mockMvc.perform(inTenant(
			get("/api/core/v1/requirement-drafts/{draftId}", draftId), tenantId))
			.andExpect(status().isOk()).andReturn()).at("/data/version").asLong();
		return new TaskFixture(
			draftId, draftVersion, UUID.fromString(json(converted).at("/data/id").asText()));
	}

	private String candidateBody(
			UUID connectorId,
			String externalCandidateId,
			String sourceVersion,
			String location,
			String experienceYears,
			String consentStatus) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("connectorId", connectorId);
		body.put("sourceType", "TALENT_POOL");
		body.put("sourceSystem", "matching-test-pool");
		body.put("externalCandidateId", externalCandidateId);
		body.put("sourceVersion", sourceVersion);
		body.put("displayName", "Candidate " + externalCandidateId);
		body.put("consentStatus", consentStatus);
		body.put("sections", List.of(
			Map.of("code", "SUMMARY", "text", "Data governance specialist delivering measurable enterprise outcomes."),
			Map.of("code", "EXPERIENCE", "text", "Seven years leading master data governance and data standard programs."),
			Map.of("code", "SKILLS", "text", "Data governance, master data, data quality, stakeholder management."),
			Map.of("code", "LOCATION", "text", location)));
		body.put("facts", Map.of(
			"location", location,
			"experienceYears", new BigDecimal(experienceYears),
			"educationLevel", "MASTER",
			"skills", List.of("Data governance", "Master data", "Data quality")));
		body.put("sourceUpdatedAt", OffsetDateTime.now().minusDays(1));
		return objectMapper.writeValueAsString(body);
	}

	private String matchRunBody(ApprovedPlan plan, UUID connectorId) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
			"positionPlanVersionRef", resourceRef("PositionPlanVersion", plan.planId(), plan.planVersion()),
			"scorecardVersionRef", resourceRef("ScorecardVersion", plan.scorecardId(), plan.scorecardVersion()),
			"candidateScope", Map.of(
				"connectorIds", List.of(connectorId),
				"filters", Map.of(),
				"dataCutoffAt", OffsetDateTime.now().plusMinutes(5),
				"maximumCandidates", 100),
			"minimumRecommendationScore", 0));
	}

	private String generationBody(TaskFixture fixture) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
			"requirementDraftRef", resourceRef("RequirementDraft", fixture.draftId(), fixture.draftVersion()),
			"knowledgeVersionRefs", List.of()));
	}

	private static Map<String, Object> confirmedField(Object value) {
		return Map.of(
			"value", value,
			"confidence", new BigDecimal("1.0"),
			"source", "USER",
			"needsConfirmation", false,
			"evidence", "Confirmed for matching lifecycle test");
	}

	private static Map<String, Object> resourceRef(String type, UUID id, long version) {
		return Map.of("type", type, "id", id, "version", version);
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static MockHttpServletRequestBuilder withTenant(
			MockHttpServletRequestBuilder request,
			UUID tenantId) {
		return request.header(TenantActorResolver.TENANT_HEADER, tenantId);
	}

	private static MockHttpServletRequestBuilder inTenant(
			MockHttpServletRequestBuilder request,
			UUID tenantId) {
		return tenantId == null ? request : withTenant(request, tenantId);
	}

	private record TaskFixture(UUID draftId, long draftVersion, UUID taskId) {
	}

	private record ApprovedPlan(
			UUID taskId,
			UUID planId,
			long planVersion,
			UUID scorecardId,
			long scorecardVersion) {
	}
}
