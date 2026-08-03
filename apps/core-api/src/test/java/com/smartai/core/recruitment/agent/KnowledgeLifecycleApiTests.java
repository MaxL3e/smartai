package com.smartai.core.recruitment.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
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
class KnowledgeLifecycleApiTests {

	private static final String MERGE_PATCH = "application/merge-patch+json";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void managesParsedKnowledgeThroughReviewPublicationAndArchive() throws Exception {
		UUID createKey = UUID.randomUUID();
		String createBody = createBody("Data governance role standard", "JOB_KNOWLEDGE", "INTERNAL");
		MvcResult created = mockMvc.perform(post("/api/core/v1/knowledge-documents")
				.header("Idempotency-Key", createKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("DRAFT"))
			.andExpect(jsonPath("$.data.currentVersion").isEmpty())
			.andReturn();
		UUID documentId = UUID.fromString(json(created).at("/data/id").asText());

		mockMvc.perform(post("/api/core/v1/knowledge-documents")
				.header("Idempotency-Key", createKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(documentId.toString()));

		mockMvc.perform(post("/api/core/v1/knowledge-documents")
				.header("Idempotency-Key", createKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody("Different title", "JOB_KNOWLEDGE", "INTERNAL")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"title\":\"Updated title\"}"))
			.andExpect(status().isPreconditionRequired());

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"title\":\"Updated role standard\",\"tags\":[\"JD\",\"governance\"]}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"2\""))
			.andExpect(jsonPath("$.data.title").value("Updated role standard"))
			.andExpect(jsonPath("$.data.tags.length()").value(2));

		byte[] content = (
			"Role purpose\nOwn enterprise data governance delivery.\n\n"
				+ "Required evidence\nFive years of governance experience and measurable delivery outcomes.")
			.getBytes(StandardCharsets.UTF_8);
		String sha256 = sha256(content);
		MockMultipartFile file = new MockMultipartFile(
			"file", "data-governance.md", "text/markdown", content);
		UUID versionKey = UUID.randomUUID();
		MvcResult uploaded = mockMvc.perform(multipart(
				"/api/core/v1/knowledge-documents/{documentId}/versions", documentId)
				.file(file)
				.param("sha256", sha256)
				.param("changeSummary", "Initial controlled version")
				.header("Idempotency-Key", versionKey))
			.andExpect(status().isAccepted())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.publicationStatus").value("DRAFT"))
			.andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
			.andExpect(jsonPath("$.data.indexStatus").value("INDEXED"))
			.andExpect(jsonPath("$.data.parserVersion").value("deterministic-text-v1"))
			.andReturn();
		JsonNode versionJson = json(uploaded).path("data");
		UUID versionId = UUID.fromString(versionJson.path("id").asText());
		String contentHash = versionJson.path("contentHash").asText();

		mockMvc.perform(get("/api/core/v1/knowledge-documents")
				.param("type", "JOB_KNOWLEDGE")
				.param("status", "DRAFT"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(documentId.toString()))
			.andExpect(jsonPath("$.data[0].currentVersion.id").value(versionId.toString()));

		String reviewBody = objectMapper.writeValueAsString(Map.of(
			"requiredRole", "KNOWLEDGE_ADMIN",
			"inputHash", contentHash,
			"comment", "Content and classification checked."));
		MvcResult review = mockMvc.perform(post(
				"/api/core/v1/knowledge-versions/{knowledgeVersionId}/review-requests", versionId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(reviewBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.type").value("PUBLISH_KNOWLEDGE"))
			.andExpect(jsonPath("$.data.requiredRole").value("KNOWLEDGE_ADMIN"))
			.andExpect(jsonPath("$.data.resourceRef.version").value(2))
			.andReturn();
		UUID checkpointId = UUID.fromString(json(review).at("/data/id").asText());

		mockMvc.perform(post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody("0".repeat(64))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("CONFIRMATION_INPUT_CHANGED"));

		UUID decisionKey = UUID.randomUUID();
		String decisionBody = decisionBody(contentHash);
		mockMvc.perform(post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", decisionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.status").value("APPROVED"));

		mockMvc.perform(post("/api/core/v1/human-checkpoints/{checkpointId}/decisions", checkpointId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", decisionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(decisionBody))
			.andExpect(status().isOk())
			.andExpect(header().string("Idempotency-Replayed", "true"));

		mockMvc.perform(get("/api/core/v1/knowledge-versions/{knowledgeVersionId}", versionId))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"3\""))
			.andExpect(jsonPath("$.data.publicationStatus").value("PUBLISHED"))
			.andExpect(jsonPath("$.data.approvalCheckpointRef.id").value(checkpointId.toString()));

		UUID evidenceId = jdbcTemplate.queryForObject(
			"SELECT knowledge_chunk_id FROM knowledge_chunk WHERE knowledge_version_id = ? ORDER BY chunk_no LIMIT 1",
			UUID.class,
			versionId);
		mockMvc.perform(get("/api/core/v1/knowledge-evidence/{evidenceId}", evidenceId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.accessLevel").value("FULL_TEXT"))
			.andExpect(jsonPath("$.data.knowledgeVersionRef.id").value(versionId.toString()))
			.andExpect(jsonPath("$.data.quote").isNotEmpty());

		mockMvc.perform(post("/api/core/v1/knowledge-versions/{knowledgeVersionId}/deactivate", versionId)
				.header("If-Match", "\"3\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"reason\":\"Policy owner requested a temporary pause.\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"4\""))
			.andExpect(jsonPath("$.data.publicationStatus").value("DISABLED"));

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("If-Match", "\"6\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"statusCommand\":\"RESTORE\",\"reason\":\"Owner reconfirmed validity.\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"7\""))
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"))
			.andExpect(jsonPath("$.data.currentVersion.publicationStatus").value("PUBLISHED"));

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("If-Match", "\"7\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"statusCommand\":\"ARCHIVE\",\"reason\":\"Superseded by a new policy.\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"8\""))
			.andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("If-Match", "\"8\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"statusCommand\":\"RESTORE\",\"reason\":\"Archive was created in error.\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"9\""))
			.andExpect(jsonPath("$.data.status").value("PUBLISHED"));

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", documentId)
				.header("If-Match", "\"9\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"statusCommand\":\"ARCHIVE\",\"reason\":\"Superseded by a new policy.\"}"))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"10\""))
			.andExpect(jsonPath("$.data.status").value("ARCHIVED"));

