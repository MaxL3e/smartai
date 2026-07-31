package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RequirementDraftApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void createsAndReadsAParsedRequirementDraft() throws Exception {
		String input = "请为数字科技部在北京招聘3名Java开发工程师，社招，紧急，目标到岗2026-10-01，"
			+ "要求5年以上Java经验，熟悉Spring和微服务，并参考历史JD和人才画像。";

		MvcResult result = mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RequestBody(input))))
			.andExpect(status().isCreated())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("READY"))
			.andExpect(jsonPath("$.data.rawInput").value(input))
			.andExpect(jsonPath("$.data.fields.positionName.value").value("Java开发工程师"))
			.andExpect(jsonPath("$.data.fields.positionName.source").value("USER"))
			.andExpect(jsonPath("$.data.fields.positionName.needsConfirmation").value(false))
			.andExpect(jsonPath("$.data.fields.organizationRef.value").value("数字科技部"))
			.andExpect(jsonPath("$.data.fields.locations.value[0]").value("北京"))
			.andExpect(jsonPath("$.data.fields.headcount.value").value(3))
			.andExpect(jsonPath("$.data.fields.recruitmentType.value").value("SOCIAL"))
			.andExpect(jsonPath("$.data.fields.priority.value").value("HIGH"))
			.andExpect(jsonPath("$.data.fields.targetDate.value").value("2026-10-01"))
			.andExpect(jsonPath("$.data.fields.coreRequirements.value[0]").value("5年以上Java经验"))
			.andExpect(jsonPath("$.data.fields.knowledgeScope.value[0]").value("JOB_DESCRIPTION_HISTORY"))
			.andExpect(jsonPath("$.data.createdBy.displayName").value("演示 HR"))
			.andReturn();

		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
		UUID draftId = UUID.fromString(response.at("/data/id").asText());
		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", draftId))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.id").value(draftId.toString()))
			.andExpect(jsonPath("$.data.rawInput").value(input));

		String stored = jdbcTemplate.queryForObject(
			"SELECT raw_input_ciphertext FROM requirement_draft WHERE requirement_draft_id = ?",
			String.class,
			draftId);
		assertThat(stored).doesNotContain(input).doesNotContain("Java开发工程师");
	}

	@Test
	void marksMissingAndDefaultedFieldsForHumanConfirmation() throws Exception {
		String input = "请帮我们招聘一名数据分析师，主要负责经营数据分析。";

		mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(new RequestBody(input))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.fields.organizationRef.value").doesNotExist())
			.andExpect(jsonPath("$.data.fields.organizationRef.confidence").value(0))
			.andExpect(jsonPath("$.data.fields.organizationRef.source").value("AI"))
			.andExpect(jsonPath("$.data.fields.organizationRef.needsConfirmation").value(true))
			.andExpect(jsonPath("$.data.fields.headcount.value").value(1))
			.andExpect(jsonPath("$.data.fields.recruitmentType.source").value("DEFAULT"))
			.andExpect(jsonPath("$.data.fields.recruitmentType.needsConfirmation").value(true))
			.andExpect(jsonPath("$.data.fields.priority.source").value("DEFAULT"))
			.andExpect(jsonPath("$.data.fields.knowledgeScope.source").value("DEFAULT"))
			.andExpect(jsonPath("$.data.fields.knowledgeScope.needsConfirmation").value(true));
	}

	@Test
	void rejectsInputShorterThanTheContractMinimum() throws Exception {
		mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"input\":\"招人\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.details[0].field").value("input"));
	}

	@Test
	void doesNotExposeDraftsAcrossTenantBoundaries() throws Exception {
		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, ?)",
			otherTenant,
			TenantActorResolver.tenantKey(otherTenant),
			"Other tenant",
			"ACTIVE");

		MvcResult created = mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"input\":\"请招聘一名上海的产品经理，负责智能招聘产品。\"}"))
			.andExpect(status().isCreated())
			.andReturn();
		UUID draftId = UUID.fromString(objectMapper.readTree(created.getResponse().getContentAsString())
			.at("/data/id").asText());

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", draftId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));

		mockMvc.perform(get("/api/core/v1/requirement-drafts/{draftId}", draftId)
				.header(TenantActorResolver.TENANT_HEADER, otherTenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(draftId.toString()));
	}

	@Test
	void replaysTheFirstResultAndRejectsAConflictingPayload() throws Exception {
		UUID idempotencyKey = UUID.randomUUID();
		String firstPayload = "{\"input\":\"请招聘一名北京的算法工程师，负责推荐算法开发。\"}";
		MvcResult first = mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(firstPayload))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andReturn();
		String firstId = objectMapper.readTree(first.getResponse().getContentAsString()).at("/data/id").asText();

		mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(firstPayload))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(firstId));

		mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"input\":\"请招聘一名上海的前端工程师，负责管理后台开发。\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));
	}

	@Test
	void requiresAnIdempotencyKeyForCreate() throws Exception {
		mockMvc.perform(post("/api/core/v1/requirement-drafts")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"input\":\"请招聘一名北京的产品经理，负责招聘产品。\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.error.details[0].field").value("Idempotency-Key"));
	}

	private record RequestBody(String input) {
	}
}
