import assert from 'node:assert/strict';
import {
  ResumeFileError,
  STANDALONE_RESUME_CONNECTOR_ID,
  getResumeFile,
  isResumeServiceUnavailable,
  listResumeFiles,
  mapResumeFileResponse,
  uploadResumeFile,
} from '../src/services/resumeFiles.js';
import { DEMO_CANDIDATE_CONNECTOR_ID } from '../src/services/recruitmentAgent.js';

const resumePayload = {
  id: '00000000-0000-0000-0000-000000000901',
  fileVersion: 2,
  originalFileName: 'zhang-wei.docx',
  mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  sizeBytes: 4096,
  sha256: 'a'.repeat(64),
  parseStatus: 'PARSED',
  failureCode: null,
  parserVersion: 'apache-tika-3.2.1/rule-extractor-v1',
  retryable: false,
  extractedText: '姓名：张伟\n专业技能\nJava，Spring Boot',
  evidence: [{ field: 'name', section: 'HEADER', quote: '张伟', startOffset: 3, endOffset: 5 }],
  parsedProfile: {
    name: '张伟',
    emails: [],
    phones: [],
    educationLevel: '硕士',
    experienceYears: 6,
    skills: ['Java', 'Spring Boot'],
    location: '上海',
    evidence: [],
  },
  candidate: {
    id: '00000000-0000-0000-0000-000000000902',
    candidateNo: 'C-00000001',
    displayName: '张伟',
    consentStatus: 'UNKNOWN',
    sourceRef: null,
  },
  resumeVersionRef: { type: 'ResumeVersion', id: '00000000-0000-0000-0000-000000000903', version: 2 },
  candidateReceipt: {
    connectorId: STANDALONE_RESUME_CONNECTOR_ID,
    resumeVersionRef: { type: 'ResumeVersion', id: '00000000-0000-0000-0000-000000000903', version: 2 },
  },
  sourceSystem: 'smartai.resume-library',
  externalCandidateId: `resume-${'a'.repeat(64)}`,
  createdAt: '2026-08-03T00:00:00Z',
  updatedAt: '2026-08-03T00:01:00Z',
};

const mapped = mapResumeFileResponse({ data: resumePayload });
assert.equal(mapped.fileName, resumePayload.originalFileName);
assert.equal(mapped.contentHash, resumePayload.sha256);
assert.equal(mapped.fileVersion, 2);
assert.equal(mapped.parsedProfile.name, '张伟');
assert.equal(mapped.connectorId, STANDALONE_RESUME_CONNECTOR_ID);
assert.deepEqual(mapped.resumeVersionRef, resumePayload.resumeVersionRef);
assert.notEqual(DEMO_CANDIDATE_CONNECTOR_ID, STANDALONE_RESUME_CONNECTOR_ID);

const secondResumePayload = {
  ...resumePayload,
  id: '00000000-0000-0000-0000-000000000911',
  originalFileName: 'li-na.pdf',
  candidate: {
    ...resumePayload.candidate,
    id: '00000000-0000-0000-0000-000000000912',
    displayName: '李娜',
  },
};
const listRequests = [];
const listed = await listResumeFiles({
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    listRequests.push({ url, init });
    const firstPage = !url.includes('cursor=');
    return new Response(JSON.stringify(firstPage
      ? { data: [resumePayload], meta: { limit: 200, hasMore: true, nextCursor: 'MjAw' } }
      : { data: [secondResumePayload], meta: { limit: 200, hasMore: false, nextCursor: null } }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  },
});
assert.equal(listRequests[0].url, 'http://core.test/api/core/v1/resume-files?limit=200');
assert.equal(listRequests[1].url, 'http://core.test/api/core/v1/resume-files?limit=200&cursor=MjAw');
assert.equal(listRequests[0].init.method, 'GET');
assert.equal(listRequests[1].init.method, 'GET');
assert.equal(listed.length, 2);
assert.equal(listed[0].candidate.displayName, '张伟');
assert.equal(listed[1].candidate.displayName, '李娜');

await assert.rejects(
  () => listResumeFiles({
    baseUrl: 'http://core.test',
    fetchImpl: async () => new Response(JSON.stringify({
      data: [resumePayload],
      meta: { limit: 200, hasMore: true, nextCursor: null },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
  }),
  (error) => error instanceof ResumeFileError && error.code === 'INVALID_RESUME_PAGINATION',
);

let detailRequest;
const detailed = await getResumeFile(resumePayload.id, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    detailRequest = { url, init };
    return new Response(JSON.stringify({ data: resumePayload }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    });
  },
});
assert.ok(detailRequest.url.endsWith(`/resume-files/${resumePayload.id}`));
assert.equal(detailRequest.init.method, 'GET');
assert.equal(detailed.extractedText, resumePayload.extractedText);

const file = new Blob(['姓名：张伟\n专业技能\nJava'], { type: 'text/plain' });
Object.defineProperty(file, 'name', { value: 'zhang-wei.txt' });
let uploadRequest;
const uploaded = await uploadResumeFile(file, {
  baseUrl: 'http://core.test',
  idempotencyKey: '00000000-0000-0000-0000-000000000904',
  fetchImpl: async (url, init) => {
    uploadRequest = { url, init };
    return new Response(JSON.stringify({ data: { ...resumePayload, originalFileName: 'zhang-wei.txt', mimeType: 'text/plain' } }), {
      status: 201,
      headers: { 'Content-Type': 'application/json' },
    });
  },
});
assert.equal(uploadRequest.url, 'http://core.test/api/core/v1/resume-files');
assert.equal(uploadRequest.init.method, 'POST');
assert.equal(uploadRequest.init.headers['Idempotency-Key'], '00000000-0000-0000-0000-000000000904');
assert.ok(uploadRequest.init.body instanceof FormData);
assert.equal(uploadRequest.init.body.get('sourceSystem'), 'smartai.resume-library');
assert.equal(uploadRequest.init.body.get('file').name, 'zhang-wei.txt');
assert.equal(uploaded.parseStatusCode, 'PARSED');

assert.throws(
  () => mapResumeFileResponse({ data: { id: resumePayload.id, parseStatus: 'PARSED' } }),
  /数据格式不完整/,
);

await assert.rejects(
  () => uploadResumeFile(file, {
    baseUrl: 'http://core.test',
    fetchImpl: async () => new Response(JSON.stringify({ error: {
      code: 'RESUME_FILE_TYPE_UNSUPPORTED',
      message: '不支持该文件类型',
      retryable: false,
    } }), { status: 415, headers: { 'Content-Type': 'application/json' } }),
  }),
  (error) => error instanceof ResumeFileError
    && error.code === 'RESUME_FILE_TYPE_UNSUPPORTED'
    && error.retryable === false,
);

assert.equal(isResumeServiceUnavailable(new ResumeFileError('offline', { code: 'RESUME_UNAVAILABLE' })), true);
assert.equal(isResumeServiceUnavailable(new ResumeFileError('invalid', { code: 'VALIDATION_FAILED', status: 400, retryable: false })), false);

console.log('Resume service frontend tests passed.');