		MockMultipartFile rejectedFile = new MockMultipartFile(
			"file", "replacement.txt", "text/plain", content);
		mockMvc.perform(multipart("/api/core/v1/knowledge-documents/{documentId}/versions", documentId)
				.file(rejectedFile)
				.param("sha256", sha256)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("KNOWLEDGE_DOCUMENT_ARCHIVED"));

		Integer auditCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_event WHERE tenant_id = ? AND resource_id IN (?, ?, ?)",
			Integer.class,
			TenantActorResolver.DEMO_TENANT_ID,
			documentId.toString(),
			versionId.toString(),
			checkpointId.toString());
		assertThat(auditCount).isGreaterThanOrEqualTo(7);
	}

	@Test
	void completesAConstrainedDirectUploadExactlyOnce() throws Exception {
		UUID documentId = createDocument("Talent profile", "TALENT_PROFILE", "CONFIDENTIAL", null);
		byte[] content = "Successful hires demonstrate evidence-led stakeholder leadership."
			.getBytes(StandardCharsets.UTF_8);
		String sha256 = sha256(content);
		String sessionBody = objectMapper.writeValueAsString(Map.of(
			"fileName", "profile.txt",
			"mimeType", "text/plain",
			"sizeBytes", content.length,
			"sha256", sha256));
		MvcResult created = mockMvc.perform(post(
				"/api/core/v1/knowledge-documents/{documentId}/upload-sessions", documentId)
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(sessionBody))
			.andExpect(status().isCreated())
			.andExpect(header().string("ETag", "\"1\""))
			.andReturn();
		UUID uploadId = UUID.fromString(json(created).at("/data/id").asText());

		mockMvc.perform(put("/api/core/v1/knowledge-upload-sessions/{uploadSessionId}/content", uploadId)
				.contentType(MediaType.TEXT_PLAIN)
				.header("X-Content-SHA256", sha256)
				.content(content))
			.andExpect(status().isOk())
			.andExpect(header().string("ETag", "\"1\""))
			.andExpect(jsonPath("$.data.status").value("UPLOADED"));

		String completionBody = objectMapper.writeValueAsString(Map.of(
			"sizeBytes", content.length,
			"sha256", sha256,
			"changeSummary", "Initial talent profile"));
		UUID completionKey = UUID.randomUUID();
		MvcResult completed = mockMvc.perform(post(
				"/api/core/v1/knowledge-upload-sessions/{uploadSessionId}/complete", uploadId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", completionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(completionBody))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "false"))
			.andExpect(jsonPath("$.data.parseStatus").value("PARSED"))
			.andReturn();
		UUID versionId = UUID.fromString(json(completed).at("/data/id").asText());

		mockMvc.perform(post("/api/core/v1/knowledge-upload-sessions/{uploadSessionId}/complete", uploadId)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", completionKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(completionBody))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Idempotency-Replayed", "true"))
			.andExpect(jsonPath("$.data.id").value(versionId.toString()));
	}

	@Test
	void isolatesTenantsAndRejectsStaleWritesAndInvalidFiles() throws Exception {
		UUID otherTenant = UUID.randomUUID();
		jdbcTemplate.update(
			"INSERT INTO tenant (tenant_id, tenant_key, display_name, status) VALUES (?, ?, ?, 'ACTIVE')",
			otherTenant, TenantActorResolver.tenantKey(otherTenant), "Other tenant");
		UUID foreignDocument = createDocument(
			"Restricted policy", "POLICY_PROCESS", "RESTRICTED", otherTenant);

		mockMvc.perform(get("/api/core/v1/knowledge-documents/{documentId}", foreignDocument))
			.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/core/v1/knowledge-documents"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[?(@.id == '" + foreignDocument + "')]").isEmpty());

		mockMvc.perform(patch("/api/core/v1/knowledge-documents/{documentId}", foreignDocument)
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("If-Match", "\"99\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MERGE_PATCH)
				.content("{\"title\":\"Stale update\"}"))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));

		byte[] content = "%PDF-1.7 binary placeholder".getBytes(StandardCharsets.UTF_8);
		MockMultipartFile file = new MockMultipartFile(
			"file", "policy.pdf", "application/pdf", content);
		MvcResult acceptedBinary = mockMvc.perform(multipart(
				"/api/core/v1/knowledge-documents/{documentId}/versions", foreignDocument)
				.file(file)
				.param("sha256", sha256(content))
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.data.parseStatus").value("PARSE_FAILED"))
			.andExpect(jsonPath("$.data.indexStatus").value("NOT_INDEXED"))
			.andExpect(jsonPath("$.data.failureCode").value("PARSER_NOT_CONFIGURED"))
			.andReturn();
		UUID binaryVersionId = UUID.fromString(json(acceptedBinary).at("/data/id").asText());
		String binaryContentHash = json(acceptedBinary).at("/data/contentHash").asText();
		String storedPayload = jdbcTemplate.queryForObject(
			"SELECT content_text FROM knowledge_version WHERE tenant_id = ? AND knowledge_version_id = ?",
			String.class,
			otherTenant,
			binaryVersionId);
		assertThat(storedPayload).startsWith("base64:");

		mockMvc.perform(post(
				"/api/core/v1/knowledge-versions/{knowledgeVersionId}/review-requests", binaryVersionId)
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("If-Match", "\"1\"")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(Map.of(
					"requiredRole", "KNOWLEDGE_ADMIN",
					"inputHash", binaryContentHash))))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.error.code").value("KNOWLEDGE_VERSION_STATE_CONFLICT"));

		MockMultipartFile unsupported = new MockMultipartFile(
			"file", "image.png", "image/png", content);
		mockMvc.perform(multipart(
				"/api/core/v1/knowledge-documents/{documentId}/versions", foreignDocument)
				.file(unsupported)
				.param("sha256", sha256(content))
				.header(TenantActorResolver.TENANT_HEADER, otherTenant)
				.header("Idempotency-Key", UUID.randomUUID()))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.error.code").value("KNOWLEDGE_FILE_TYPE_UNSUPPORTED"));
	}

	private UUID createDocument(String title, String type, String classification, UUID tenantId) throws Exception {
		MvcResult result = mockMvc.perform(withTenant(
			post("/api/core/v1/knowledge-documents")
				.header("Idempotency-Key", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody(title, type, classification)),
			tenantId))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(json(result).at("/data/id").asText());
	}

	private String createBody(String title, String type, String classification) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
			"title", title,
			"type", type,
			"ownerOrganizationRef", Map.of(
				"type", "Organization",
				"id", UUID.randomUUID(),
				"version", 1),
			"classification", classification,
			"tags", List.of("controlled")));
	}

	private String decisionBody(String inputHash) throws Exception {
		return objectMapper.writeValueAsString(Map.of(
			"decision", "APPROVE",
			"inputHash", inputHash,
			"comment", "Knowledge administrator approved the exact content hash."));
	}

	private JsonNode json(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

	private static MockHttpServletRequestBuilder withTenant(
			MockHttpServletRequestBuilder request,
			UUID tenantId) {
		if (tenantId != null) request.header(TenantActorResolver.TENANT_HEADER, tenantId);
		return request;
	}
}
