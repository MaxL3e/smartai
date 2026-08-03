package com.smartai.core.recruitment.agent;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiException;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.platform.api.PageMeta;
import com.smartai.core.recruitment.agent.RequirementDraftModels.TenantActor;
import com.smartai.core.recruitment.agent.ResumeFileModels.ResumeFile;

import jakarta.servlet.http.HttpServletRequest;

@RestController
final class ResumeFileController {

	private final ResumeFileService service;
	private final TenantActorResolver tenantActorResolver;

	ResumeFileController(ResumeFileService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@PostMapping(value = "/api/core/v1/resume-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ApiEnvelope<ResumeFile, ApiMeta>> upload(
			@RequestPart("file") MultipartFile file,
			@RequestParam(required = false) String sourceSystem,
			@RequestParam(required = false) String externalCandidateId,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			HttpServletRequest request) {
		TenantActor actor = tenantActorResolver.resolve(request);
		ApiRequestContext context = ApiRequestContext.from(request);
		var result = service.upload(
			actor,
			idempotencyKey,
			file.getOriginalFilename(),
			file.getContentType(),
			bytes(file),
			sourceSystem,
			externalCandidateId,
			new ResumeFileService.ResumeAuditContext(context.requestId(), context.traceId()));
		ResumeFile value = result.value();
		return ResponseEntity.status(HttpStatus.CREATED)
			.location(URI.create("/api/core/v1/resume-files/" + value.id()))
			.eTag(Integer.toString(value.fileVersion()))
			.header("Idempotency-Replayed", Boolean.toString(result.replayed()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), value));
	}

	@GetMapping("/api/core/v1/resume-files")
	ResponseEntity<ApiEnvelope<List<ResumeFile>, PageMeta>> list(
			@RequestParam(required = false) String parseStatus,
			@RequestParam(required = false) String cursor,
			@RequestParam(defaultValue = "50") int limit,
			HttpServletRequest request) {
		var page = service.list(tenantActorResolver.resolve(request), parseStatus, cursor, limit);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok(ApiEnvelope.page(
			context.requestId(), context.traceId(), page.items(), limit, page.hasMore(), page.nextCursor()));
	}

	@GetMapping("/api/core/v1/resume-files/{resumeFileId}")
	ResponseEntity<ApiEnvelope<ResumeFile, ApiMeta>> get(
			@PathVariable UUID resumeFileId,
			HttpServletRequest request) {
		ResumeFile value = service.get(tenantActorResolver.resolve(request), resumeFileId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Integer.toString(value.fileVersion()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), value));
	}

	private static byte[] bytes(MultipartFile file) {
		try {
			return file.getBytes();
		}
		catch (IOException exception) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RESUME_FILE_READ_FAILED", "Resume file could not be read");
		}
	}
}
