import assert from 'node:assert/strict';
import {
  RecruitmentAgentError,
  composeRequirementInput,
  convertRequirementDraft,
  createRequirementDraft,
  decideHumanCheckpoint,
  generatePositionPlan,
  getCurrentPositionPlan,
  getRequirementDraft,
  mapPositionPlanResponse,
  mapRecruitmentTaskResponse,
  mapRequirementDraftResponse,
  requestPositionPlanReview,
  resolveRequirementDraft,
  updatePositionPlan,
  updateRequirementDraft,
} from '../src/services/recruitmentAgent.js';

function field(value, { needsConfirmation = false, source = 'USER' } = {}) {
  return { value, confidence: 0.94, source, needsConfirmation };
}

const serviceEnvelope = {
  requestId: '99c3502e-599d-4ef8-9171-0b52fb51980c',
  data: {
    id: '45ac900e-76f7-4b16-8f38-6cae11fbe6b4',
    status: 'READY',
    version: 2,
    rawInput: '数字科技部需要在北京招聘2名数据治理专家，8月底前到岗。',
    fields: {
      positionName: field('数据治理专家'),
      organizationRef: field({ id: 'org-1', displayName: '数字科技部' }),
      locations: field(['北京']),
      headcount: field(2),
      recruitmentType: field('SOCIAL'),
      priority: field('HIGH', { needsConfirmation: true, source: 'AI' }),
      targetDate: field('2026-08-31T00:00:00Z'),
      coreRequirements: field(['大型企业项目经验', '数据标准体系建设']),
      knowledgeScope: field({ enabled: true }),
    },
  },
  meta: {},
};

const taskEnvelope = {
  requestId: 'eb6e916b-b0bd-4ab7-a9bf-53efabc5895e',
  data: {
    id: '9f61b10a-b177-42f8-945d-f3915af5b6cc',
    taskNo: 'RT-20260731-A1B2C3D4',
    title: '数据治理专家招聘任务',
    positionName: '数据治理专家',
    organizationRef: { type: 'Organization', id: 'dd640df9-d95e-46f0-9d34-97b208368d39', version: 1 },
    owner: { id: '00000000-0000-0000-0000-000000000101', displayName: '演示 HR' },
    hiringManager: null,
    participants: [],
    recruitmentType: 'SOCIAL',
    headcount: 2,
    locations: ['北京'],
    priority: 'HIGH',
    targetDate: '2026-08-31',
    businessStage: 'ROLE_PLAN',
    lifecycleStatus: 'ACTIVE',
    executionStatus: 'IDLE',
    version: 1,
    creationCheckpointRef: { type: 'HumanCheckpoint', id: '841e7813-3212-4ea1-a8ae-ec698c3c09c9', version: 1 },
    currentPlanVersionRef: null,
    sourceJobRef: null,
    createdAt: '2026-07-31T04:00:00Z',
    updatedAt: '2026-07-31T04:00:00Z',
  },
  meta: {},
};

const planEnvelope = {
  data: {
    id: '7bb39fae-4d07-440e-8df3-b8fb52d84110',
    taskId: taskEnvelope.data.id,
    versionNo: 1,
    status: 'DRAFT',
    version: 1,
    jobDescription: '负责数据治理体系建设并支持组织业务目标。',
    responsibilities: ['建设数据标准体系', '推进数据质量问题闭环'],
    requirements: ['具有大型企业数据治理经验'],
    hardConstraints: [],
    scorecard: {
      id: '900d47cf-ab23-4cdf-8637-bf290b61a55e',
      versionNo: 1,
      totalScore: 100,
      criteria: [
        { code: 'DOMAIN_EXPERIENCE', name: '领域经验', weight: 40, description: '相关经验', evidenceRequirement: '简历原文证据', scoringRule: { type: 'PRESENCE', parameters: {} }, required: true, capScore: null, displayOrder: 1 },
        { code: 'CORE_SKILLS', name: '核心能力', weight: 35, description: '专业能力', evidenceRequirement: '项目事实证据', scoringRule: { type: 'PRESENCE', parameters: {} }, required: true, capScore: null, displayOrder: 2 },
        { code: 'ROLE_FIT', name: '岗位适配', weight: 25, description: '岗位适配', evidenceRequirement: '职责匹配证据', scoringRule: { type: 'PRESENCE', parameters: {} }, required: true, capScore: null, displayOrder: 3 },
      ],
      thresholds: [
        { level: 'NOT_RECOMMENDED', minimum: 0, maximum: 50 },
        { level: 'REVIEW', minimum: 50, maximum: 70 },
        { level: 'RECOMMENDED', minimum: 70, maximum: 85 },
        { level: 'STRONGLY_RECOMMENDED', minimum: 85, maximum: 100 },
      ],
      missingEvidencePolicy: 'NO_SCORE_AND_REVIEW',
      sensitiveFeaturePolicy: 'EXCLUDE_FROM_SCORING',
      contentHash: 'c'.repeat(64),
    },
    generatedBy: 'AI',
    basedOnRunId: '81276fef-5e93-43e7-8fa9-ee3695ae8767',
    knowledgeVersionRefs: [],
    promptVersion: 'deterministic-demo-v1',
    contentHash: 'd'.repeat(64),
    changeSummary: '确定性演示生成器',
    approvalCheckpointRef: null,
    approvedBy: null,
    approvedAt: null,
    createdAt: '2026-07-31T04:10:00Z',
    updatedAt: '2026-07-31T04:10:00Z',
  },
};

