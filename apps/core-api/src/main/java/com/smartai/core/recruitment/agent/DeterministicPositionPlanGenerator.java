package com.smartai.core.recruitment.agent;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.smartai.core.recruitment.agent.PositionPlanModels.HardConstraint;
import com.smartai.core.recruitment.agent.PositionPlanModels.PositionPlanVersion;
import com.smartai.core.recruitment.agent.PositionPlanModels.RecommendationThreshold;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScoreCriterion;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScorecardVersion;
import com.smartai.core.recruitment.agent.PositionPlanModels.ScoringRule;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Draft;
import com.smartai.core.recruitment.agent.RequirementDraftModels.ResourceRef;
import com.smartai.core.recruitment.agent.RequirementDraftModels.Task;

@Component
final class DeterministicPositionPlanGenerator {

	static final String GENERATOR_KIND = "DETERMINISTIC_DEMO";
	static final String WORKFLOW_VERSION = "position-plan-demo-v1";
	static final String PROMPT_VERSION = "deterministic-demo-v1";

	private final PositionPlanHasher hasher;

	DeterministicPositionPlanGenerator(PositionPlanHasher hasher) {
		this.hasher = hasher;
	}

	PositionPlanVersion generate(
			Task task,
			Draft sourceDraft,
			UUID runId,
			int versionNo,
			List<ResourceRef> knowledgeVersionRefs,
			String instructions,
			OffsetDateTime now) {
		List<ResourceRef> knowledgeSnapshotRefs = List.copyOf(knowledgeVersionRefs);
		List<String> requirements = requirements(task, sourceDraft);
		List<HardConstraint> constraints = task.locations().isEmpty()
			? List.of()
			: List.of(new HardConstraint(
				"WORK_LOCATION",
				"工作地点",
				"location",
				"IN",
				task.locations(),
				knowledgeSnapshotRefs.isEmpty()
					? "招聘任务已确认的工作地点"
					: "招聘任务已确认的工作地点；知识版本仅作为固定快照引用，未参与内容抽取",
				knowledgeSnapshotRefs));

		ScorecardVersion scorecard = new ScorecardVersion(
			UUID.randomUUID(),
			versionNo,
			new BigDecimal("100"),
			List.of(
				criterion("DOMAIN_EXPERIENCE", "领域经验", "40", 1),
				criterion("CORE_SKILLS", "核心能力", "35", 2),
				criterion("ROLE_FIT", "岗位适配", "25", 3)),
			List.of(
				threshold("NOT_RECOMMENDED", "0", "50"),
				threshold("REVIEW", "50", "70"),
				threshold("RECOMMENDED", "70", "85"),
				threshold("STRONGLY_RECOMMENDED", "85", "100")),
			"NO_SCORE_AND_REVIEW",
			"EXCLUDE_FROM_SCORING",
			"0".repeat(64));
		scorecard = new ScorecardVersion(
			scorecard.id(), scorecard.versionNo(), scorecard.totalScore(), scorecard.criteria(),
			scorecard.thresholds(), scorecard.missingEvidencePolicy(), scorecard.sensitiveFeaturePolicy(),
			hasher.scorecardContentHash(scorecard));

		String instructionNote = instructions == null || instructions.isBlank()
			? ""
			: "\n\nHR补充要求：" + instructions.strip();
		PositionPlanVersion plan = new PositionPlanVersion(
			UUID.randomUUID(),
			task.id(),
			versionNo,
			"DRAFT",
			1L,
			"负责" + task.positionName() + "岗位的专业工作，在" + String.join("、", task.locations())
				+ "支持组织业务目标，并对交付质量负责。" + instructionNote,
			List.of(
				"承担" + task.positionName() + "相关工作的规划、执行与持续改进",
				"与业务及协作团队明确目标、风险和交付标准",
				"沉淀可复用的方法、文档和质量规范"),
			requirements,
			constraints,
			scorecard,
			"AI",
			runId,
			knowledgeSnapshotRefs,
			PROMPT_VERSION,
			"0".repeat(64),
			generationSummary(knowledgeSnapshotRefs.size()),
			null,
			null,
			null,
			now,
			now);
		return withContentHash(plan, hasher.planContentHash(plan));
	}

	private static String generationSummary(int knowledgeReferenceCount) {
		if (knowledgeReferenceCount == 0) {
			return "由本地确定性演示生成器创建，未调用 LLM 或知识检索。";
		}
		return "由本地确定性演示生成器创建并锁定 " + knowledgeReferenceCount
			+ " 个已发布知识版本快照；当前版本仅记录引用，未执行 LLM 或 RAG 内容抽取。";
	}

	private static ScoreCriterion criterion(String code, String name, String weight, int order) {
		return new ScoreCriterion(
			code,
			name,
			new BigDecimal(weight),
			"依据简历中与岗位直接相关的可核实信息评分",
			"必须提供可定位的简历事实；缺失时进入人工复核",
			new ScoringRule("PRESENCE", Map.of("evidenceRequired", true), "demo-rule-v1"),
			true,
			null,
			order);
	}

	private static RecommendationThreshold threshold(String level, String minimum, String maximum) {
		return new RecommendationThreshold(level, new BigDecimal(minimum), new BigDecimal(maximum));
	}

	private static List<String> requirements(Task task, Draft draft) {
		List<String> result = new ArrayList<>();
		Object value = draft.fields().coreRequirements().value();
		if (value instanceof List<?> values) {
			values.stream().map(Object::toString).filter(text -> !text.isBlank()).forEach(result::add);
		}
		else if (value != null && !value.toString().isBlank()) {
			result.add(value.toString());
		}
		if (result.isEmpty()) result.add("具备与" + task.positionName() + "岗位相关的专业经验");
		result.add("能够提供与核心能力相关的可核实项目证据");
		return List.copyOf(result);
	}

	static PositionPlanVersion withContentHash(PositionPlanVersion plan, String contentHash) {
		return new PositionPlanVersion(
			plan.id(), plan.taskId(), plan.versionNo(), plan.status(), plan.version(), plan.jobDescription(),
			plan.responsibilities(), plan.requirements(), plan.hardConstraints(), plan.scorecard(), plan.generatedBy(),
			plan.basedOnRunId(), plan.knowledgeVersionRefs(), plan.promptVersion(), contentHash,
			plan.changeSummary(), plan.approvalCheckpointRef(), plan.approvedBy(), plan.approvedAt(),
			plan.createdAt(), plan.updatedAt());
	}
}
