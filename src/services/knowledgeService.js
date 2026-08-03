const DEFAULT_CORE_API_URL = '';
const DEFAULT_TIMEOUT_MS = 8000;
const DEFAULT_UPLOAD_TIMEOUT_MS = 60000;
const KNOWLEDGE_DOCUMENTS_PATH = '/api/core/v1/knowledge-documents';

export const DEFAULT_KNOWLEDGE_OWNER_REF = Object.freeze({
  type: 'Organization',
  id: '00000000-0000-0000-0000-000000000201',
  version: 1,
});

const typeCodes = {
  岗位知识: 'JOB_KNOWLEDGE',
  人才画像: 'TALENT_PROFILE',
  制度流程: 'POLICY_PROCESS',
  评价标准: 'EVALUATION_STANDARD',
};

const typeLabels = Object.fromEntries(Object.entries(typeCodes).map(([label, code]) => [code, label]));

const statusLabels = {
  DRAFT: '草稿',
  IN_REVIEW: '待复核',
  PUBLISHED: '可用',
  DISABLED: '停用',
  ARCHIVED: '已归档',
};

const parseStatusLabels = {
  UPLOADED: '等待解析',
  PARSING: '解析中',
  PARSED: '解析完成',
  PARSE_FAILED: '解析失败',
};