function jsonResponse(envelope, headers = {}) {
  const normalized = Object.fromEntries(Object.entries(headers).map(([name, value]) => [name.toLowerCase(), value]));
  return {
    ok: true,
    status: 200,
    headers: { get: (name) => normalized[name.toLowerCase()] || null },
    json: async () => envelope,
  };
}

assert.equal(
  composeRequirementInput('补充要求有大型项目经验', '数字科技部招聘数据治理专家'),
  '数字科技部招聘数据治理专家\n补充说明：补充要求有大型项目经验',
);
assert.equal(composeRequirementInput('相同内容', '相同内容'), '相同内容');

const mapped = mapRequirementDraftResponse(serviceEnvelope);
assert.equal(mapped.role, '数据治理专家');
assert.equal(mapped.dept, '数字科技部');
assert.equal(mapped.city, '北京');
assert.equal(mapped.headcount, 2);
assert.equal(mapped.recruitmentType, '社会招聘');
assert.equal(mapped.priority, '高');
assert.equal(mapped.due, '2026-08-31');
assert.equal(mapped.useKnowledge, true);
assert.equal(mapped.confirmedFields.role, true);
assert.equal(mapped.confirmedFields.priority, false);
assert.equal(mapped.serviceDraft.version, 2);

let capturedRequest;
const created = await createRequirementDraft('数字科技部需要招聘两名数据治理专家', {
  accessToken: 'session-token',
  baseUrl: 'https://core.example.test/',
  sourceJobRef: { system: 'pilot-ats', id: 'JOB-1008', version: '7' },
  hostContextHash: 'f'.repeat(64),
  idempotencyKey: 'b7d91dda-182b-46af-a443-1e5d467146be',
  fetchImpl: async (url, options) => {
    capturedRequest = { url, options };
    return jsonResponse(serviceEnvelope, {
      ETag: '"2"',
      'X-SmartAI-Input-Hash': 'a'.repeat(64),
    });
  },
});
assert.equal(created.data.id, serviceEnvelope.data.id);
assert.equal(capturedRequest.url, 'https://core.example.test/api/core/v1/requirement-drafts');
assert.equal(capturedRequest.options.method, 'POST');
assert.equal(capturedRequest.options.headers.Authorization, 'Bearer session-token');
assert.equal(capturedRequest.options.headers['Idempotency-Key'], 'b7d91dda-182b-46af-a443-1e5d467146be');
assert.deepEqual(JSON.parse(capturedRequest.options.body), {
  input: '数字科技部需要招聘两名数据治理专家',
  sourceJobRef: { system: 'pilot-ats', id: 'JOB-1008', version: '7' },
  hostContextHash: 'f'.repeat(64),
  locale: 'zh-CN',
});

let getDraftRequest;
await getRequirementDraft(serviceEnvelope.data.id, {
  baseUrl: 'https://core.example.test',
  fetchImpl: async (url, options) => {
    getDraftRequest = { url, options };
    return jsonResponse(serviceEnvelope, { ETag: '"2"', 'X-SmartAI-Input-Hash': 'a'.repeat(64) });
  },
});
assert.equal(getDraftRequest.options.method, 'GET');
assert.equal(getDraftRequest.options.headers['Idempotency-Key'], undefined);
assert.match(getDraftRequest.url, new RegExp(`${serviceEnvelope.data.id}$`));

