import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');
const contractFiles = {
  openapi: resolve(root, 'packages/contracts/openapi/smartai-core-v1.json'),
  asyncapi: resolve(root, 'packages/contracts/asyncapi/smartai-events-v1.json'),
};

function invariant(condition, message) {
  if (!condition) throw new Error(message);
}

async function readJson(path) {
  const source = await readFile(path, 'utf8');
  try {
    return JSON.parse(source);
  } catch (error) {
    throw new Error(`${path} is not valid JSON: ${error.message}`);
  }
}

function decodePointerPart(value) {
  return value.replaceAll('~1', '/').replaceAll('~0', '~');
}

function resolveLocalRef(document, ref) {
  invariant(ref.startsWith('#/'), `Only local contract refs are allowed: ${ref}`);
  return ref.slice(2).split('/').map(decodePointerPart).reduce((value, key) => value?.[key], document);
}

function collectLocalRefs(value, refs = []) {
  if (!value || typeof value !== 'object') return refs;
  if (typeof value.$ref === 'string') refs.push(value.$ref);
  Object.values(value).forEach((item) => collectLocalRefs(item, refs));
  return refs;
}

function validateRefs(name, document) {
  const refs = collectLocalRefs(document);
  refs.forEach((ref) => invariant(resolveLocalRef(document, ref), `${name} has an unresolved ref: ${ref}`));
  return refs.length;
}

function validateOpenApi(document) {
  invariant(/^3\.1\./.test(document.openapi), 'OpenAPI contract must use OpenAPI 3.1.x');
  invariant(document.info?.version, 'OpenAPI info.version is required');
  invariant(document.paths && Object.keys(document.paths).length > 0, 'OpenAPI paths are required');
  invariant(document.components?.schemas, 'OpenAPI components.schemas are required');

  const httpMethods = new Set(['get', 'post', 'put', 'patch', 'delete']);
  const idempotencyExceptions = new Set(['exchangeEmbedToken']);
  const operationIds = [];
  const operations = new Map();
  let operationCount = 0;
  let writeCount = 0;
  for (const [path, pathItem] of Object.entries(document.paths)) {
    for (const [method, operation] of Object.entries(pathItem)) {
      if (!httpMethods.has(method)) continue;
      operationCount += 1;
      if (method !== 'get') writeCount += 1;
      invariant(operation.operationId, `${method.toUpperCase()} ${path} is missing operationId`);
      invariant(operation.responses && Object.keys(operation.responses).length > 0, `${operation.operationId} is missing responses`);
      const parameterRefs = [...(pathItem.parameters || []), ...(operation.parameters || [])].map((parameter) => parameter.$ref || '');
      if (method !== 'get' && !idempotencyExceptions.has(operation.operationId)) invariant(parameterRefs.some((ref) => ref.endsWith('/IdempotencyKey')), `${operation.operationId} is missing Idempotency-Key`);
      if (method === 'patch') invariant(parameterRefs.some((ref) => ref.endsWith('/IfMatch')), `${operation.operationId} is missing If-Match`);
      operationIds.push(operation.operationId);
      operations.set(operation.operationId, operation);
    }
  }
  invariant(new Set(operationIds).size === operationIds.length, 'OpenAPI operationId values must be unique');

  const statusToResponse = {
    400: 'BadRequest',
    401: 'Unauthorized',
    403: 'Forbidden',
    404: 'NotFound',
    409: 'VersionConflict',
    410: 'Gone',
    428: 'PreconditionRequired',
    429: 'RateLimited',
    500: 'InternalServerError',
    503: 'ServiceUnavailable',
    504: 'GatewayTimeout',
  };
  const firstImplementationErrors = {
    createEmbedSession: [400, 401, 403, 404, 409, 429, 500, 503, 504],
    exchangeEmbedToken: [400, 401, 403, 404, 409, 410, 429, 500, 503, 504],
    resolveEmbedContext: [400, 401, 403, 404, 409, 410, 429, 500, 503, 504],
    replaceEmbedContext: [400, 401, 403, 404, 409, 410, 428, 429, 500, 503, 504],
    createRequirementDraft: [400, 401, 403, 404, 409, 429, 500, 503, 504],
    convertRequirementDraft: [400, 401, 403, 404, 409, 410, 428, 429, 500, 503, 504],
    listRecruitmentTasks: [400, 401, 403, 429, 500, 503, 504],
    getRecruitmentTask: [400, 401, 403, 404, 429, 500, 503, 504],
  };
  for (const [operationId, statuses] of Object.entries(firstImplementationErrors)) {
    const operation = operations.get(operationId);
    invariant(operation, `First implementation operation is missing: ${operationId}`);
    for (const status of statuses) {
      const expectedRef = `#/components/responses/${statusToResponse[status]}`;
      invariant(operation.responses?.[status]?.$ref === expectedRef, `${operationId} must declare ${status} as ${expectedRef}`);
    }
  }

  const requireSuccessHeader = (operationId, status, header) => {
    const response = operations.get(operationId)?.responses?.[status];
    invariant(response?.headers?.[header], `${operationId} ${status} is missing ${header}`);
  };
  requireSuccessHeader('exchangeEmbedToken', '200', 'ETag');
  requireSuccessHeader('resolveEmbedContext', '200', 'ETag');
  requireSuccessHeader('createRequirementDraft', '201', 'Idempotency-Replayed');

  const hostOrigin = document.components.schemas.EmbedSessionCreateRequest?.properties?.hostOrigin;
  invariant(hostOrigin?.$ref === '#/components/schemas/HttpsOrigin', 'EmbedSessionCreateRequest.hostOrigin must reuse HttpsOrigin');
  const httpsOriginPattern = new RegExp(document.components.schemas.HttpsOrigin?.pattern);
  invariant(httpsOriginPattern.test('https://ats.example.com:8443'), 'HttpsOrigin must accept an HTTPS host with an optional port');
  for (const invalidOrigin of ['https://ats.example.com/path', 'https://ats.example.com?tenant=1', 'https://ats.example.com#panel', 'https://user@ats.example.com']) {
    invariant(!httpsOriginPattern.test(invalidOrigin), `HttpsOrigin must reject non-origin value: ${invalidOrigin}`);
  }

  for (const responseName of new Set(Object.values(statusToResponse))) {
    const response = document.components.responses?.[responseName];
    const schemaRef = response?.content?.['application/json']?.schema?.$ref;
    invariant(schemaRef === '#/components/schemas/ErrorEnvelope', `${responseName} must use ErrorEnvelope`);
  }
  invariant(operationCount >= 20, `OpenAPI MVP is unexpectedly small (${operationCount} operations)`);
  invariant(writeCount >= 10, `OpenAPI MVP needs explicit command coverage (${writeCount} write operations)`);
  return { operationCount, writeCount, schemaCount: Object.keys(document.components.schemas).length, operationIds };
}

