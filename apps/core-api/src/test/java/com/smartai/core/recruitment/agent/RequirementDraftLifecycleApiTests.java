package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
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

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RequirementDraftLifecycleApiTests {

	private static final String MERGE_PATCH = "application/merge-patch+json";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void parsesSubjectOrganizationAndChineseTargetDateFromTheRealDemoPhrase() throws Exception {
		CreatedDraft created = createDraft(
			"数字科技部需要在北京紧急招聘2名数据治理专家，希望2026年8月31日前到岗，"
				+ "要求熟悉主数据管理和数据标准，并参考历史JD和人才画像。",
			null);

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fields.organizationRef.value").value("数字科技部"))
			.andExpect(jsonPath("$.data.fields.targetDate.value").value("2026-08-31"))
			.andExpect(jsonPath("$.data.fields.headcount.value").value(2))
			.andExpect(jsonPath("$.data.fields.positionName.value").value("数据治理专家"));
	}

	@Test
	void parsesPlannedChineseTargetDateWithoutAHopePrefix() throws Exception {
		CreatedDraft created = createDraft(
			"数字科技部计划在北京招聘2名数据治理专家，计划2026年8月31日前到岗，"
				+ "要求具有大型企业数据治理项目经验。",
			null);

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fields.targetDate.value").value("2026-08-31"))
			.andExpect(jsonPath("$.data.fields.targetDate.needsConfirmation").value(false));
	}

	@Test
	void parsesPriorityHighInNaturalWordOrder() throws Exception {
		CreatedDraft created = createDraft(
			"数字科技部在北京招聘2名数据治理专家，计划2026年8月31日前到岗，优先级高。",
			null);

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.fields.priority.value").value("HIGH"))
			.andExpect(jsonPath("$.data.fields.priority.needsConfirmation").value(false));
	}

	@Test
	void createsAndConvertsTheNaturalLanguagePhraseShownInTheTaskDialog() throws Exception {
		String input = "数字科技部想在北京紧急招聘2名数据治理专家，希望8月底前到岗，"
			+ "要求有大型企业项目经验。";
		CreatedDraft created = createDraft(input, null);
		LocalDate today = LocalDate.now(ZoneOffset.UTC);
		YearMonth expectedMonth = YearMonth.of(today.getYear(), 8);
		if (expectedMonth.atEndOfMonth().isBefore(today)) expectedMonth = expectedMonth.plusYears(1);

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("READY"))
			.andExpect(jsonPath("$.data.fields.organizationRef.value").value("数字科技部"))
			.andExpect(jsonPath("$.data.fields.targetDate.value").value(expectedMonth.atEndOfMonth().toString()));

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.positionName").value("数据治理专家"))
			.andExpect(jsonPath("$.data.targetDate").value(expectedMonth.atEndOfMonth().toString()));
	}

	@Test
	void patchesWithOptimisticLockingAndReplaysTheFirstPatchResult() throws Exception {
		CreatedDraft created = createDraft(
			"数字科技部需要在北京招聘2名数据治理专家，要求熟悉主数据管理。",
			null);
		UUID idempotencyKey = UUID.randomUUID();
		String patchBody = "{\"fields\":{\"priority\":{\"value\":\"URGENT\",\"confidence\":1,"
			+ "\"source\":\"USER\",\"needsConfirmation\":false,\"evidence\":\"HR确认紧急\"}}}";

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MERGE_PATCH)
				.content(patchBody))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(header().exists("X-SmartAI-Input-Hash"))
			.andExpect(jsonPath("$.data.version").value(2))
			.andExpect(jsonPath("$.data.status").value("READY"))
			.andExpect(jsonPath("$.data.fields.priority.value").value("URGENT"));

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MERGE_PATCH)
				.content(patchBody))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.version").value(2));

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MERGE_PATCH)
				.content("{\"rawInput\":\"这是另一个不同的招聘需求输入，不能复用相同幂等键。\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"2\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFIRMATION_INPUT_CHANGED"));
	}

	@Test
	void enforcesIfMatchForPatch() throws Exception {
		CreatedDraft created = createDraft("请为数字科技部在北京招聘一名Java工程师，要求熟悉Spring。", null);
		String body = "{\"rawInput\":\"请为数字科技部在北京招聘两名Java工程师，要求熟悉Spring。\"}";

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content(body))
			.andExpect(status().isPreconditionRequired())
			.andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"));

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("If-Match", "\"9\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
	}

	@Test
	void convertsConfirmedDraftExactlyOnceAndPersistsTheTaskAndCheckpoint() throws Exception {
		CreatedDraft created = createDraft(
			"请为数字科技部在北京招聘2名Java开发工程师，社招，高优先级，目标到岗2026-10-01，"
				+ "要求5年以上Java经验。",
			null);
		UUID convertKey = UUID.randomUUID();
		String body = convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID);

		MvcResult result = mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", convertKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.positionName").value("Java开发工程师"))
			.andExpect(jsonPath("$.data.organizationRef.type").value("Organization"))
			.andExpect(jsonPath("$.data.headcount").value(2))
			.andExpect(jsonPath("$.data.locations[0]").value("北京"))
			.andExpect(jsonPath("$.data.businessStage").value("ROLE_PLAN"))
			.andExpect(jsonPath("$.data.lifecycleStatus").value("ACTIVE"))
			.andExpect(jsonPath("$.data.creationCheckpointRef.type").value("HumanCheckpoint"))
			.andReturn();
		String taskId = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText();

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", convertKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(taskId));

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(jsonPath("$.data.status").value("CONVERTED"))
			.andExpect(jsonPath("$.data.convertedTaskRef.id").value(taskId));

		mockMvc.perform(get("/api/core/v1/recruitment-tasks/{taskId}", taskId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(taskId));

		Integer checkpoints = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM human_checkpoint WHERE tenant_id = ? AND recruitment_task_id = ? "
				+ "AND checkpoint_type = 'CREATE_TASK' AND status = 'APPROVED'",
			Integer.class,
			TenantActorResolver.DEMO_TENANT_ID,
			UUID.fromString(taskId));
		assertThat(checkpoints).isEqualTo(1);

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"2\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("DRAFT_ALREADY_CONVERTED"));

		mockMvc.perform(patch("/api/core/v1/requirement-drafts/{draftId}", created.id())
				.header("If-Match", "\"2\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"rawInput\":\"转换后的需求草案不允许再次修改，这是一段足够长的输入。\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("RESOURCE_STATE_CONFLICT"));
	}

	@Test
	void rejectsStaleConfirmationHashAndHidesTasksAcrossTenants() throws Exception {
		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, ?)",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Lifecycle tenant", "ACTIVE");
		CreatedDraft created = createDraft(
			"请为数字科技部在上海招聘一名产品经理，社招，要求负责招聘产品规划。",
			otherTenant);

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("If-Match", "\"9\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody("0".repeat(64), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFIRMATION_INPUT_CHANGED"));

		MvcResult converted = mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isCreated())
			.andReturn();
		String taskId = objectMapper.readTree(converted.getResponse().getContentAsString()).at("/data/id").asText();

		mockMvc.perform(get("/api/core/v1/recruitment-tasks/{taskId}", taskId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
	}

	@Test
	void refusesToConvertAnExpiredDraft() throws Exception {
		CreatedDraft created = createDraft(
			"请为数字科技部在北京招聘一名安全工程师，社招，要求熟悉应用安全。",
			null);
		jdbcTemplate.update(
			"UPDATE requirement_draft SET expires_at = ? WHERE requirement_draft_id = ?",
			OffsetDateTime.now().minusHours(1),
			created.id());

		mockMvc.perform(post("/api/core/v1/requirement-drafts/{draftId}/convert", created.id())
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(convertBody(created.inputHash(), TenantActorResolver.DEMO_USER_ID)))
			.andExpect(status().isGone())
			.andExpect(jsonPath("$.error.code").value("DRAFT_EXPIRED"));

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", created.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("EXPIRED"));
	}

	private CreatedDraft createDraft(String input, UUID tenantId) throws Exception {
		var request = post("/api/core/v1/requirement-drafts")
			.header("Idempotency-Key", UUID.randomUUID())
			.contentType(MediaType.APPLICATION_JSON)
			.content(objectMapper.writeValueAsString(new CreateBody(input)));
		if (tenantId != null) request.header(TenantActorResolver.TENANT_HEADER, tenantId);
		MvcResult result = mockMvc.perform(request)
			.andExpect(status().isCreated())
			.andExpect(header().exists("X-SmartAI-Input-Hash"))
			.andReturn();
		return new CreatedDraft(
			UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).at("/data/id").asText()),
			result.getResponse().getHeader("X-SmartAI-Input-Hash"));
	}

	private static String convertBody(String inputHash, UUID ownerId) {
		return "{\"confirmation\":{\"confirmed\":true,\"inputHash\":\"" + inputHash
			+ "\",\"comment\":\"HR已核对当前草案\"},\"ownerUserId\":\"" + ownerId
			+ "\",\"participantUserIds\":[]}";
	}

	private record CreateBody(String input) {
	}

	private record CreatedDraft(UUID id, String inputHash) {
	}
}