let patchRequest;
const patched = await updateRequirementDraft({
  id: serviceEnvelope.data.id,
  version: 2,
  etag: '"2"',
}, '数字科技部需要招聘两名数据治理专家，补充要求8月底前到岗。', {
  baseUrl: 'https://core.example.test',
  idempotencyKey: '1b09993e-ab29-4c30-a091-c7dcb4721c5c',
  fetchImpl: async (url, options) => {
    patchRequest = { url, options };
    return jsonResponse(serviceEnvelope, {
      ETag: '"3"',
      'X-SmartAI-Input-Hash': 'b'.repeat(64),
    });
  },
});
assert.equal(patchRequest.options.method, 'PATCH');
assert.equal(patchRequest.options.headers['Content-Type'], 'application/merge-patch+json');
assert.equal(patchRequest.options.headers['If-Match'], '"2"');
assert.equal(patchRequest.options.headers['Idempotency-Key'], '1b09993e-ab29-4c30-a091-c7dcb4721c5c');
assert.deepEqual(JSON.parse(patchRequest.options.body), {
  rawInput: '数字科技部需要招聘两名数据治理专家，补充要求8月底前到岗。',
});
assert.equal(mapRequirementDraftResponse(patched).serviceDraft.inputHash, 'b'.repeat(64));

let convertRequest;
const converted = await convertRequirementDraft({
  ...mapped,
  serviceDraft: {
    id: serviceEnvelope.data.id,
    version: 3,
    etag: '"3"',
    inputHash: 'b'.repeat(64),
  },
}, {
  baseUrl: 'https://core.example.test',
  ownerUserId: '00000000-0000-0000-0000-000000000101',
  idempotencyKey: '1ce58f36-eb40-4e40-88bf-eb6caf85f38d',
  fetchImpl: async (url, options) => {
    convertRequest = { url, options };
    return jsonResponse(taskEnvelope, { ETag: '"1"' });
  },
});
assert.equal(convertRequest.options.method, 'POST');
assert.equal(convertRequest.options.headers['If-Match'], '"3"');
assert.equal(convertRequest.options.headers['Idempotency-Key'], '1ce58f36-eb40-4e40-88bf-eb6caf85f38d');
assert.equal(JSON.parse(convertRequest.options.body).confirmation.inputHash, 'b'.repeat(64));
const mappedTask = mapRecruitmentTaskResponse(converted, mapped);
assert.equal(mappedTask.code, 'RT-20260731-A1B2C3D4');
assert.equal(mappedTask.stage, '岗位方案');
assert.equal(mappedTask.owner, '演示 HR');
assert.equal(mappedTask.serviceTask.lifecycleStatus, 'ACTIVE');
assert.equal(mappedTask.serviceTask.requirementDraftRef.id, serviceEnvelope.data.id);

let generateRequest;
await generatePositionPlan(mappedTask, mapped, {
  idempotencyKey: 'e8e90577-1b19-4510-aa81-912e8ec27a7b',
  fetchImpl: async (url, options) => {
    generateRequest = { url, options };
    return jsonResponse({ data: { id: '81276fef-5e93-43e7-8fa9-ee3695ae8767', status: 'WAITING_HUMAN' } });
  },
});
assert.match(generateRequest.url, new RegExp(`${taskEnvelope.data.id}/position-plan/generations$`));
assert.equal(generateRequest.options.method, 'POST');
assert.equal(JSON.parse(generateRequest.options.body).requirementDraftRef.id, serviceEnvelope.data.id);

let getPlanRequest;
const fetchedPlan = await getCurrentPositionPlan(mappedTask, {
  fetchImpl: async (url, options) => {
    getPlanRequest = { url, options };
    return jsonResponse(planEnvelope, { ETag: '"1"' });
  },
});
assert.equal(getPlanRequest.options.method, 'GET');
assert.equal(getPlanRequest.options.body, undefined);
assert.equal(getPlanRequest.options.headers['Idempotency-Key'], undefined);
const mappedPlanTask = mapPositionPlanResponse(fetchedPlan, mappedTask);
assert.equal(mappedPlanTask.requirement, planEnvelope.data.jobDescription);
assert.equal(mappedPlanTask.planDuties.length, 2);
assert.equal(mappedPlanTask.planScoreRules[0].label, '领域经验');
assert.equal(mappedPlanTask.planThresholds.strong, 85);
assert.equal(mappedPlanTask.servicePlan.etag, '"1"');