async function validateInventory(operationIds) {
  const path = resolve(root, 'docs/api/interface-inventory.md');
  const source = await readFile(path, 'utf8');
  const p0Gaps = source.split(/\r?\n/).filter((line) => line.startsWith('|') && line.includes('| P0 |') && line.includes('[OAS gap]'));
  invariant(p0Gaps.length === 0, `Interface inventory still has ${p0Gaps.length} P0 OpenAPI gaps`);

  const documentedIds = [...source.matchAll(/`\[OAS\]\s+([^`]+)`/g)]
    .flatMap((match) => match[1].split('/'))
    .map((value) => value.trim())
    .filter((value) => /^[a-z][A-Za-z0-9]+$/.test(value));
  const knownIds = new Set(operationIds);
  const missingIds = [...new Set(documentedIds.filter((operationId) => !knownIds.has(operationId)))];
  invariant(missingIds.length === 0, `Interface inventory references missing operationIds: ${missingIds.join(', ')}`);
  return new Set(documentedIds).size;
}

function validateAsyncApi(document) {
  invariant(/^3\.0\./.test(document.asyncapi), 'AsyncAPI contract must use AsyncAPI 3.0.x');
  invariant(document.info?.version, 'AsyncAPI info.version is required');
  invariant(document.channels && Object.keys(document.channels).length >= 5, 'AsyncAPI must define the core channels');
  invariant(document.operations && Object.keys(document.operations).length >= 10, 'AsyncAPI must define send and receive operations');
  invariant(document.components?.messages, 'AsyncAPI components.messages are required');
  invariant(document['x-smartai-topology'], 'AsyncAPI retry/DLQ topology is required');
  invariant(document['x-smartai-idempotency'], 'AsyncAPI idempotency policy is required');
  return {
    channelCount: Object.keys(document.channels).length,
    operationCount: Object.keys(document.operations).length,
    messageCount: Object.keys(document.components.messages).length,
  };
}

const openapi = await readJson(contractFiles.openapi);
const asyncapi = await readJson(contractFiles.asyncapi);
const openapiStats = validateOpenApi(openapi);
const asyncapiStats = validateAsyncApi(asyncapi);
const openapiRefCount = validateRefs('OpenAPI', openapi);
const asyncapiRefCount = validateRefs('AsyncAPI', asyncapi);
const inventoryOperationCount = await validateInventory(openapiStats.operationIds);

console.log('Contract validation passed.');
console.log(`OpenAPI: ${openapiStats.operationCount} operations, ${openapiStats.writeCount} writes, ${openapiStats.schemaCount} schemas, ${openapiRefCount} refs.`);
console.log(`AsyncAPI: ${asyncapiStats.channelCount} channels, ${asyncapiStats.operationCount} operations, ${asyncapiStats.messageCount} messages, ${asyncapiRefCount} refs.`);
console.log(`Interface inventory: ${inventoryOperationCount} referenced operationIds, 0 P0 gaps.`);
