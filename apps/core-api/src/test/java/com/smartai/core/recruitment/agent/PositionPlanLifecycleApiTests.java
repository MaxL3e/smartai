package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeDocument;
import com.smartai.core.recruitment.agent.KnowledgeModels.KnowledgeVersion;
import com.smartai.core.recruitment.agent.KnowledgeModels.VersionContent;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class PositionPlanLifecycleApiTests {

	private static final String MERGE_PATCH = "application/merge-patch+json";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	KnowledgeRepository knowledgeRepository;

	@Test
	void generatesEditsReviewsAndApprovesTheG2PlanExactlyOnce() throws Exception {
		TaskFixture fixture = createTask(null);
		UUID generateKey = UUID.randomUUID();
		String generationBody = generationBody(fixture, "Prefer evidence that can be verified by HR.");

		MvcResult generated = mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", generateKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody), fixture.tenantId()))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.runType").value("POSITION_PLAN_GENERATION"))
			.andExpect(jsonPath("$.data.status").value("WAITING_HUMAN"))
			.andReturn();
		JsonNode generatedJson = json(generated);
		UUID runId = UUID.fromString(generatedJson.at("/data/id").asText());

		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", generateKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody), fixture.tenantId()))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(runId.toString()));

		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", generateKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture, "A different request must not reuse the key.")), fixture.tenantId()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

		MvcResult currentPlanResult = mockMvc.perform(withTenant(
			get("/api/core/v1/recruitment-tasks/{taskId}/position-plan", fixture.taskId()), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.generatedBy").value("AI"))
			.andExpect(jsonPath("$.data.promptVersion").value("deterministic-demo-v1"))
			.andExpect(jsonPath("$.data.knowledgeVersionRefs.length()").value(0))
			.andExpect(jsonPath("$.data.scorecard.totalScore").value(100))
			.andReturn();
		JsonNode currentPlan = json(currentPlanResult).path("data");
		UUID planId = UUID.fromString(currentPlan.path("id").asText());
		String initialHash = currentPlan.path("contentHash").asText();

		mockMvc.perform(withTenant(
			patch("/api/core/v1/position-plan-versions/{planVersionId}", planId)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"jobDescription\":\"Updated description\"}"), fixture.tenantId()))
			.andExpect(status().isPreconditionRequired())
			.andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));

		UUID patchKey = UUID.randomUUID();
		String patchBody = objectMapper.writeValueAsString(Map.of(
			"jobDescription", "Own the data governance roadmap and measurable delivery outcomes.",
			"changeSummary", "HR refined the accountable outcomes."));
		MvcResult patchedResult = mockMvc.perform(withTenant(
			patch("/api/core/v1/position-plan-versions/{planVersionId}", planId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", patchKey)
				.contentType(MERGE_PATCH)
				.content(patchBody), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(jsonPath("$.data.version").value(2))
			.andExpect(jsonPath("$.data.jobDescription").value(
				"Own the data governance roadmap and measurable delivery outcomes."))
			.andReturn();
		JsonNode patchedPlan = json(patchedResult).path("data");
		String confirmedHash = patchedPlan.path("contentHash").asText();
		assertThat(confirmedHash).isNotEqualTo(initialHash);

		UUID reviewKey = UUID.randomUUID();
		String reviewBody = objectMapper.writeValueAsString(Map.of(
			"requiredRole", "RECRUITMENT_MANAGER",
			"inputHash", confirmedHash,
			"comment", "Please review the complete G2 content."));
		MvcResult reviewResult = mockMvc.perform(withTenant(
			post("/api/core/v1/position-plan-versions/{planVersionId}/review-requests", planId)
				.header("If-Match", "\"2\"")
				.header("Idempotency-Key", reviewKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(reviewBody), fixture.tenantId()))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.type").value("APPROVE_POSITION_PLAN"))
			.andExpect(jsonPath("$.data.status").value("PENDING"))
			.andExpect(jsonPath("$.data.resourceRef.version").value(3))
			.andExpect(jsonPath("$.data.inputHash").value(confirmedHash))
			.andReturn();
		UUID checkpointId = UUID.fromString(json(reviewResult).at("/data/id").asText());

		mockMvc.perform(withTenant(get("/api/core/v1/recruitment-tasks/{taskId}", fixture.taskId()), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.businessStage").value("ROLE_PLAN"))
			.andExpect(jsonPath("$.data.executionStatus").value("WAITING_HUMAN"));

		mockMvc.perform(withTenant(
			patch("/api/core/v1/position-plan-versions/{planVersionId}", planId)
				.header("If-Match", "\"3\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"changeSummary\":\"Must not edit a frozen review.\"}"), fixture.tenantId()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("POSITION_PLAN_STATE_CONFLICT"));

		mockMvc.perform(withTenant(
			get("/api/core/v1/human-checkpoints/{checkpointId}", checkpointId), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.summary").isNotEmpty())
			.andExpect(jsonPath("$.data.inputHash").value(confirmedHash));

		mockMvc.perform(withTenant(
			post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody("0".repeat(64))), fixture.tenantId()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFIRMATION_INPUT_CHANGED"));

		UUID decisionKey = UUID.randomUUID();
		String decisionBody = decisionBody(confirmedHash);
		mockMvc.perform(withTenant(
			post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", decisionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.decision").value("APPROVE"));

		mockMvc.perform(withTenant(
			post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", decisionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));

		mockMvc.perform(withTenant(get("/api/core/v1/recruitment-tasks/{taskId}", fixture.taskId()), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.businessStage").value("TALENT_SEARCH"))
			.andExpect(jsonPath("$.data.executionStatus").value("IDLE"))
			.andExpect(jsonPath("$.data.currentPlanVersionRef.id").value(planId.toString()))
			.andExpect(jsonPath("$.data.currentPlanVersionRef.version").value(4));

		mockMvc.perform(withTenant(get("/api/core/v1/position-plan-versions/{planVersionId}", planId), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"4\""))
			.andExpect(jsonPath("$.data.status").value("APPROVED"))
			.andExpect(jsonPath("$.data.approvalCheckpointRef.id").value(checkpointId.toString()));

		mockMvc.perform(withTenant(get("/api/core/v1/agent-runs/{agentRunId}", runId), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
			.andExpect(jsonPath("$.data.finishedAt").isNotEmpty());

		String generatorKind = jdbcTemplate.queryForObject(
			"SELECT generator_kind FROM agent_run WHERE tenant_id = ? AND agent_run_id = ?",
			String.class, fixture.tenantId(), runId);
		assertThat(generatorKind).isEqualTo("DETERMINISTIC_DEMO");
		String storedPlanJson = jdbcTemplate.queryForObject(
			"SELECT plan_json FROM position_plan_version WHERE tenant_id = ? AND position_plan_version_id = ?",
			String.class, fixture.tenantId(), planId);
		assertThat(storedPlanJson).contains("\"knowledgeVersionRefs\":[]")
			.contains("deterministic-demo-v1");

		Integer auditCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_event WHERE tenant_id = ? AND resource_id IN (?, ?)",
			Integer.class, fixture.tenantId(), planId.toString(), checkpointId.toString());
		assertThat(auditCount).isEqualTo(4);

		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture)), fixture.tenantId()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("TASK_STAGE_CONFLICT"));
	}

	@Test
	void preservesPublishedKnowledgeSnapshotsInRequestOrderAndReplaysGeneration() throws Exception {
		TaskFixture fixture = createTask(null);
		ResourceRef first = createKnowledgeVersion(
			fixture.tenantId(), "PUBLISHED", "PARSED", "INDEXED", 3L);
		ResourceRef second = createKnowledgeVersion(
			fixture.tenantId(), "PUBLISHED", "PARSED", "INDEXED", 5L);
		List<ResourceRef> requestedRefs = List.of(first, second);
		UUID idempotencyKey = UUID.randomUUID();
		String requestBody = generationBody(fixture, requestedRefs, null);

		MvcResult generated = mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody), fixture.tenantId()))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andReturn();
		UUID runId = UUID.fromString(json(generated).at("/data/id").asText());

		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody), fixture.tenantId()))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(runId.toString()));

		MvcResult planResult = mockMvc.perform(withTenant(
			get("/api/core/v1/recruitment-tasks/{taskId}/position-plan", fixture.taskId()), fixture.tenantId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.knowledgeVersionRefs[0].id").value(first.id().toString()))
			.andExpect(jsonPath("$.data.knowledgeVersionRefs[0].version").value(first.version()))
			.andExpect(jsonPath("$.data.knowledgeVersionRefs[1].id").value(second.id().toString()))
			.andExpect(jsonPath("$.data.knowledgeVersionRefs[1].version").value(second.version()))
			.andExpect(jsonPath("$.data.hardConstraints[0].sourceRefs[0].id").value(first.id().toString()))
			.andExpect(jsonPath("$.data.hardConstraints[0].sourceRefs[1].id").value(second.id().toString()))
			.andExpect(jsonPath("$.data.changeSummary").value(org.hamcrest.Matchers.containsString("仅记录引用")))
			.andExpect(jsonPath("$.data.changeSummary").value(
				org.hamcrest.Matchers.containsString("未执行 LLM 或 RAG 内容抽取")))
			.andReturn();

		JsonNode storedRefs = json(planResult).at("/data/knowledgeVersionRefs");
		assertThat(storedRefs).hasSize(2);
		Integer runCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM agent_run WHERE tenant_id = ? AND recruitment_task_id = ?",
			Integer.class, fixture.tenantId(), fixture.taskId());
		assertThat(runCount).isEqualTo(1);
	}

	@Test
	void rejectsKnowledgeThatIsNotPublishedParsedIndexedOrVisibleToTheTenant() throws Exception {
		TaskFixture fixture = createTask(null);
		ResourceRef draft = createKnowledgeVersion(
			fixture.tenantId(), "DRAFT", "PARSED", "INDEXED", 1L);
		ResourceRef parsing = createKnowledgeVersion(
			fixture.tenantId(), "PUBLISHED", "PARSING", "INDEXED", 3L);
		ResourceRef notIndexed = createKnowledgeVersion(
			fixture.tenantId(), "PUBLISHED", "PARSED", "NOT_INDEXED", 3L);

		for (ResourceRef unavailable : List.of(draft, parsing, notIndexed)) {
			mockMvc.perform(withTenant(
				post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
					.header("Idempotency-Key", UUID.randomUUID())
					.contentType(MediaType.APPLICATION_JSON)
					.content(generationBody(fixture, List.of(unavailable), null)), fixture.tenantId()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.code").value("KNOWLEDGE_VERSION_NOT_AVAILABLE"));
		}

		ResourceRef wrongType = new ResourceRef("KnowledgeDocument", draft.id(), draft.version());
		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture, List.of(wrongType), null)), fixture.tenantId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Knowledge owner tenant");
		ResourceRef crossTenant = createKnowledgeVersion(
			otherTenant, "PUBLISHED", "PARSED", "INDEXED", 3L);
		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture, List.of(crossTenant), null)), fixture.tenantId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void validatesScorecardAndDoesNotPretendUnavailableKnowledgeWasRetrieved() throws Exception {
		TaskFixture fixture = createTask(null);
		ResourceRef unavailable = createKnowledgeVersion(
			fixture.tenantId(), "DRAFT", "PARSED", "INDEXED", 1L);
		String fakeKnowledgeBody = objectMapper.writeValueAsString(Map.of(
			"requirementDraftRef", resourceRef("RequirementDraft", fixture.draftId(), fixture.draftVersion()),
			"knowledgeVersionRefs", List.of(unavailable)));
		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(fakeKnowledgeBody), fixture.tenantId()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("KNOWLEDGE_VERSION_NOT_AVAILABLE"));

		mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture)), fixture.tenantId()))
			.andExpect(status().isAccepted());
		MvcResult planResult = mockMvc.perform(withTenant(
			get("/api/core/v1/recruitment-tasks/{taskId}/position-plan", fixture.taskId()), fixture.tenantId()))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode plan = json(planResult).path("data");
		UUID planId = UUID.fromString(plan.path("id").asText());
		ObjectNode invalidScorecard = plan.path("scorecard").deepCopy();
		((ObjectNode) invalidScorecard.path("criteria").get(0)).put("weight", 39);
		ObjectNode body = objectMapper.createObjectNode();
		body.set("scorecard", invalidScorecard);
		body.put("changeSummary", "Invalid total must be rejected.");

		mockMvc.perform(withTenant(
			patch("/api/core/v1/position-plan-versions/{planVersionId}", planId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content(objectMapper.writeValueAsString(body)), fixture.tenantId()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
	}

	@Test
	void hidesPlansRunsAndCheckpointsAcrossTenants() throws Exception {
		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Other tenant");
		TaskFixture fixture = createTask(otherTenant);
		MvcResult generated = mockMvc.perform(withTenant(
			post("/api/core/v1/recruitment-tasks/{taskId}/position-plan/generations", fixture.taskId())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(generationBody(fixture)), otherTenant))
			.andExpect(status().isAccepted())
			.andReturn();
		UUID runId = UUID.fromString(json(generated).at("/data/id").asText());
		MvcResult planResult = mockMvc.perform(withTenant(
			get("/api/core/v1/recruitment-tasks/{taskId}/position-plan", fixture.taskId()), otherTenant))
			.andExpect(status().isOk())
			.andReturn();
		UUID planId = UUID.fromString(json(planResult).at("/data/id").asText());

		mockMvc.perform(get("/api/core/v1/recruitment-tasks/{taskId}/position-plan", fixture.taskId()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(get("/api/core/v1/position-plan-versions/{planVersionId}", planId))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/core/v1/agent-runs/{agentRunId}", runId))
			.andExpect(status().isNotFound());
	}

	private TaskFixture createTask(UUID tenantId) throws Exception {
		UUID effectiveTenant = tenantId == null ? TenantActorResolver.DEMO_TENANT_ID : tenantId;
		MvcResult createdResult = mockMvc.perform(withTenant(
			post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"input", "Create a complete recruitment task for the G2 position plan lifecycle test."))), tenantId))
			.andExpect(status().isCreated())
			.andReturn();
		UUID draftId = UUID.fromString(json(createdResult).at("/data/id").asText());

		Map<String, Object> fields = new LinkedHashMap<>();
		fields.put("positionName", confirmedField("Data Governance Specialist"));
		fields.put("organizationRef", confirmedField("Digital Technology Department"));
		fields.put("locations", confirmedField(List.of("Beijing")));
		fields.put("headcount", confirmedField(2));
		fields.put("coreRequirements", confirmedField(List.of(
			"Five years of enterprise data governance experience",
			"Evidence of master data and data standard delivery")));
		String patchBody = objectMapper.writeValueAsString(Map.of("fields", fields));
		MvcResult patched = mockMvc.perform(withTenant(
			patch("/api/core/v1/requirement-drafts/{draftId}", draftId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content(patchBody), tenantId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("READY"))
			.andReturn();
		String inputHash = patched.getResponse().getHeader("X-SmartAI-Input-Hash");
		String convertBody = objectMapper.writeValueAsString(Map.of(
			"confirmation", Map.of(
				"confirmed", true,
				"inputHash", inputHash,
				"comment", "HR confirmed the source requirement."),
			"ownerUserId", TenantActorResolver.DEMO_USER_ID,
			"participantUserIds", List.of()));
		MvcResult converted = mockMvc.perform(withTenant(
			post("/api/core/v1/requirement-drafts/{draftId}/convert", draftId)
				.header("If-Match", "\"2\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody), tenantId))
			.andExpect(status().isCreated())
			.andReturn();
		UUID taskId = UUID.fromString(json(converted).at("/data/id").asText());
		MvcResult sourceDraft = mockMvc.perform(withTenant(
			get("/api/core/v1/requirement-drafts/{draftId}", draftId), tenantId))
			.andExpect(status().isOk())
			.andReturn();
		long draftVersion = json(sourceDraft).at("/data/version").asLong();
		return new TaskFixture(effectiveTenant, draftId, draftVersion, taskId);
	}

	private String generationBody(TaskFixture fixture, String instructions) throws Exception {
		return generationBody(fixture, List.of(), instructions);
	}

	private String generationBody(
			TaskFixture fixture,
			List<ResourceRef> knowledgeVersionRefs,
			String instructions) throws Exception {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("requirementDraftRef", resourceRef(
			"RequirementDraft", fixture.draftId(), fixture.draftVersion()));
		body.put("knowledgeVersionRefs", knowledgeVersionRefs);
		if (instructions != null) body.put("instructions", instructions);
		return objectMapper.writeValueAsString(body);
	}

	private String generationBody(TaskFixture fixture) throws Exception {
		return generationBody(fixture, null);
	}

	private String decisionBody(String inputHash) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
			"decision", "APPROVE",
			"inputHash", inputHash,
			"comment", "Recruitment manager approved the exact frozen plan."));
	}

	private static Map<String, Object> confirmedField(Object value) {
		return Map.of(
			"value", value,
			"confidence", new BigDecimal("1.0"),
			"source", "USER",
			"needsConfirmation", false,
			"evidence", "Confirmed for the G2 lifecycle test");
	}

	private static Map<String, Object> resourceRef(String type, UUID id, long version) {
		return Map.of("type", type, "id", id, "version", version);
	}

	private ResourceRef createKnowledgeVersion(
			UUID tenantId,
			String publicationStatus,
			String parseStatus,
			String indexStatus,
			long entityVersion) {
		OffsetDateTime now = OffsetDateTime.now();
		UUID documentId = UUID.randomUUID();
		KnowledgeDocument document = new KnowledgeDocument(
			documentId,
			"Position plan test knowledge",
			"JOB_KNOWLEDGE",
			new ResourceRef("Organization", UUID.randomUUID(), 1L),
			"INTERNAL",
			"DRAFT",
			1L,
			List.of("position-plan-test"),
			null,
			null,
			null,
			now,
			now);
		knowledgeRepository.insertDocument(tenantId, document, TenantActorResolver.DEMO_USER_ID.toString());

		KnowledgeVersion version = new KnowledgeVersion(
			UUID.randomUUID(),
			documentId,
			1,
			entityVersion,
			"position-plan-test.md",
			"text/markdown",
			"1".repeat(64),
			"2".repeat(64),
			publicationStatus,
			parseStatus,
			indexStatus,
			"plain-text-v1",
			null,
			null,
			null,
			null,
			null,
			null,
			now);
		knowledgeRepository.insertVersion(
			tenantId,
			new VersionContent(version, 22L, "Position plan evidence", "Test fixture"),
			List.of(),
			TenantActorResolver.DEMO_USER_ID.toString(),
			now);
		return new ResourceRef("KnowledgeVersion", version.id(), version.version());
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static MockHttpServletRequestBuilder withTenant(
			MockHttpServletRequestBuilder request,
			UUID tenantId) {
		if (tenantId != null) request.header(TenantActorResolver.TENANT_HEADER, tenantId);
		return request;
	}

	private record TaskFixture(UUID tenantId, UUID draftId, long draftVersion, UUID taskId) {
	}
}
