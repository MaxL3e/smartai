package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ResumeFileLifecycleApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void uploadsParsesNormalizesListsAndVersionsATextResume() throws Exception {
		String externalCandidateId = "candidate-" + UUID.randomUUID();
		byte[] firstContent = (
			"姓名：张伟\n"
				+ "现居地：上海\n"
				+ "邮箱：zhang.wei@example.com\n"
				+ "手机：13800138000\n"
				+ "教育背景\n硕士，计算机科学\n"
				+ "专业技能\nJava，Spring Boot，PostgreSQL\n"
				+ "工作经历\n6年工作经验，负责企业级平台建设。\n")
			.getBytes(StandardCharsets.UTF_8);
		MockMultipartFile firstFile = new MockMultipartFile(
			"file", "zhang-wei.txt", MediaType.TEXT_PLAIN_VALUE, firstContent);
		UUID firstKey = UUID.randomUUID();

		MvcResult created = mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(firstFile)
				.param("sourceSystem", "smartai.resume-library")
				.param("externalCandidateId", externalCandidateId)
				.header("Idempotency-Key", firstKey))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.originalFileName").value("zhang-wei.txt"))
			.andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
			.andExpect(jsonPath("$.data.failureCode").isEmpty())
			.andExpect(jsonPath("$.data.retryable").value(false))
			.andExpect(jsonPath("$.data.parsedProfile.name").value("张伟"))
			.andExpect(jsonPath("$.data.parsedProfile.emails[0]").value("zhang.wei@example.com"))
			.andExpect(jsonPath("$.data.parsedProfile.phones[0]").value("13800138000"))
			.andExpect(jsonPath("$.data.parsedProfile.educationLevel").value("硕士"))
			.andExpect(jsonPath("$.data.parsedProfile.experienceYears").value(6))
			.andExpect(jsonPath("$.data.parsedProfile.skills.length()").value(3))
			.andExpect(jsonPath("$.data.candidate.displayName").value("张伟"))
			.andExpect(jsonPath("$.data.candidate.consentStatus").value("GRANTED"))
			.andExpect(jsonPath("$.data.resumeVersionRef.type").value("ResumeVersion"))
			.andExpect(jsonPath("$.data.candidateReceipt.normalizerKind").value("DETERMINISTIC_NORMALIZER"))
			.andReturn();
		JsonNode createdJson = json(created).path("data");
		UUID resumeFileId = UUID.fromString(createdJson.path("id").asText());
		UUID candidateId = UUID.fromString(createdJson.at("/candidate/id").asText());

		mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(firstFile)
				.param("sourceSystem", "smartai.resume-library")
				.param("externalCandidateId", externalCandidateId)
				.header("Idempotency-Key", firstKey))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(resumeFileId.toString()));

		mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(new MockMultipartFile(
					"file", "changed.txt", MediaType.TEXT_PLAIN_VALUE,
					"姓名：另一个人".getBytes(StandardCharsets.UTF_8)))
				.param("sourceSystem", "smartai.resume-library")
				.param("externalCandidateId", externalCandidateId)
				.header("Idempotency-Key", firstKey))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

		mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(firstFile)
				.param("sourceSystem", "smartai.resume-library")
				.param("externalCandidateId", externalCandidateId)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.id").value(resumeFileId.toString()))
			.andExpect(jsonPath("$.data.fileVersion").value(1));

		byte[] updatedContent = (
			"姓名：张伟\n现居地：杭州\n教育背景\n硕士\n专业技能\nJava，Kubernetes\n工作经历\n7年工作经验\n")
			.getBytes(StandardCharsets.UTF_8);
		MvcResult updated = mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(new MockMultipartFile("file", "zhang-wei-v2.txt", MediaType.TEXT_PLAIN_VALUE, updatedContent))
				.param("sourceSystem", "smartai.resume-library")
				.param("externalCandidateId", externalCandidateId)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(jsonPath("$.data.id").value(resumeFileId.toString()))
			.andExpect(jsonPath("$.data.fileVersion").value(2))
			.andExpect(jsonPath("$.data.candidate.id").value(candidateId.toString()))
			.andExpect(jsonPath("$.data.parsedProfile.location").value("杭州"))
			.andReturn();

		mockMvc.perform(get("/api/core/v1/resume-files/{resumeFileId}", resumeFileId))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(jsonPath("$.data.sha256").value(json(updated).at("/data/sha256").asText()))
			.andExpect(jsonPath("$.data.extractedText").value(org.hamcrest.Matchers.containsString("7年工作经验")))
			.andExpect(jsonPath("$.data.rawBase64").doesNotExist());

		MvcResult listed = mockMvc.perform(get("/api/core/v1/resume-files")
				.param("parseStatus", "PARSED")
				.param("limit", "20"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + resumeFileId + "')].fileVersion").value(2))
			.andReturn();
		JsonNode listedRecord = null;
		for (JsonNode item : json(listed).path("data")) {
			if (resumeFileId.toString().equals(item.path("id").asText())) {
				listedRecord = item;
				break;
			}
		}
		assertThat(listedRecord).isNotNull();
		assertThat(listedRecord.path("extractedText").isNull()).isTrue();
		assertThat(listedRecord.path("evidence").isEmpty()).isTrue();
		assertThat(listedRecord.at("/parsedProfile/emails").isEmpty()).isTrue();
		assertThat(listedRecord.at("/parsedProfile/phones").isEmpty()).isTrue();
		assertThat(listedRecord.path("candidateReceipt").isNull()).isTrue();

		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM resume_file_version WHERE resume_document_id = ?", Integer.class, resumeFileId))
			.isEqualTo(2);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM resume_version WHERE candidate_id = ?", Integer.class, candidateId))
			.isEqualTo(2);
		String rawBase64 = jdbcTemplate.queryForObject(
			"SELECT raw_base64 FROM resume_file_version WHERE resume_document_id = ? AND version_no = 1",
			String.class,
			resumeFileId);
		assertThat(Base64.getDecoder().decode(rawBase64)).isEqualTo(firstContent);
	}

	@Test
	void persistsRealParserFailuresWithoutCreatingCandidateFacts() throws Exception {
		byte[] brokenPdf = "%PDF-1.7\nthis is not a valid PDF body".getBytes(StandardCharsets.US_ASCII);
		MvcResult result = mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(new MockMultipartFile("file", "broken.pdf", MediaType.APPLICATION_PDF_VALUE, brokenPdf))
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.parseStatus").value("PARSE_FAILED"))
			.andExpect(jsonPath("$.data.failureCode").value("RESUME_PARSE_FAILED"))
			.andExpect(jsonPath("$.data.retryable").value(true))
			.andExpect(jsonPath("$.data.extractedText").isEmpty())
			.andExpect(jsonPath("$.data.evidence.length()").value(0))
			.andExpect(jsonPath("$.data.candidate").isEmpty())
			.andExpect(jsonPath("$.data.resumeVersionRef").isEmpty())
			.andExpect(jsonPath("$.data.candidateReceipt").isEmpty())
			.andReturn();

		UUID resumeFileId = UUID.fromString(json(result).at("/data/id").asText());
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM resume_file_version WHERE resume_document_id = ? "
				+ "AND parse_status = 'PARSE_FAILED' AND candidate_receipt_json IS NULL "
				+ "AND normalized_resume_version_id IS NULL",
			Integer.class,
			resumeFileId)).isEqualTo(1);
	}

	@Test
	void isolatesResumeFilesByTenant() throws Exception {
		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant,
			TenantActorResolver.tenantKey(otherTenant),
			"Resume tenant");
		byte[] content = "Name: Tenant Candidate\nSkills\nJava".getBytes(StandardCharsets.UTF_8);
		MvcResult uploaded = mockMvc.perform(multipart("/api/core/v1/resume-files")
				.file(new MockMultipartFile("file", "tenant.txt", MediaType.TEXT_PLAIN_VALUE, content))
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isCreated())
			.andReturn();
		UUID resumeFileId = UUID.fromString(json(uploaded).at("/data/id").asText());

		mockMvc.perform(get("/api/core/v1/resume-files/{resumeFileId}", resumeFileId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.error.code").value("RESUME_FILE_NOT_FOUND"));

		mockMvc.perform(get("/api/core/v1/resume-files"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + resumeFileId + "')]").isEmpty());

		mockMvc.perform(get("/api/core/v1/resume-files/{resumeFileId}", resumeFileId)
				.header(TenantActorResolver.TENANT_HEADER, otherTenant))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value(resumeFileId.toString()));
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}
}
