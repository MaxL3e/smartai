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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

	@Test
	void confirmsAnImmutableCandidateListAndPublishesAVersionedRecommendationReport() throws Exception {
		UUID tenantId = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			tenantId, TenantActorResolver.tenantKey(tenantId), "Candidate-list lifecycle tenant");
		ApprovedPlan fixture = approvedPlan(tenantId);
		UUID connectorId = UUID.randomUUID();
		mockMvc.perform(withTenant(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody(
				connectorId, "candidate-g4", "resume-v1", "Beijing", "7.0", "GRANTED")), tenantId))
			.andExpect(status().isCreated());

		MvcResult runResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/match-runs", fixture.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(matchRunBody(fixture, connectorId)), tenantId))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
			.andReturn();
		JsonNode run = json(runResponse).path("data");
		UUID runId = UUID.fromString(run.path("id").asText());
		long runVersion = run.path("version").asLong();
		JsonNode matchResultTaskCandidateRef = json(mockMvc.perform(withTenant(get(
			"/api/core/v1/match-runs/{matchRunId}/results", runId), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andReturn()).at("/data/0/taskCandidateRef");
		JsonNode taskCandidate = json(mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}/task-candidates", fixture.taskId()), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andReturn()).at("/data/0");
		UUID taskCandidateId = UUID.fromString(taskCandidate.path("id").asText());
		long taskCandidateVersion = taskCandidate.path("version").asLong();
		assertThat(matchResultTaskCandidateRef.path("id").asText()).isEqualTo(taskCandidateId.toString());
		assertThat(matchResultTaskCandidateRef.path("version").asLong()).isEqualTo(taskCandidateVersion);
		long taskVersion = taskVersion(fixture.taskId(), tenantId);

		Map<String, Object> invitationPlan = Map.of(
			"connectorId", UUID.randomUUID(),
			"templateId", "online-interview-reserved",
			"deadline", OffsetDateTime.now().plusDays(2),
			"channel", "EMAIL",
			"messageTemplateId", "candidate-invitation-v1",
			"externalImpactSummary", "仅生成邀请方案，不创建面试或调用外部系统");
		String previewBody = objectMapper.writeValueAsString(Map.of(
			"matchRunRef", resourceRef("MatchRun", runId, runVersion),
			"taskCandidateRefs", List.of(resourceRef(
				"TaskCandidate", UUID.fromString(matchResultTaskCandidateRef.path("id").asText()),
				matchResultTaskCandidateRef.path("version").asLong())),
			"selectionNotes", Map.of(taskCandidateId, "优先核实大型项目职责范围"),
			"invitationPlan", invitationPlan));
		UUID previewKey = UUID.randomUUID();
		MvcResult firstPreviewResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews", fixture.taskId())
			.header("Idempotency-Key", previewKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(previewBody), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.version").value(1))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andExpect(jsonPath("$.data.items[0].selectionReason").isNotEmpty())
			.andExpect(jsonPath("$.data.items[0].note").value("优先核实大型项目职责范围"))
			.andReturn();
		JsonNode firstPreview = json(firstPreviewResponse).path("data");
		UUID previewId = UUID.fromString(firstPreview.path("id").asText());
		String inputHash = firstPreview.path("inputHash").asText();
		mockMvc.perform(withTenant(get(
			"/api/core/v1/candidate-list-previews/{previewId}", previewId), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(header().string("X-SmartAI-Input-Hash", inputHash))
			.andExpect(jsonPath("$.data.id").value(previewId.toString()))
			.andExpect(jsonPath("$.data.items[0].note").value("优先核实大型项目职责范围"));

		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews", fixture.taskId())
			.header("Idempotency-Key", previewKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(previewBody), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(previewId.toString()));
		MvcResult secondPreviewResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews", fixture.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(previewBody), tenantId))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode secondPreview = json(secondPreviewResponse).path("data");
		assertThat(secondPreview.path("id").asText()).isNotEqualTo(previewId.toString());
		assertThat(secondPreview.path("inputHash").asText()).isEqualTo(inputHash);
		UUID secondPreviewId = UUID.fromString(secondPreview.path("id").asText());

		UUID concurrentPreviewKey = UUID.randomUUID();
		CountDownLatch requestsReady = new CountDownLatch(2);
		CountDownLatch startRequests = new CountDownLatch(1);
		Callable<MvcResult> createConcurrentPreview = () -> {
			requestsReady.countDown();
			startRequests.await(5, TimeUnit.SECONDS);
			return mockMvc.perform(withTenant(post(
				"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews", fixture.taskId())
				.header("Idempotency-Key", concurrentPreviewKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(previewBody), tenantId)).andReturn();
		};
		ExecutorService executor = Executors.newFixedThreadPool(2);
		MvcResult concurrentFirst;
		MvcResult concurrentSecond;
		try {
			Future<MvcResult> firstFuture = executor.submit(createConcurrentPreview);
			Future<MvcResult> secondFuture = executor.submit(createConcurrentPreview);
			assertThat(requestsReady.await(5, TimeUnit.SECONDS)).isTrue();
			startRequests.countDown();
			concurrentFirst = firstFuture.get(30, TimeUnit.SECONDS);
			concurrentSecond = secondFuture.get(30, TimeUnit.SECONDS);
		}
		finally {
			executor.shutdownNow();
		}
		assertThat(List.of(
			concurrentFirst.getResponse().getStatus(), concurrentSecond.getResponse().getStatus()))
			.containsOnly(200);
		assertThat(Set.of(
			concurrentFirst.getResponse().getHeader("Idempotency-Replayed"),
			concurrentSecond.getResponse().getHeader("Idempotency-Replayed")))
			.containsExactlyInAnyOrder("false", "true");
		assertThat(json(concurrentFirst).at("/data/id").asText())
			.isEqualTo(json(concurrentSecond).at("/data/id").asText());

		Map<String, Object> previewRef = resourceRef("CandidateListPreview", previewId, 1L);
		String staleReviewBody = objectMapper.writeValueAsString(Map.of(
			"previewRef", previewRef,
			"inputHash", inputHash,
			"requiredRole", "RECRUITMENT_MANAGER"));
		mockMvc.perform(withTenant(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody(
				connectorId, "candidate-g4", "resume-v2", "Beijing", "7.0", "REVOKED")), tenantId))
			.andExpect(status().isCreated());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + taskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CANDIDATE_CONSENT_NOT_GRANTED"));
		mockMvc.perform(withTenant(post("/api/core/v1/candidate-inputs")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(candidateBody(
				connectorId, "candidate-g4", "resume-v3", "Beijing", "7.0", "GRANTED")), tenantId))
			.andExpect(status().isCreated());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + (taskVersion + 100) + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + taskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"previewRef", previewRef,
				"inputHash", "0".repeat(64),
				"requiredRole", "RECRUITMENT_MANAGER"))), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFIRMATION_INPUT_CHANGED"));

		String expiringReviewBody = objectMapper.writeValueAsString(Map.of(
			"previewRef", resourceRef("CandidateListPreview", secondPreviewId, 1L),
			"inputHash", inputHash,
			"requiredRole", "RECRUITMENT_MANAGER"));
		MvcResult expiringReviewResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + taskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(expiringReviewBody), tenantId))
			.andExpect(status().isCreated())
			.andReturn();
		UUID expiringCheckpointId = UUID.fromString(json(expiringReviewResponse).at("/data/id").asText());
		jdbcTemplate.update(
			"UPDATE human_checkpoint SET expires_at = ? WHERE tenant_id = ? AND human_checkpoint_id = ?",
			OffsetDateTime.now().minusMinutes(1), tenantId, expiringCheckpointId);
		mockMvc.perform(withTenant(post(
			"/api/core/v1/human-checkpoints/{checkpointId}/decisions", expiringCheckpointId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"decision", "CANCEL",
				"inputHash", inputHash))), tenantId))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("CHECKPOINT_EXPIRED"));
		mockMvc.perform(withTenant(get(
			"/api/core/v1/human-checkpoints/{checkpointId}", expiringCheckpointId), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("EXPIRED"))
			.andExpect(jsonPath("$.data.version").value(2));
		mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}", fixture.taskId()), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.executionStatus").value("IDLE"));
		taskVersion = taskVersion(fixture.taskId(), tenantId);

		MvcResult reviewResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + taskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), tenantId))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.type").value("CONFIRM_CANDIDATE_LIST"))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.summary").isNotEmpty())
			.andReturn();
		UUID checkpointId = UUID.fromString(json(reviewResponse).at("/data/id").asText());
		mockMvc.perform(withTenant(get("/api/core/v1/human-checkpoints")
			.queryParam("taskId", fixture.taskId().toString())
			.queryParam("type", "CONFIRM_CANDIDATE_LIST")
			.queryParam("status", "PENDING")
			.queryParam("limit", "1"), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(checkpointId.toString()))
			.andExpect(jsonPath("$.meta.hasMore").value(false));
		long pendingTaskVersion = taskVersion(fixture.taskId(), tenantId);
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + taskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + pendingTaskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), tenantId))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.id").value(checkpointId.toString()))
			.andExpect(jsonPath("$.data.status").value("PENDING"));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + pendingTaskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(Map.of(
				"previewRef", previewRef,
				"inputHash", inputHash,
				"requiredRole", "HIRING_MANAGER"))), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CHECKPOINT_RESOURCE_CHANGED"));
		UUID decisionKey = UUID.randomUUID();
		String decisionBody = objectMapper.writeValueAsString(Map.of(
			"decision", "APPROVE",
			"inputHash", inputHash,
			"comment", "证据与待核实项已人工复核"));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", decisionKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(decisionBody), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
			.header("If-Match", "\"1\"")
			.header("Idempotency-Key", decisionKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(decisionBody), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));

		long confirmTaskVersion = taskVersion(fixture.taskId(), tenantId);
		String confirmBody = objectMapper.writeValueAsString(Map.of(
			"previewRef", previewRef,
			"checkpointId", checkpointId));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm", fixture.taskId())
			.header("If-Match", "\"" + (confirmTaskVersion + 100) + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
		UUID confirmKey = UUID.randomUUID();
		MvcResult confirmedResponse = mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm", fixture.taskId())
			.header("If-Match", "\"" + confirmTaskVersion + "\"")
			.header("Idempotency-Key", confirmKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody), tenantId))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.versionNo").value(1))
			.andReturn();
		JsonNode confirmed = json(confirmedResponse).path("data");
		UUID candidateListId = UUID.fromString(confirmed.path("id").asText());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm", fixture.taskId())
			.header("If-Match", "\"" + confirmTaskVersion + "\"")
			.header("Idempotency-Key", confirmKey)
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody), tenantId))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(candidateListId.toString()));
		long postConfirmTaskVersion = taskVersion(fixture.taskId(), tenantId);
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm", fixture.taskId())
			.header("If-Match", "\"" + (postConfirmTaskVersion + 100) + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody), tenantId))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

		MvcResult currentListResponse = mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/current", fixture.taskId()), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(candidateListId.toString()))
			.andExpect(jsonPath("$.data.contentHash").value(confirmed.path("contentHash").asText()))
			.andReturn();
		assertThat(json(currentListResponse).at("/data/taskCandidateRefs/0/version").asLong())
			.isEqualTo(taskCandidateVersion + 1);
		MvcResult reportResponse = mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}/recommendation-reports/current", fixture.taskId()), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.candidateListVersionRef.id").value(candidateListId.toString()))
			.andExpect(jsonPath("$.data.positionPlanVersionRef.id").value(fixture.planId().toString()))
			.andExpect(jsonPath("$.data.scorecardVersionRef.id").value(fixture.scorecardId().toString()))
			.andExpect(jsonPath("$.data.matchRunRef.id").value(runId.toString()))
			.andReturn();
		JsonNode report = json(reportResponse).path("data");
		UUID reportId = UUID.fromString(report.path("id").asText());
		JsonNode reportCandidate = report.at("/candidates/0");
		assertThat(reportCandidate.path("selectionReason").asText()).contains("由 HR 纳入本次推荐名单");
		assertThat(reportCandidate.path("note").asText()).isEqualTo("优先核实大型项目职责范围");
		assertThat(reportCandidate.path("needsVerification").isArray()).isTrue();
		assertThat(reportCandidate.path("criteria")).anySatisfy(criterion -> {
			assertThat(criterion.path("sourceEvidenceRefs").isArray()).isTrue();
			assertThat(criterion.path("systemJudgment").asText()).isNotBlank();
		});
		assertThat(reportCandidate.path("criteria")).anySatisfy(criterion ->
			assertThat(criterion.path("sourceEvidenceRefs").isEmpty()).isFalse());

		MvcResult textDownload = mockMvc.perform(withTenant(get(
			"/api/core/v1/recommendation-reports/{reportId}/download", reportId)
			.queryParam("format", "TXT"), tenantId))
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
			.andReturn();
		assertThat(textDownload.getResponse().getContentAsString(StandardCharsets.UTF_8))
			.contains("推荐报告 v1", "选择理由", "优先核实大型项目职责范围", "原文证据", "系统判断", report.path("contentHash").asText());
		MvcResult jsonDownload = mockMvc.perform(withTenant(get(
			"/api/core/v1/recommendation-reports/{reportId}/download", reportId)
			.queryParam("format", "JSON"), tenantId))
			.andExpect(status().isOk())
			.andReturn();
		assertThat(objectMapper.readTree(jsonDownload.getResponse().getContentAsByteArray()).path("id").asText())
			.isEqualTo(reportId.toString());

		mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}", fixture.taskId()), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.businessStage").value("ONLINE_INTERVIEW"));
		assertThat(jdbcTemplate.queryForObject(
			"SELECT selection_status FROM task_candidate WHERE tenant_id = ? AND task_candidate_id = ?",
			String.class, tenantId, taskCandidateId)).isEqualTo("CONFIRMED");
		assertThat(jdbcTemplate.queryForObject(
			"SELECT payload FROM audit_event WHERE tenant_id = ? AND resource_id = ? "
				+ "AND action = 'CANDIDATE_LIST_CONFIRMED'",
			String.class, tenantId, candidateListId.toString())).contains("\"externalActionCreated\":false");

		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Other candidate-list tenant");
		mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/current", fixture.taskId()), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(get(
			"/api/core/v1/recommendation-reports/{reportId}", reportId), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(get(
			"/api/core/v1/candidate-list-previews/{previewId}", previewId), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(get("/api/core/v1/human-checkpoints")
			.queryParam("taskId", fixture.taskId().toString())
			.queryParam("type", "CONFIRM_CANDIDATE_LIST"), otherTenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(0));
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-previews", fixture.taskId())
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(previewBody), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-list-review-requests", fixture.taskId())
			.header("If-Match", "\"" + postConfirmTaskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(staleReviewBody), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
			.header("If-Match", "\"2\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(decisionBody), otherTenant))
			.andExpect(status().isNotFound());
		mockMvc.perform(withTenant(post(
			"/api/core/v1/recruitment-tasks/{taskId}/candidate-lists/confirm", fixture.taskId())
			.header("If-Match", "\"" + postConfirmTaskVersion + "\"")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(confirmBody), otherTenant))
			.andExpect(status().isNotFound());
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

	private long taskVersion(UUID taskId, UUID tenantId) throws Exception {
		return json(mockMvc.perform(withTenant(get(
			"/api/core/v1/recruitment-tasks/{taskId}", taskId), tenantId))
			.andExpect(status().isOk())
			.andReturn()).at("/data/version").asLong();
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