let updatePlanRequest;
const updatedPlan = await updatePositionPlan(mappedPlanTask, { jobDescription: '更新后的岗位描述' }, {
  idempotencyKey: '9dd62cd9-a14c-4668-a0ad-c7207c31a694',
  fetchImpl: async (url, options) => {
    updatePlanRequest = { url, options };
    return jsonResponse({ ...planEnvelope, data: { ...planEnvelope.data, version: 2, jobDescription: '更新后的岗位描述' } }, { ETag: '"2"' });
  },
});
assert.equal(updatePlanRequest.options.method, 'PATCH');
assert.equal(updatePlanRequest.options.headers['If-Match'], '"1"');
const updatedPlanTask = mapPositionPlanResponse(updatedPlan, mappedPlanTask);

let reviewRequest;
const checkpointEnvelope = await requestPositionPlanReview(updatedPlanTask, {
  idempotencyKey: 'b0c95fc7-7956-43c8-895f-17a30ba74244',
  fetchImpl: async (url, options) => {
    reviewRequest = { url, options };
    return jsonResponse({ data: { id: '3163cbd8-d6d4-4d46-b1cc-f1e44ed0348f', version: 1, inputHash: 'd'.repeat(64), status: 'PENDING' } }, { ETag: '"1"' });
  },
});
assert.equal(reviewRequest.options.headers['If-Match'], '"2"');
assert.equal(JSON.parse(reviewRequest.options.body).inputHash, 'd'.repeat(64));

let decisionRequest;
await decideHumanCheckpoint(checkpointEnvelope, {
  idempotencyKey: '99622c74-c35c-494d-9728-0adb6c42f4d4',
  fetchImpl: async (url, options) => {
    decisionRequest = { url, options };
    return jsonResponse({ data: { ...checkpointEnvelope.data, version: 2, status: 'APPROVED' } });
  },
});
assert.equal(decisionRequest.options.headers['If-Match'], '"1"');
assert.equal(JSON.parse(decisionRequest.options.body).decision, 'APPROVE');

let serviceInput;
const online = await resolveRequirementDraft('补充要求8月底前到岗', {
  rawRequirement: '数字科技部需要招聘两名数据治理专家',
}, {
  client: async (input) => {
    serviceInput = input;
    return serviceEnvelope;
  },
});
assert.equal(online.mode, 'service');
assert.match(serviceInput, /补充说明：补充要求8月底前到岗/);

let resolvedPatchRequest;
const serviceDraftState = {
  ...mapped,
  serviceDraft: {
    id: serviceEnvelope.data.id,
    version: 2,
    etag: '"2"',
    inputHash: 'a'.repeat(64),
  },
};
const patchedResolution = await resolveRequirementDraft('补充要求8月底前到岗', serviceDraftState, {
  requestOptions: {
    baseUrl: 'https://core.example.test',
    idempotencyKey: '151cc17c-38e4-469d-a9b3-fbd7b4805188',
    fetchImpl: async (url, options) => {
      resolvedPatchRequest = { url, options };
      return jsonResponse(serviceEnvelope, {
        ETag: '"3"',
        'X-SmartAI-Input-Hash': 'b'.repeat(64),
      });
    },
  },
});
assert.equal(patchedResolution.mode, 'service');
assert.equal(resolvedPatchRequest.options.method, 'PATCH');

let fallbackArguments;
const offlineServiceDraft = { id: serviceEnvelope.data.id, version: 2, etag: '"2"', inputHash: 'a'.repeat(64) };
const offline = await resolveRequirementDraft('继续补充信息', { role: '原岗位', serviceDraft: offlineServiceDraft }, {
  client: async () => {
    throw new RecruitmentAgentError('无法连接智能体服务', { code: 'AGENT_UNAVAILABLE' });
  },
  fallback: (input, previous) => {
    fallbackArguments = { input, previous };
    return { ...previous, requirement: input };
  },
});
assert.equal(offline.mode, 'local');
assert.equal(offline.draft.role, '原岗位');
assert.deepEqual(offline.draft.serviceDraft, offlineServiceDraft);
assert.equal(fallbackArguments.input, '继续补充信息');
assert.equal(offline.error.code, 'AGENT_UNAVAILABLE');

console.log('Recruitment agent frontend tests passed.');
