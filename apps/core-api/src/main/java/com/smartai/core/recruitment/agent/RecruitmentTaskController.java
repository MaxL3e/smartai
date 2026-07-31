package com.smartai.core.recruitment.agent;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartai.core.platform.api.ApiEnvelope;
import com.smartai.core.platform.api.ApiMeta;
import com.smartai.core.platform.api.ApiRequestContext;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/core/v1/recruitment-tasks")
final class RecruitmentTaskController {

	private final RequirementDraftService service;
	private final TenantActorResolver tenantActorResolver;

	RecruitmentTaskController(RequirementDraftService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@GetMapping("/{taskId}")
	ResponseEntity<ApiEnvelope<Task, ApiMeta>> get(
			@PathVariable UUID taskId,
			HttpServletRequest request) {
		Task task = service.getTask(tenantActorResolver.resolve(request), taskId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.eTag(Long.toString(task.version()))
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), task));
	}
}