export class KnowledgeServiceError extends Error {
  constructor(message, { code = 'KNOWLEDGE_SERVICE_ERROR', status = null, retryable = true, cause } = {}) {
    super(message, { cause });
    this.name = 'KnowledgeServiceError';
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
  return globalThis.crypto?.randomUUID?.() || `knowledge-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function responseHeader(response, name) {
  return response.headers?.get?.(name) || response.headers?.get?.(name.toLowerCase()) || null;
}

async function readErrorBody(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function normalizeEtag(resource) {
  const raw = String(resource?.serviceDocument?.etag || resource?.etag || '');
  if (/^(W\/)?"[1-9][0-9]*"$/.test(raw)) return raw;
  const version = Number(resource?.serviceDocument?.version || resource?.version);
  if (Number.isInteger(version) && version > 0) return `"${version}"`;
  throw new KnowledgeServiceError('知识资料缺少服务端版本信息，请刷新后重试', {
    code: 'KNOWLEDGE_VERSION_MISSING',
    retryable: false,
  });
}

async function requestJson(path, {
  accessToken,
  baseUrl,
  body,
  contentType = 'application/json',
  etag,
  fetchImpl = globalThis.fetch,
  idempotencyKey,
  method = 'GET',
  timeoutMs = DEFAULT_TIMEOUT_MS,
} = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new KnowledgeServiceError('当前环境无法连接知识服务', { code: 'FETCH_UNAVAILABLE' });
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
      throw new KnowledgeServiceError(detail?.message || `知识服务暂时无法处理请求（${response.status}）`, {
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
        idempotencyReplayed: responseHeader(response, 'Idempotency-Replayed') === 'true',
      },
    };
  } catch (error) {
    if (error instanceof KnowledgeServiceError) throw error;
    if (error?.name === 'AbortError') {
      throw new KnowledgeServiceError('知识服务响应超时', { code: 'KNOWLEDGE_TIMEOUT', cause: error });
    }
    throw new KnowledgeServiceError('无法连接知识服务', { code: 'KNOWLEDGE_UNAVAILABLE', cause: error });
  } finally {
    clearTimeout(timer);
  }
}

function fileFormat(fileName, mimeType) {
  const extension = String(fileName || '').split('.').pop();
  if (extension && extension !== fileName) return extension.toUpperCase();
  if (mimeType === 'application/pdf') return 'PDF';
  return 'FILE';
}

function fileMimeType(file) {
  if (file?.type) return file.type;
  const extension = String(file?.name || '').split('.').pop()?.toLowerCase();
  return {
    txt: 'text/plain',
    md: 'text/markdown',
    json: 'application/json',
    pdf: 'application/pdf',
    doc: 'application/msword',
    docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    xls: 'application/vnd.ms-excel',
    xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    ppt: 'application/vnd.ms-powerpoint',
    pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  }[extension] || 'application/octet-stream';
}

function ownerLabel(ownerOrganizationRef) {
  return ownerOrganizationRef?.displayName || ownerOrganizationRef?.name || '企业知识库';
}

export function knowledgeTypeCode(value) {
  return typeCodes[value] || value || 'JOB_KNOWLEDGE';
}

export function knowledgeParseStatusLabel(value) {
  return parseStatusLabels[value] || value || '未上传';
}

export function mapKnowledgeDocumentResponse(envelope) {
  const source = envelope?.data || envelope;
  if (!source?.id || !source?.title || !source?.type) {
    throw new KnowledgeServiceError('知识服务返回的数据格式不完整', {
      code: 'INVALID_KNOWLEDGE_RESPONSE',
      retryable: true,
    });
  }
  const currentVersion = source.currentVersion || null;
  const versionNo = Number(currentVersion?.versionNo || source.version || 1);
  const parseStatusCode = currentVersion?.parseStatus || null;
  return {
    id: source.id,
    type: typeLabels[source.type] || source.type,
    title: source.title,
    format: fileFormat(currentVersion?.fileName, currentVersion?.mimeType),
    owner: ownerLabel(source.ownerOrganizationRef),
    updated: String(source.updatedAt || source.createdAt || '').slice(0, 10),
    status: statusLabels[source.status] || source.status,
    refs: 0,
    version: `v${versionNo}.0`,
    archived: source.status === 'ARCHIVED',
    tags: source.tags || [],
    description: currentVersion
      ? `文件 ${currentVersion.fileName}，${knowledgeParseStatusLabel(parseStatusCode)}。`
      : '资料条目已创建，尚未上传知识文件。',
    parseStatus: knowledgeParseStatusLabel(parseStatusCode),
    parseStatusCode,
    indexStatusCode: currentVersion?.indexStatus || 'NOT_INDEXED',
    syncState: 'synced',
    serverBacked: true,
    knowledgeVersionRef: currentVersion ? {
      type: 'KnowledgeVersion',
      id: currentVersion.id,
      version: Number(currentVersion.version),
    } : null,
    serviceVersion: currentVersion ? {
      id: currentVersion.id,
      version: Number(currentVersion.version),
      etag: `"${currentVersion.version}"`,
      inputHash: currentVersion.contentHash || currentVersion.sha256,
      publicationStatus: currentVersion.publicationStatus,
      parseStatus: currentVersion.parseStatus,
      indexStatus: currentVersion.indexStatus,
      fileName: currentVersion.fileName,
    } : null,
    serviceDocument: {
      id: source.id,
      version: Number(source.version),
      etag: envelope?.__responseMeta?.etag || `"${source.version}"`,
      status: source.status,
      ownerOrganizationRef: source.ownerOrganizationRef,
    },
  };
}

export async function listKnowledgeDocuments({ cursor, limit = 100, status, type, ...requestOptions } = {}) {
  const query = new URLSearchParams();
  if (cursor) query.set('cursor', cursor);
  if (limit) query.set('limit', String(limit));
  if (status) query.set('status', status);
  if (type) query.set('type', knowledgeTypeCode(type));
  const envelope = await requestJson(`${KNOWLEDGE_DOCUMENTS_PATH}?${query}`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
  const documents = Array.isArray(envelope?.data)
    ? envelope.data.map((document) => mapKnowledgeDocumentResponse({ data: document }))
    : [];
  return { documents, meta: envelope?.meta || null };
}

export async function createKnowledgeDocument(input, requestOptions = {}) {
  const title = String(input?.title || '').trim();
  if (!title) {
    throw new KnowledgeServiceError('请填写资料名称', { code: 'INVALID_KNOWLEDGE_TITLE', retryable: false });
  }
  return requestJson(KNOWLEDGE_DOCUMENTS_PATH, {
    ...requestOptions,
    method: 'POST',
    body: {
      title,
      type: knowledgeTypeCode(input.type),
      ownerOrganizationRef: input.ownerOrganizationRef || DEFAULT_KNOWLEDGE_OWNER_REF,
      classification: input.classification || 'INTERNAL',
      tags: Array.isArray(input.tags) ? input.tags : [],
    },
  });
}

export async function getKnowledgeDocument(documentId, requestOptions = {}) {
  if (!documentId) {
    throw new KnowledgeServiceError('知识资料缺少服务端标识', {
      code: 'KNOWLEDGE_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return requestJson(`${KNOWLEDGE_DOCUMENTS_PATH}/${encodeURIComponent(documentId)}`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  });
}

export async function updateKnowledgeDocument(document, changes, requestOptions = {}) {
  const serviceDocument = document?.serviceDocument || document;
  if (!serviceDocument?.id) {
    throw new KnowledgeServiceError('知识资料缺少服务端标识', {
      code: 'KNOWLEDGE_IDENTITY_MISSING',
      retryable: false,
    });
  }
  const body = {};
  if (changes.title != null) body.title = String(changes.title).trim();
  if (changes.classification != null) body.classification = changes.classification;
  if (changes.tags != null) body.tags = changes.tags;
  if (changes.statusCommand) {
    body.statusCommand = changes.statusCommand;
    body.reason = changes.reason || '招聘知识库维护操作';
  }
  if (!Object.keys(body).length) return { data: document, __responseMeta: { etag: normalizeEtag(document) } };
  return requestJson(`${KNOWLEDGE_DOCUMENTS_PATH}/${encodeURIComponent(serviceDocument.id)}`, {
    ...requestOptions,
    method: 'PATCH',
    contentType: 'application/merge-patch+json',
    etag: normalizeEtag(document),
    body,
  });
}

async function sha256(file) {
  if (!globalThis.crypto?.subtle || typeof file?.arrayBuffer !== 'function') {
    throw new KnowledgeServiceError('当前浏览器无法计算文件校验值', {
      code: 'FILE_HASH_UNAVAILABLE',
      retryable: false,
    });
  }
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await file.arrayBuffer());
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

async function uploadToSignedUrl(session, file, { baseUrl, fetchImpl = globalThis.fetch, timeoutMs = DEFAULT_UPLOAD_TIMEOUT_MS } = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new KnowledgeServiceError('当前环境无法上传知识文件', { code: 'FETCH_UNAVAILABLE' });
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const apiBase = resolveCoreApiUrl(baseUrl) || globalThis.location?.origin || 'http://127.0.0.1';
    const uploadUrl = new URL(session.uploadUrl, `${apiBase}/`).toString();
    const response = await fetchImpl(uploadUrl, {
      method: 'PUT',
      headers: session.uploadHeaders || { 'Content-Type': file.type || 'application/octet-stream' },
      body: file,
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new KnowledgeServiceError(`知识文件上传失败（${response.status}）`, {
        code: `UPLOAD_HTTP_${response.status}`,
        status: response.status,
        retryable: response.status >= 500,
      });
    }
    return { etag: responseHeader(response, 'ETag') };
  } catch (error) {
    if (error instanceof KnowledgeServiceError) throw error;
    if (error?.name === 'AbortError') {
      throw new KnowledgeServiceError('知识文件上传超时', { code: 'UPLOAD_TIMEOUT', cause: error });
    }
    throw new KnowledgeServiceError('无法上传知识文件', { code: 'UPLOAD_UNAVAILABLE', cause: error });
  } finally {
    clearTimeout(timer);
  }
}

export async function uploadKnowledgeDocument(document, file, {
  baseUrl,
  changeSummary = '上传知识资料初始版本',
  fetchImpl = globalThis.fetch,
  ...requestOptions
} = {}) {
  const documentId = document?.serviceDocument?.id || document?.id;
  if (!documentId) {
    throw new KnowledgeServiceError('知识资料缺少服务端标识', {
      code: 'KNOWLEDGE_IDENTITY_MISSING',
      retryable: false,
    });
  }
  if (!file || !file.name || !file.size) {
    throw new KnowledgeServiceError('请选择需要上传的知识文件', {
      code: 'KNOWLEDGE_FILE_MISSING',
      retryable: false,
    });
  }
  if (file.size > 100 * 1024 * 1024) {
    throw new KnowledgeServiceError('知识文件不能超过 100MB', {
      code: 'KNOWLEDGE_FILE_TOO_LARGE',
      retryable: false,
    });
  }

  const checksum = await sha256(file);
  const mimeType = fileMimeType(file);
  const sessionEnvelope = await requestJson(`${KNOWLEDGE_DOCUMENTS_PATH}/${encodeURIComponent(documentId)}/upload-sessions`, {
    ...requestOptions,
    baseUrl,
    fetchImpl,
    method: 'POST',
    body: {
      fileName: file.name,
      mimeType,
      sizeBytes: file.size,
      sha256: checksum,
    },
  });
  const session = sessionEnvelope?.data;
  if (!session?.id || !session?.uploadUrl) {
    throw new KnowledgeServiceError('知识服务未返回有效的上传地址', {
      code: 'INVALID_UPLOAD_SESSION',
      retryable: true,
    });
  }
  const uploadMeta = await uploadToSignedUrl(session, file, { baseUrl, fetchImpl });
  const versionEnvelope = await requestJson(`/api/core/v1/knowledge-upload-sessions/${encodeURIComponent(session.id)}/complete`, {
    ...requestOptions,
    baseUrl,
    fetchImpl,
    method: 'POST',
    etag: uploadMeta?.etag || sessionEnvelope?.__responseMeta?.etag || `"${session.version}"`,
    body: {
      sizeBytes: file.size,
      sha256: checksum,
      changeSummary,
    },
  });
  return { session, version: versionEnvelope?.data, responseMeta: versionEnvelope?.__responseMeta };
}

function requireKnowledgeVersion(document) {
  const version = document?.serviceVersion || document;
  if (!version?.id || !Number.isInteger(Number(version.version)) || !version?.inputHash) {
    throw new KnowledgeServiceError('知识版本缺少审核所需的版本或内容摘要', {
      code: 'KNOWLEDGE_VERSION_IDENTITY_MISSING',
      retryable: false,
    });
  }
  return version;
}

export async function requestKnowledgeVersionReview(document, requestOptions = {}) {
  const version = requireKnowledgeVersion(document);
  return requestJson(`/api/core/v1/knowledge-versions/${encodeURIComponent(version.id)}/review-requests`, {
    ...requestOptions,
    method: 'POST',
    etag: version.etag || `"${version.version}"`,
    body: {
      requiredRole: 'KNOWLEDGE_ADMIN',
      assigneeUserId: null,
      inputHash: version.inputHash,
      comment: requestOptions.comment || '知识管理员提交企业知识发布审核',
      expiresAt: null,
    },
  });
}

export async function publishKnowledgeVersion(document, checkpoint, requestOptions = {}) {
  const source = checkpoint?.data || checkpoint;
  if (!source?.id || !source?.inputHash || !Number.isInteger(Number(source.version))) {
    throw new KnowledgeServiceError('知识发布确认点缺少服务端版本信息', {
      code: 'KNOWLEDGE_CHECKPOINT_MISSING',
      retryable: false,
    });
  }
  await requestJson(`/api/core/v1/human-checkpoints/${encodeURIComponent(source.id)}/decisions`, {
    ...requestOptions,
    method: 'POST',
    etag: source.etag || `"${source.version}"`,
    body: {
      decision: 'APPROVE',
      inputHash: source.inputHash,
      comment: requestOptions.comment || '知识管理员批准发布该知识版本',
    },
  });
  return getKnowledgeDocument(document?.serviceDocument?.id || document?.id, requestOptions);
}

export async function deactivateKnowledgeVersion(document, requestOptions = {}) {
  const version = requireKnowledgeVersion(document);
  return requestJson(`/api/core/v1/knowledge-versions/${encodeURIComponent(version.id)}/deactivate`, {
    ...requestOptions,
    method: 'POST',
    etag: version.etag || `"${version.version}"`,
    body: { reason: requestOptions.reason || '知识管理员停用当前发布版本' },
  });
}

export function isKnowledgeServiceUnavailable(error) {
  return error instanceof KnowledgeServiceError && (
    error.status == null
    || [404, 501, 502, 503, 504].includes(error.status)
    || ['FETCH_UNAVAILABLE', 'KNOWLEDGE_TIMEOUT', 'KNOWLEDGE_UNAVAILABLE'].includes(error.code)
  );
}
