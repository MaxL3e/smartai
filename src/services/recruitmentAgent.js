const DEFAULT_CORE_API_URL = '';
const DEFAULT_TIMEOUT_MS = 8000;
const REQUIREMENT_DRAFT_PATH = '/api/core/v1/requirement-drafts';
const DEFAULT_DEMO_OWNER_ID = '00000000-0000-0000-0000-000000000101';
export const DEMO_CANDIDATE_CONNECTOR_ID = '00000000-0000-0000-0000-000000000302';

const recruitmentTypeLabels = {
  SOCIAL: '社会招聘',
  CAMPUS: '校园招聘',
  INTERNAL: '内部竞聘',
  SOCIAL_RECRUITMENT: '社会招聘',
  CAMPUS_RECRUITMENT: '校园招聘',
  INTERNAL_RECRUITMENT: '内部竞聘',
};

const priorityLabels = {
  URGENT: '高',
  HIGH: '高',
  MEDIUM: '中',
  NORMAL: '中',
  LOW: '低',
};

export class RecruitmentAgentError extends Error {
  constructor(message, { code = 'RECRUITMENT_AGENT_ERROR', status = null, retryable = true, cause } = {}) {
    super(message, { cause });
    this.name = 'RecruitmentAgentError';
    this.code = code;
    this.status = status;
    this.retryable = retryable;
  }
}

function resolveCoreApiUrl(baseUrl) {
  const configured = baseUrl || import.meta.env?.VITE_CORE_API_URL || DEFAULT_CORE_API_URL;
  return configured.replace(/\/+$/, '');
}

