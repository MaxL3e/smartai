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
import com.smartai.core.recruitment.agent.PositionPlanModels.AgentRun;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/core/v1/agent-runs")
final class AgentRunController {

	private final PositionPlanService service;
	private final TenantActorResolver tenantActorResolver;

	AgentRunController(PositionPlanService service, TenantActorResolver tenantActorResolver) {
		this.service = service;
		this.tenantActorResolver = tenantActorResolver;
	}

	@GetMapping("/{agentRunId}")
	ResponseEntity<ApiEnvelope<AgentRun, ApiMeta>> get(
			@PathVariable UUID agentRunId,
			HttpServletRequest request) {
		AgentRun run = service.getAgentRun(tenantActorResolver.resolve(request), agentRunId);
		ApiRequestContext context = ApiRequestContext.from(request);
		return ResponseEntity.ok()
			.body(ApiEnvelope.success(context.requestId(), context.traceId(), run));
	}
}
