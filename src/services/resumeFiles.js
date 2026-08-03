const DEFAULT_CORE_API_URL = '';
const DEFAULT_TIMEOUT_MS = 12000;
const DEFAULT_UPLOAD_TIMEOUT_MS = 90000;
const RESUME_FILES_PATH = '/api/core/v1/resume-files';
export const STANDALONE_RESUME_CONNECTOR_ID = '00000000-0000-0000-0000-000000000301';

const parseStatusLabels = {
  UPLOADED: '等待解析',
  PARSING: '解析中',
  PARSED: '解析完成',
  PARSE_FAILED: '解析失败',
};

export class ResumeFileError extends Error {
  constructor(message, { code = 'RESUME_FILE_ERROR', status = null, retryable = true, cause } = {}) {
    super(message, { cause });
    this.name = 'ResumeFileError';
    this.code = code;
    this.status = status;
    this.retryable = retryable;
  }
}

function resolveCoreApiUrl(baseUrl) {
  const configured = baseUrl || import.meta.env?.VITE_CORE_API_URL || DEFAULT_CORE_API_URL;
  return configured.replace(/\/+$/, '');
}

function requestId() {
  return globalThis.crypto?.randomUUID?.() || `resume-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

async function readErrorBody(response) {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

async function request(path, {
  accessToken,
  baseUrl,
  body,
  fetchImpl = globalThis.fetch,
  idempotencyKey,
  method = 'GET',
  timeoutMs = DEFAULT_TIMEOUT_MS,
} = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new ResumeFileError('当前环境无法连接简历服务', { code: 'FETCH_UNAVAILABLE' });
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const operationId = requestId();
  const headers = {
    Accept: 'application/json',
    'X-Request-Id': operationId,
    'X-Correlation-Id': operationId,
  };
  if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
  const operationKey = idempotencyKey === undefined && method !== 'GET' ? requestId() : idempotencyKey;
  if (operationKey) headers['Idempotency-Key'] = operationKey;

  try {
    const response = await fetchImpl(`${resolveCoreApiUrl(baseUrl)}${path}`, {
      method,
      cache: 'no-store',
      credentials: 'omit',
      headers,
      body,
      signal: controller.signal,
    });
    if (!response.ok) {
      const errorBody = await readErrorBody(response);
      const detail = errorBody?.error;
      throw new ResumeFileError(detail?.message || `简历服务暂时无法处理请求（${response.status}）`, {
        code: detail?.code || `HTTP_${response.status}`,
        status: response.status,
        retryable: detail?.retryable ?? response.status >= 500,
      });
    }
    return await response.json();
  } catch (error) {
    if (error instanceof ResumeFileError) throw error;
    if (error?.name === 'AbortError') {
      throw new ResumeFileError('简历服务响应超时', { code: 'RESUME_TIMEOUT', cause: error });
    }
    throw new ResumeFileError('无法连接简历服务', { code: 'RESUME_UNAVAILABLE', cause: error });
  } finally {
    clearTimeout(timer);
  }
}

export function resumeParseStatusLabel(status) {
  return parseStatusLabels[status] || status || '状态未知';
}

export function mapResumeFileResponse(envelope) {
  const source = envelope?.data || envelope;
  if (!source?.id || !source?.originalFileName || !source?.parseStatus) {
    throw new ResumeFileError('简历服务返回的数据格式不完整', {
      code: 'INVALID_RESUME_RESPONSE',
      retryable: true,
    });
  }
  const receipt = source.candidateReceipt || null;
  const candidate = source.candidate || null;
  return {
    id: source.id,
    fileVersion: Number(source.fileVersion || 1),
    fileName: source.originalFileName,
    contentType: source.mimeType || 'application/octet-stream',
    sizeBytes: Number(source.sizeBytes || 0),
    contentHash: source.sha256 || null,
    parseStatusCode: source.parseStatus,
    parseStatus: resumeParseStatusLabel(source.parseStatus),
    failureCode: source.failureCode || null,
    retryable: Boolean(source.retryable),
    candidate,
    candidateInputReceipt: receipt,
    parsedProfile: source.parsedProfile || null,
    extractedText: source.extractedText || null,
    evidence: Array.isArray(source.evidence) ? source.evidence : [],
    resumeVersionRef: source.resumeVersionRef || receipt?.resumeVersionRef || null,
    connectorId: receipt?.connectorId || STANDALONE_RESUME_CONNECTOR_ID,
    sourceSystem: source.sourceSystem || 'smartai.resume-library',
    externalCandidateId: source.externalCandidateId || null,
    createdAt: source.createdAt || null,
    updatedAt: source.updatedAt || null,
    serviceBacked: true,
  };
}

export async function listResumeFiles(requestOptions = {}) {
  const records = [];
  const seenCursors = new Set();
  let cursor = null;

  do {
    const query = new URLSearchParams({ limit: '200' });
    if (cursor) query.set('cursor', cursor);
    const envelope = await request(`${RESUME_FILES_PATH}?${query}`, {
      ...requestOptions,
      method: 'GET',
      idempotencyKey: null,
    });
    const source = Array.isArray(envelope?.data)
      ? envelope.data
      : Array.isArray(envelope?.data?.items)
        ? envelope.data.items
        : [];
    records.push(...source.map((item) => mapResumeFileResponse({ data: item })));

    const meta = envelope?.meta || envelope?.data?.meta || {};
    const nextCursor = meta.nextCursor || null;
    if (!meta.hasMore) break;
    if (!nextCursor || seenCursors.has(nextCursor)) {
      throw new ResumeFileError('简历服务返回了无效的分页游标', {
        code: 'INVALID_RESUME_PAGINATION',
        retryable: true,
      });
    }
    seenCursors.add(nextCursor);
    cursor = nextCursor;
  } while (cursor);

  return records;
}

export async function getResumeFile(resumeFileId, requestOptions = {}) {
  if (!resumeFileId) {
    throw new ResumeFileError('简历记录缺少服务端标识', { code: 'RESUME_ID_MISSING', retryable: false });
  }
  return mapResumeFileResponse(await request(`${RESUME_FILES_PATH}/${encodeURIComponent(resumeFileId)}`, {
    ...requestOptions,
    method: 'GET',
    idempotencyKey: null,
  }));
}

export async function uploadResumeFile(file, {
  externalCandidateId,
  sourceSystem = 'smartai.resume-library',
  timeoutMs = DEFAULT_UPLOAD_TIMEOUT_MS,
  ...requestOptions
} = {}) {
  if (!file?.name || !file?.size) {
    throw new ResumeFileError('请选择有效的简历文件', { code: 'RESUME_FILE_MISSING', retryable: false });
  }
  const body = new FormData();
  body.append('file', file, file.name);
  if (sourceSystem) body.append('sourceSystem', sourceSystem);
  if (externalCandidateId) body.append('externalCandidateId', externalCandidateId);
  return mapResumeFileResponse(await request(RESUME_FILES_PATH, {
    ...requestOptions,
    method: 'POST',
    body,
    timeoutMs,
  }));
}

export function isResumeServiceUnavailable(error) {
  return error instanceof ResumeFileError && (
    error.status == null
    || [404, 501, 502, 503, 504].includes(error.status)
    || ['FETCH_UNAVAILABLE', 'RESUME_TIMEOUT', 'RESUME_UNAVAILABLE'].includes(error.code)
  );
}