function randomRequestId() {
  return globalThis.crypto?.randomUUID?.() || `web-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function fieldValue(fields, key) {
  return fields?.[key]?.value;
}

function readableValue(value) {
  if (value == null) return '';
  if (typeof value === 'string' || typeof value === 'number') return String(value);
  if (Array.isArray(value)) return value.map(readableValue).filter(Boolean).join('、');
  if (typeof value === 'object') {
    return String(value.displayName || value.name || value.label || value.code || value.externalId || value.id || '');
  }
  return '';
}

function normalizeHeadcount(value, fallback = 1) {
  const count = Number(value);
  return Number.isFinite(count) && count > 0 ? Math.round(count) : fallback;
}

function normalizeDate(value, fallback) {
  if (typeof value !== 'string' || !value.trim()) return fallback;
  const date = value.match(/^\d{4}-\d{2}-\d{2}/)?.[0];
  return date || value.trim();
}

function defaultTargetDate() {
  const date = new Date();
  date.setDate(date.getDate() + 30);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function fieldIsConfirmed(field, previousValue = false) {
  return field ? field.needsConfirmation === false : previousValue;
}

function knowledgeEnabled(value, fallback = true) {
  if (value == null) return fallback;
  if (typeof value === 'boolean') return value;
  if (typeof value === 'string') return !['NONE', 'DISABLED', 'OFF', 'FALSE'].includes(value.toUpperCase());
  if (Array.isArray(value)) return value.length > 0;
  if (typeof value === 'object' && 'enabled' in value) return Boolean(value.enabled);
  return true;
}

export function composeRequirementInput(input, previousRawInput = '') {
  const next = String(input || '').trim();
  const previous = String(previousRawInput || '').trim();
  if (!previous || previous === next) return next;
  if (!next || previous.endsWith(next)) return previous;
  return `${previous}\n补充说明：${next}`;
}

export function mapRequirementDraftResponse(envelope, previous = {}) {
  const source = envelope?.data || envelope;
  if (!source || typeof source !== 'object' || !source.fields || typeof source.fields !== 'object') {
    throw new RecruitmentAgentError('智能体返回的数据格式不完整', {
      code: 'INVALID_AGENT_RESPONSE',
      retryable: true,
    });
  }

  const fields = source.fields;
  const positionName = readableValue(fieldValue(fields, 'positionName'));
  const organization = readableValue(fieldValue(fields, 'organizationRef'));
  const locations = readableValue(fieldValue(fields, 'locations'));
  const recruitmentType = readableValue(fieldValue(fields, 'recruitmentType'));
  const priority = readableValue(fieldValue(fields, 'priority'));
  const coreRequirements = readableValue(fieldValue(fields, 'coreRequirements'));

  return {
    role: positionName || previous.role || '待确认岗位',
    dept: organization || previous.dept || '组织人事部',
    city: locations || previous.city || '北京',
    headcount: normalizeHeadcount(fieldValue(fields, 'headcount'), previous.headcount || 1),
    recruitmentType: recruitmentTypeLabels[recruitmentType.toUpperCase()] || recruitmentType || previous.recruitmentType || '社会招聘',
    priority: priorityLabels[priority.toUpperCase()] || priority || previous.priority || '中',
    due: normalizeDate(fieldValue(fields, 'targetDate'), previous.due || defaultTargetDate()),
    requirement: source.rawInput || coreRequirements || previous.requirement || '',
    rawRequirement: source.rawInput || previous.rawRequirement || previous.requirement || '',
    coreRequirements,
    useKnowledge: knowledgeEnabled(fieldValue(fields, 'knowledgeScope'), previous.useKnowledge ?? true),
    confirmedFields: {
      ...previous.confirmedFields,
      role: fieldIsConfirmed(fields.positionName, previous.confirmedFields?.role),
      dept: fieldIsConfirmed(fields.organizationRef, previous.confirmedFields?.dept),
      city: fieldIsConfirmed(fields.locations, previous.confirmedFields?.city),
      headcount: fieldIsConfirmed(fields.headcount, previous.confirmedFields?.headcount),
      recruitmentType: fieldIsConfirmed(fields.recruitmentType, previous.confirmedFields?.recruitmentType),
      priority: fieldIsConfirmed(fields.priority, previous.confirmedFields?.priority),
      due: fieldIsConfirmed(fields.targetDate, previous.confirmedFields?.due),
    },
    serviceDraft: {
      id: source.id,
      status: source.status,
      version: source.version,
      etag: envelope?.__responseMeta?.etag || `"${source.version}"`,
      inputHash: envelope?.__responseMeta?.inputHash || null,
    },
  };
}

export function mapRecruitmentTaskResponse(envelope, draft = {}) {
  const source = envelope?.data || envelope;
  if (!source || typeof source !== 'object' || !source.id || !source.taskNo || !source.positionName) {
    throw new RecruitmentAgentError('招聘任务返回的数据格式不完整', {
      code: 'INVALID_TASK_RESPONSE',
      retryable: true,
    });
  }
  const locations = readableValue(source.locations);
  const recruitmentType = readableValue(source.recruitmentType);
  const priority = readableValue(source.priority);
  return {
    code: source.taskNo,
    role: source.positionName,
    dept: draft.dept || readableValue(source.organizationRef) || '待确认部门',
    city: locations || draft.city || '待确认地点',
    count: `${normalizeHeadcount(source.headcount, draft.headcount || 1)}人`,
    headcount: normalizeHeadcount(source.headcount, draft.headcount || 1),
    stage: '岗位方案',
    progress: 12,
    owner: source.owner?.displayName || '当前用户',
    due: source.targetDate ? String(source.targetDate).slice(5) : '待确定',
    tone: 'green',
    recruitmentType: recruitmentTypeLabels[recruitmentType.toUpperCase()] || recruitmentType || draft.recruitmentType || '社会招聘',
    priority: priorityLabels[priority.toUpperCase()] || priority || draft.priority || '中',
    requirement: draft.requirement || source.title || '',
    useKnowledge: draft.useKnowledge ?? true,
    serviceTask: {
      id: source.id,
      version: source.version,
      lifecycleStatus: source.lifecycleStatus,
      executionStatus: source.executionStatus,
      creationCheckpointRef: source.creationCheckpointRef,
      requirementDraftRef: draft.serviceDraft?.id ? {
        type: 'RequirementDraft',
        id: draft.serviceDraft.id,
        version: Number(draft.serviceDraft.version),
      } : null,
    },
  };
}

async function readErrorBody(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function responseHeader(response, name) {
  return response.headers?.get?.(name) || response.headers?.get?.(name.toLowerCase()) || null;
}

function requireDraftIdentity(draft) {
  if (!draft?.id || !Number.isInteger(Number(draft.version)) || Number(draft.version) < 1) {
    throw new RecruitmentAgentError('招聘草案缺少服务端版本信息', {
      code: 'DRAFT_IDENTITY_MISSING',
      retryable: false,
    });
  }
}

function draftEtag(draft) {
  const value = String(draft.etag || '');
  return /^(W\/)?"[1-9][0-9]*"$/.test(value) ? value : `"${draft.version}"`;
}

async function requestJson(path, {
  accessToken,
  baseUrl,
  body,
  contentType = 'application/json',
  etag,
  fetchImpl = globalThis.fetch,
  idempotencyKey,
  method,
  timeoutMs = DEFAULT_TIMEOUT_MS,
} = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new RecruitmentAgentError('当前环境无法连接智能体服务', { code: 'FETCH_UNAVAILABLE' });
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const requestId = randomRequestId();
  const headers = {
    Accept: 'application/json',
    'X-Request-Id': requestId,
    'X-Correlation-Id': requestId,
  };
  if (body != null) headers['Content-Type'] = contentType;
  const operationKey = idempotencyKey === undefined && method !== 'GET' ? randomRequestId() : idempotencyKey;
  if (operationKey) headers['Idempotency-Key'] = operationKey;
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  if (etag) headers['If-Match'] = etag;

  try {
    const response = await fetchImpl(`${resolveCoreApiUrl(baseUrl)}${path}`, {
      method,
      cache: 'no-store',
      credentials: 'omit',
      headers,
      body: body == null ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    if (!response.ok) {
      const errorBody = await readErrorBody(response);
      const detail = errorBody?.error;
      throw new RecruitmentAgentError(detail?.message || `智能体服务暂时无法处理请求（${response.status}）`, {
        code: detail?.code || `HTTP_${response.status}`,
        status: response.status,
        retryable: detail?.retryable ?? response.status >= 500,
      });
    }
    const envelope = await response.json();
    return {
      ...envelope,
      __responseMeta: {
        etag: responseHeader(response, 'ETag'),
        inputHash: responseHeader(response, 'X-SmartAI-Input-Hash'),
        idempotencyReplayed: responseHeader(response, 'Idempotency-Replayed') === 'true',
      },
    };
  } catch (error) {
    if (error instanceof RecruitmentAgentError) throw error;
    if (error?.name === 'AbortError') {
      throw new RecruitmentAgentError('智能体服务响应超时', { code: 'AGENT_TIMEOUT', cause: error });
    }
    throw new RecruitmentAgentError('无法连接智能体服务', { code: 'AGENT_UNAVAILABLE', cause: error });
  } finally {
    clearTimeout(timer);
  }
}

export async function createRequirementDraft(input, requestOptions = {}) {
  const normalizedInput = String(input || '').trim();
  if (normalizedInput.length < 10) {
    throw new RecruitmentAgentError('请再多描述一些招聘需求', {
      code: 'INVALID_REQUIREMENT_INPUT',
      retryable: false,
    });
  }
  return requestJson(REQUIREMENT_DRAFT_PATH, {
    ...requestOptions,
    method: 'POST',
    body: {
      input: normalizedInput,
      sourceJobRef: requestOptions.sourceJobRef || null,
      hostContextHash: requestOptions.hostContextHash || null,
      locale: 'zh-CN',
    },
  });
}

export async function getRequirementDraft(draftId, requestOptions = {}) {
  const normalizedId = String(draftId || '').trim();
  if (!normalizedId) {
    throw new RecruitmentAgentError('招聘草案缺少服务端标识', {
      code: 'DRAFT_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return requestJson(`${REQUIREMENT_DRAFT_PATH}/${encodeURIComponent(normalizedId)}`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
}

export async function updateRequirementDraft(draft, input, requestOptions = {}) {
  requireDraftIdentity(draft);
  const normalizedInput = String(input || '').trim();
  if (normalizedInput.length < 10) {
    throw new RecruitmentAgentError('请再多描述一些招聘需求', {
      code: 'INVALID_REQUIREMENT_INPUT',
      retryable: false,
    });
  }
  return requestJson(`${REQUIREMENT_DRAFT_PATH}/${encodeURIComponent(draft.id)}`, {
    ...requestOptions,
    method: 'PATCH',
    contentType: 'application/merge-patch+json',
    etag: draftEtag(draft),
    body: { rawInput: normalizedInput },
  });
}

export async function convertRequirementDraft(draft, {
  ownerUserId = import.meta.env?.VITE_CURRENT_USER_ID || DEFAULT_DEMO_OWNER_ID,
  comment = 'HR 确认招聘需求并创建任务',
  ...requestOptions
} = {}) {
  requireDraftIdentity(draft?.serviceDraft);
  const serviceDraft = draft.serviceDraft;
  if (!serviceDraft.inputHash) {
    throw new RecruitmentAgentError('招聘草案缺少服务端确认摘要，请重新提交需求', {
      code: 'CONFIRMATION_HASH_MISSING',
      retryable: true,
    });
  }
  return requestJson(`${REQUIREMENT_DRAFT_PATH}/${encodeURIComponent(serviceDraft.id)}/convert`, {
    ...requestOptions,
    method: 'POST',
    etag: draftEtag(serviceDraft),
    body: {
      confirmation: { confirmed: true, inputHash: serviceDraft.inputHash, comment },
      ownerUserId,
      participantUserIds: [],
    },
  });
}

function requireServiceTask(task) {
  const taskId = task?.serviceTask?.id || task?.id;
  if (!taskId) {
    throw new RecruitmentAgentError('招聘任务缺少服务端标识', {
      code: 'TASK_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return taskId;
}

function requireServicePlan(plan) {
  const source = plan?.servicePlan || plan;
  if (!source?.id || !Number.isInteger(Number(source.version)) || Number(source.version) < 1) {
    throw new RecruitmentAgentError('岗位方案缺少服务端版本信息', {
      code: 'POSITION_PLAN_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return source;
}

export async function generatePositionPlan(task, draft, requestOptions = {}) {
  const taskId = requireServiceTask(task);
  const sourceDraft = draft?.serviceDraft || task?.serviceTask?.requirementDraftRef;
  if (!sourceDraft?.id || !Number.isInteger(Number(sourceDraft.version))) {
    throw new RecruitmentAgentError('岗位方案缺少已确认的需求草案版本', {
      code: 'SOURCE_DRAFT_MISSING',
      retryable: false,
    });
  }
  return requestJson(`/api/core/v1/recruitment-tasks/${encodeURIComponent(taskId)}/position-plan/generations`, {
    ...requestOptions,
    method: 'POST',
    body: {
      requirementDraftRef: {
        type: 'RequirementDraft',
        id: sourceDraft.id,
        version: Number(sourceDraft.version),
      },
      knowledgeVersionRefs: requestOptions.knowledgeVersionRefs || [],
      instructions: requestOptions.instructions || null,
    },
  });
}

export async function getCurrentPositionPlan(task, requestOptions = {}) {
  const taskId = requireServiceTask(task);
  return requestJson(`/api/core/v1/recruitment-tasks/${encodeURIComponent(taskId)}/position-plan`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
}

export async function updatePositionPlan(plan, changes, requestOptions = {}) {
  const source = requireServicePlan(plan);
  return requestJson(`/api/core/v1/position-plan-versions/${encodeURIComponent(source.id)}`, {
    ...requestOptions,
    method: 'PATCH',
    contentType: 'application/merge-patch+json',
    etag: source.etag || `"${source.version}"`,
    body: changes,
  });
}

export async function requestPositionPlanReview(plan, requestOptions = {}) {
  const source = requireServicePlan(plan);
  if (!source.contentHash) {
    throw new RecruitmentAgentError('岗位方案缺少确认摘要', {
      code: 'POSITION_PLAN_HASH_MISSING',
      retryable: true,
    });
  }
  return requestJson(`/api/core/v1/position-plan-versions/${encodeURIComponent(source.id)}/review-requests`, {
    ...requestOptions,
    method: 'POST',
    etag: source.etag || `"${source.version}"`,
    body: {
      requiredRole: 'RECRUITMENT_MANAGER',
      assigneeUserId: null,
      inputHash: source.contentHash,
      comment: requestOptions.comment || '招聘负责人提交岗位方案审核',
      expiresAt: null,
    },
  });
}

export async function decideHumanCheckpoint(checkpoint, requestOptions = {}) {
  const source = checkpoint?.data || checkpoint;
  if (!source?.id || !source?.inputHash || !Number.isInteger(Number(source.version))) {
    throw new RecruitmentAgentError('人工确认点缺少服务端版本信息', {
      code: 'CHECKPOINT_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return requestJson(`/api/core/v1/human-checkpoints/${encodeURIComponent(source.id)}/decisions`, {
    ...requestOptions,
    method: 'POST',
    etag: source.etag || `"${source.version}"`,
    body: {
      decision: requestOptions.decision || 'APPROVE',
      inputHash: source.inputHash,
      comment: requestOptions.comment || '招聘负责人已核对岗位方案、评分卡和推荐阈值',
    },
  });
}

function planThresholds(thresholds = []) {
  const minimum = (level, fallback) => Number(thresholds.find((item) => item.level === level)?.minimum ?? fallback);
  return {
    strong: minimum('STRONGLY_RECOMMENDED', 85),
    recommended: minimum('RECOMMENDED', 70),
    review: minimum('REVIEW', 50),
  };
}

export function mapPositionPlanResponse(envelope, task = {}) {
  const source = envelope?.data || envelope;
  if (!source?.id || !source?.taskId || !source?.scorecard?.criteria) {
    throw new RecruitmentAgentError('岗位方案返回的数据格式不完整', {
      code: 'INVALID_POSITION_PLAN_RESPONSE',
      retryable: true,
    });
  }
  return {
    ...task,
    requirement: source.jobDescription,
    planDuties: source.responsibilities || [],
    planRequirements: source.requirements || [],
    planScoreRules: source.scorecard.criteria.map((item) => ({
      code: item.code,
      label: item.name,
      weight: Number(item.weight),
      detail: item.evidenceRequirement || item.description,
    })),
    planThresholds: planThresholds(source.scorecard.thresholds),
    planConfirmed: source.status === 'APPROVED',
    stage: source.status === 'APPROVED' ? '人才搜索' : '岗位方案',
    progress: source.status === 'APPROVED' ? 30 : 12,
    servicePlan: {
      ...source,
      etag: envelope?.__responseMeta?.etag || `"${source.version}"`,
    },
  };
}

export async function submitCandidateInput(candidate, requestOptions = {}) {
  return requestJson('/api/core/v1/candidate-inputs', {
    ...requestOptions,
    method: 'POST',
    body: candidate,
  });
}

export async function createMatchRun(task, candidateScope, requestOptions = {}) {
  const taskId = requireServiceTask(task);
  const plan = requireServicePlan(task);
  if (plan.status !== 'APPROVED' || !plan.scorecard?.id) {
    throw new RecruitmentAgentError('请先批准岗位方案和评分卡', {
      code: 'POSITION_PLAN_NOT_APPROVED',
      retryable: false,
    });
  }
  return requestJson(`/api/core/v1/recruitment-tasks/${encodeURIComponent(taskId)}/match-runs`, {
    ...requestOptions,
    method: 'POST',
    body: {
      positionPlanVersionRef: {
        type: 'PositionPlanVersion',
        id: plan.id,
        version: Number(plan.version),
      },
      scorecardVersionRef: {
        type: 'ScorecardVersion',
        id: plan.scorecard.id,
        version: Number(plan.scorecard.versionNo),
      },
      candidateScope,
      minimumRecommendationScore: requestOptions.minimumRecommendationScore ?? null,
    },
  });
}

export async function getMatchRun(matchRunId, requestOptions = {}) {
  return requestJson(`/api/core/v1/match-runs/${encodeURIComponent(matchRunId)}`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
}

export async function listMatchResults(matchRunId, requestOptions = {}) {
  return requestJson(`/api/core/v1/match-runs/${encodeURIComponent(matchRunId)}/results?limit=200`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
}

export async function resolveRequirementDraft(input, previous = {}, {
  client,
  fallback,
  requestOptions,
} = {}) {
  const requestInput = composeRequirementInput(input, previous.rawRequirement || previous.requirement);
  try {
    const envelope = client
      ? await client(requestInput, requestOptions)
      : previous.serviceDraft?.id
        ? await updateRequirementDraft(previous.serviceDraft, requestInput, requestOptions)
        : await createRequirementDraft(requestInput, requestOptions);
    return {
      draft: mapRequirementDraftResponse(envelope, previous),
      mode: 'service',
      error: null,
    };
  } catch (error) {
    const normalizedError = error instanceof RecruitmentAgentError
      ? error
      : new RecruitmentAgentError('智能体服务暂不可用', { code: 'AGENT_UNAVAILABLE', cause: error });
    if (typeof fallback !== 'function') throw normalizedError;
    const fallbackDraft = fallback(input, previous);
    return {
      draft: {
        ...fallbackDraft,
        rawRequirement: requestInput,
        serviceDraft: previous.serviceDraft,
      },
      mode: 'local',
      error: normalizedError,
    };
  }
}
