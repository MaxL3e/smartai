import assert from 'node:assert/strict';
import {
  createKnowledgeDocument,
  deactivateKnowledgeVersion,
  mapKnowledgeDocumentResponse,
  publishKnowledgeVersion,
  requestKnowledgeVersionReview,
  uploadKnowledgeDocument,
} from '../src/services/knowledgeService.js';

const documentPayload = {
  id: '00000000-0000-0000-0000-000000000501',
  title: '研发岗位标准',
  type: 'JOB_KNOWLEDGE',
  ownerOrganizationRef: { type: 'Organization', id: '00000000-0000-0000-0000-000000000201', version: 1 },
  classification: 'INTERNAL',
  status: 'DRAFT',
  version: 2,
  tags: ['研发'],
  currentVersion: {
    id: '00000000-0000-0000-0000-000000000601',
    documentId: '00000000-0000-0000-0000-000000000501',
    versionNo: 1,
    version: 3,
    fileName: '研发岗位标准.md',
    mimeType: 'text/markdown',
    sha256: 'a'.repeat(64),
    contentHash: 'b'.repeat(64),
    publicationStatus: 'DRAFT',
    parseStatus: 'PARSED',
    indexStatus: 'INDEXED',
    approvalCheckpointRef: null,
    createdAt: '2026-08-03T00:00:00Z',
  },
  createdAt: '2026-08-03T00:00:00Z',
  updatedAt: '2026-08-03T00:01:00Z',
};

const mapped = mapKnowledgeDocumentResponse({ data: documentPayload, __responseMeta: { etag: '"2"' } });
assert.deepEqual(mapped.knowledgeVersionRef, {
  type: 'KnowledgeVersion',
  id: documentPayload.currentVersion.id,
  version: 3,
});
assert.equal(mapped.parseStatusCode, 'PARSED');
assert.equal(mapped.serviceVersion.inputHash, 'b'.repeat(64));

let createRequest;
await createKnowledgeDocument({ title: '研发岗位标准', type: '岗位知识', tags: ['研发'] }, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    createRequest = { url, init };
    return new Response(JSON.stringify({ data: documentPayload }), { status: 201, headers: { ETag: '"2"', 'Content-Type': 'application/json' } });
  },
});
assert.equal(createRequest.url, 'http://core.test/api/core/v1/knowledge-documents');
assert.equal(createRequest.init.method, 'POST');
assert.ok(createRequest.init.headers['Idempotency-Key']);
assert.equal(JSON.parse(createRequest.init.body).type, 'JOB_KNOWLEDGE');

const uploadCalls = [];
const file = new Blob(['# 岗位标准\nJava 5年以上'], { type: 'text/markdown' });
Object.defineProperty(file, 'name', { value: '岗位标准.md' });
const uploadResult = await uploadKnowledgeDocument(mapped, file, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    uploadCalls.push({ url, init });
    if (url.endsWith('/upload-sessions')) {
      return new Response(JSON.stringify({ data: {
        id: '00000000-0000-0000-0000-000000000701',
        documentId: mapped.id,
        status: 'CREATED',
        version: 1,
        uploadUrl: '/api/core/v1/knowledge-upload-sessions/00000000-0000-0000-0000-000000000701/content',
        uploadHeaders: { 'Content-Type': 'text/markdown', 'X-Content-SHA256': 'signed-hash' },
        objectKey: 'tenant/document/version',
        expiresAt: '2026-08-03T00:10:00Z',
      } }), { status: 201, headers: { ETag: '"1"', 'Content-Type': 'application/json' } });
    }
    if (init.method === 'PUT') return new Response(null, { status: 200, headers: { ETag: '"2"' } });
    return new Response(JSON.stringify({ data: documentPayload.currentVersion }), { status: 202, headers: { 'Content-Type': 'application/json' } });
  },
});
assert.equal(uploadCalls[1].url, 'http://core.test/api/core/v1/knowledge-upload-sessions/00000000-0000-0000-0000-000000000701/content');
assert.equal(uploadCalls[1].init.method, 'PUT');
assert.equal(uploadCalls[2].init.headers['If-Match'], '"2"');
assert.equal(uploadResult.version.id, documentPayload.currentVersion.id);

let reviewRequest;
const checkpoint = {
  id: '00000000-0000-0000-0000-000000000801',
  version: 1,
  inputHash: 'b'.repeat(64),
};
await requestKnowledgeVersionReview(mapped, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    reviewRequest = { url, init };
    return new Response(JSON.stringify({ data: checkpoint }), { status: 201, headers: { 'Content-Type': 'application/json' } });
  },
});
assert.ok(reviewRequest.url.endsWith(`/knowledge-versions/${mapped.serviceVersion.id}/review-requests`));
assert.equal(reviewRequest.init.headers['If-Match'], '"3"');

const publishCalls = [];
await publishKnowledgeVersion(mapped, checkpoint, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    publishCalls.push({ url, init });
    return new Response(JSON.stringify({ data: url.includes('/decisions') ? checkpoint : documentPayload }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  },
});
assert.equal(publishCalls[0].init.method, 'POST');
assert.equal(JSON.parse(publishCalls[0].init.body).decision, 'APPROVE');
assert.equal(publishCalls[1].init.method, 'GET');

let deactivateRequest;
await deactivateKnowledgeVersion(mapped, {
  baseUrl: 'http://core.test',
  fetchImpl: async (url, init) => {
    deactivateRequest = { url, init };
    return new Response(JSON.stringify({ data: { ...documentPayload.currentVersion, publicationStatus: 'DISABLED' } }), { status: 200, headers: { 'Content-Type': 'application/json' } });
  },
});
assert.equal(deactivateRequest.init.headers['If-Match'], '"3"');
assert.equal(JSON.parse(deactivateRequest.init.body).reason, '知识管理员停用当前发布版本');

console.log('Knowledge service frontend tests passed.');
