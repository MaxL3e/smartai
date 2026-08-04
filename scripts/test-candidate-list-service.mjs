import assert from 'node:assert/strict';
import {
  confirmCandidateList,
  createCandidateListPreview,
  decideHumanCheckpoint,
  downloadRecommendationReport,
  getCandidateListPreview,
  getCurrentCandidateList,
  getCurrentRecommendationReport,
  getHumanCheckpoint,
  getRecruitmentTask,
  listHumanCheckpoints,
  requestCandidateListReview,
} from '../src/services/recruitmentAgent.js';

const task = {
  serviceTask: {
    id: '11111111-1111-4111-8111-111111111111',
    version: 7,
    etag: '"7"',
  },
};
const matchRunRef = {
  type: 'MatchRun',
  id: '22222222-2222-4222-8222-222222222222',
  version: 2,
};
const taskCandidateRefs = [
  { type: 'TaskCandidate', id: '33333333-3333-4333-8333-333333333333', version: 3 },
  { type: 'TaskCandidate', id: '44444444-4444-4444-8444-444444444444', version: 1 },
];
const invitationPlan = {
  connectorId: '55555555-5555-4555-8555-555555555555',
  templateId: 'INTERVIEW-INVITE-V1',
  deadline: '2026-08-15T09:00:00Z',
  channel: 'ENTERPRISE_MESSAGE',
  messageTemplateId: 'MSG-CANDIDATE-CONFIRMED-V1',
  externalImpactSummary: '确认后允许后续 G5 创建面试批次，不在本步骤发送邀请。',
};
const previewRef = {
  type: 'CandidateListPreview',
  id: '66666666-6666-4666-8666-666666666666',
  version: 1,
};
const inputHash = 'a'.repeat(64);
const previewItem = {
  taskCandidateRef: taskCandidateRefs[0],
  matchResultRef: { type: 'MatchResult', id: 'dddddddd-dddd-4ddd-8ddd-dddddddddddd', version: 1 },
  candidate: {
    id: 'eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee',
    candidateNo: 'C-0001',
    displayName: '候选人甲',
    consentStatus: 'GRANTED',
  },
  selectionReason: 'RECOMMENDED / 82 分；由 HR 纳入本次推荐名单。',
  note: '优先核实大型项目职责范围。',
  evidenceRefs: [],
  needsVerification: ['项目管理范围待人工核实'],
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

let taskReadRequest;
const taskEnvelope = await getRecruitmentTask(task, {
  baseUrl: 'https://core.example.test/',
  fetchImpl: async (url, options) => {
    taskReadRequest = { url, options };
    return jsonResponse({ data: { ...task.serviceTask, taskNo: 'RT-G4-001', positionName: '数据治理专家' } }, { ETag: '"7"' });
  },
});
assert.match(taskReadRequest.url, new RegExp(`/recruitment-tasks/${task.serviceTask.id}$`));
assert.equal(taskReadRequest.options.method, 'GET');
assert.equal(taskReadRequest.options.headers['Idempotency-Key'], undefined);
assert.equal(taskEnvelope.__responseMeta.etag, '"7"');

let previewRequest;
const previewEnvelope = await createCandidateListPreview(task, {
  matchRunRef,
  taskCandidateRefs,
  selectionNotes: { [taskCandidateRefs[0].id]: ' 优先核实大型项目职责范围。 ' },
  invitationPlan,
}, {
  accessToken: 'g4-session-token',
  baseUrl: 'https://core.example.test/',
  idempotencyKey: '77777777-7777-4777-8777-777777777777',
  fetchImpl: async (url, options) => {
    previewRequest = { url, options };
    return jsonResponse({
      data: {
        id: previewRef.id,
        taskId: task.serviceTask.id,
        matchRunRef,
        items: [previewItem],
        invitationPlan,
        inputHash,
        expiresAt: '2026-08-05T09:00:00Z',
      },
    });
  },
});
assert.equal(previewRequest.url, `https://core.example.test/api/core/v1/recruitment-tasks/${task.serviceTask.id}/candidate-list-previews`);
assert.equal(previewRequest.options.method, 'POST');
assert.equal(previewRequest.options.headers.Authorization, 'Bearer g4-session-token');
assert.equal(previewRequest.options.headers['Idempotency-Key'], '77777777-7777-4777-8777-777777777777');
assert.equal(previewRequest.options.headers['If-Match'], undefined);
assert.deepEqual(JSON.parse(previewRequest.options.body), {
  matchRunRef,
  taskCandidateRefs,
  selectionNotes: { [taskCandidateRefs[0].id]: '优先核实大型项目职责范围。' },
  invitationPlan,
});
assert.equal(previewEnvelope.data.inputHash, inputHash);

let previewReadRequest;
const restoredPreview = await getCandidateListPreview(previewRef.id, {
  fetchImpl: async (url, options) => {
    previewReadRequest = { url, options };
    return jsonResponse(previewEnvelope, { ETag: '"1"', 'X-SmartAI-Input-Hash': inputHash });
  },
});
assert.match(previewReadRequest.url, new RegExp(`/candidate-list-previews/${previewRef.id}$`));
assert.equal(previewReadRequest.options.method, 'GET');
assert.equal(previewReadRequest.options.headers['Idempotency-Key'], undefined);
assert.equal(restoredPreview.__responseMeta.inputHash, inputHash);

let reviewRequest;
const checkpointEnvelope = await requestCandidateListReview(task, {
  previewRef,
  inputHash,
  requiredRole: 'HIRING_MANAGER',
  assigneeUserId: null,
  comment: '请用人经理核对候选名单和待核实项。',
  expiresAt: '2026-08-06T09:00:00Z',
}, {
  idempotencyKey: '88888888-8888-4888-8888-888888888888',
  fetchImpl: async (url, options) => {
    reviewRequest = { url, options };
    return jsonResponse({
      data: {
        id: '99999999-9999-4999-8999-999999999999',
        taskId: task.serviceTask.id,
        type: 'CONFIRM_CANDIDATE_LIST',
        resourceRef: previewRef,
        status: 'PENDING',
        requiredRole: 'HIRING_MANAGER',
        inputHash,
        version: 1,
      },
    }, { ETag: '"1"' });
  },
});
assert.match(reviewRequest.url, /candidate-list-review-requests$/);
assert.equal(reviewRequest.options.headers['If-Match'], '"7"');
assert.deepEqual(JSON.parse(reviewRequest.options.body), {
  previewRef,
  inputHash,
  requiredRole: 'HIRING_MANAGER',
  assigneeUserId: null,
  comment: '请用人经理核对候选名单和待核实项。',
  expiresAt: '2026-08-06T09:00:00Z',
});

let decisionRequest;
const approvedCheckpoint = await decideHumanCheckpoint(checkpointEnvelope, {
  decision: 'APPROVE',
  idempotencyKey: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  fetchImpl: async (url, options) => {
    decisionRequest = { url, options };
    return jsonResponse({ data: { ...checkpointEnvelope.data, status: 'APPROVED', version: 2 } }, { ETag: '"2"' });
  },
});
assert.match(decisionRequest.url, /human-checkpoints\/99999999-9999-4999-8999-999999999999\/decisions$/);
assert.equal(decisionRequest.options.headers['If-Match'], '"1"');
assert.deepEqual(JSON.parse(decisionRequest.options.body), {
  decision: 'APPROVE',
  inputHash,
  comment: '确认人已核对待确认正文并提交人工决定',
});
assert.equal(approvedCheckpoint.data.status, 'APPROVED');

let checkpointReadRequest;
await getHumanCheckpoint(approvedCheckpoint.data.id, {
  fetchImpl: async (url, options) => {
    checkpointReadRequest = { url, options };
    return jsonResponse(approvedCheckpoint, { ETag: '"2"' });
  },
});
assert.match(checkpointReadRequest.url, new RegExp(`/human-checkpoints/${approvedCheckpoint.data.id}$`));
assert.equal(checkpointReadRequest.options.method, 'GET');

let checkpointListRequest;
const checkpointPage = await listHumanCheckpoints(task, {
  type: 'CONFIRM_CANDIDATE_LIST',
  limit: 1,
}, {
  fetchImpl: async (url, options) => {
    checkpointListRequest = { url, options };
    return jsonResponse({ data: [approvedCheckpoint.data], meta: { hasMore: false, nextCursor: null } });
  },
});
assert.match(checkpointListRequest.url, /human-checkpoints\?taskId=.*type=CONFIRM_CANDIDATE_LIST/);
assert.equal(checkpointListRequest.options.method, 'GET');
assert.equal(checkpointPage.data[0].status, 'APPROVED');

let confirmRequest;
const confirmedList = await confirmCandidateList(task, {
  previewRef,
  checkpointId: approvedCheckpoint.data.id,
}, {
  etag: '"8"',
  idempotencyKey: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
  fetchImpl: async (url, options) => {
    confirmRequest = { url, options };
    return jsonResponse({
      data: {
        id: 'cccccccc-cccc-4ccc-8ccc-cccccccccccc',
        taskId: task.serviceTask.id,
        versionNo: 1,
        previewRef,
        matchRunRef,
        taskCandidateRefs,
        invitationPlan,
        checkpointRef: { type: 'HumanCheckpoint', id: approvedCheckpoint.data.id, version: 2 },
        confirmedBy: { id: 'ffffffff-ffff-4fff-8fff-ffffffffffff', displayName: '招聘负责人' },
        confirmedAt: '2026-08-04T09:00:00Z',
        contentHash: 'd'.repeat(64),
      },
    });
  },
});
assert.match(confirmRequest.url, /candidate-lists\/confirm$/);
assert.equal(confirmRequest.options.headers['If-Match'], '"8"');
assert.deepEqual(JSON.parse(confirmRequest.options.body), {
  previewRef,
  checkpointId: approvedCheckpoint.data.id,
});
assert.equal(confirmedList.data.versionNo, 1);

let currentListRequest;
await getCurrentCandidateList(task, {
  fetchImpl: async (url, options) => {
    currentListRequest = { url, options };
    return jsonResponse(confirmedList);
  },
});
assert.match(currentListRequest.url, /candidate-lists\/current$/);
assert.equal(currentListRequest.options.method, 'GET');

const report = {
  id: '12121212-1212-4212-8212-121212121212',
  taskId: task.serviceTask.id,
  versionNo: 1,
  candidateListVersionRef: { type: 'CandidateListVersion', id: confirmedList.data.id, version: 1 },
  positionPlanVersionRef: { type: 'PositionPlanVersion', id: '13131313-1313-4313-8313-131313131313', version: 2 },
  scorecardVersionRef: { type: 'ScorecardVersion', id: '14141414-1414-4414-8414-141414141414', version: 1 },
  matchRunRef,
  candidates: [],
  generatedBy: { id: 'ffffffff-ffff-4fff-8fff-ffffffffffff', displayName: '招聘负责人' },
  generatedAt: '2026-08-04T09:00:00Z',
  contentHash: 'e'.repeat(64),
};
let currentReportRequest;
await getCurrentRecommendationReport(task, {
  fetchImpl: async (url, options) => {
    currentReportRequest = { url, options };
    return jsonResponse({ data: report });
  },
});
assert.match(currentReportRequest.url, /recommendation-reports\/current$/);

let downloadRequest;
const downloaded = await downloadRecommendationReport(report.id, 'TXT', {
  fetchImpl: async (url, options) => {
    downloadRequest = { url, options };
    return {
      ok: true,
      status: 200,
      headers: { get: (name) => name.toLowerCase() === 'content-disposition' ? 'attachment; filename="report.txt"' : null },
      blob: async () => new Blob(['report']),
    };
  },
});
assert.match(downloadRequest.url, /\/download\?format=TXT$/);
assert.equal(downloadRequest.options.headers.Accept, 'text/plain');
assert.equal(downloaded.fileName, 'report.txt');

await assert.rejects(
  requestCandidateListReview(task, previewEnvelope.data),
  (error) => error.code === 'CANDIDATE_LIST_PREVIEW_REF_MISSING' && error.retryable === false,
);
await assert.rejects(
  confirmCandidateList({ serviceTask: { id: task.serviceTask.id } }, { previewRef, checkpointId: approvedCheckpoint.data.id }),
  (error) => error.code === 'TASK_VERSION_MISSING' && error.retryable === false,
);
await assert.rejects(
  createCandidateListPreview(task, { matchRunRef, taskCandidateRefs: [], invitationPlan }),
  (error) => error.code === 'TASK_CANDIDATE_REFS_MISSING' && error.retryable === false,
);

console.log('Candidate list G4 frontend service tests passed.');
