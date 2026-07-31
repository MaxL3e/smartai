import assert from 'node:assert/strict';
import {
  SmartAIEmbed,
  createSmartAIEmbed,
  createEmbedEnvelope,
  isEmbedEnvelope,
  isRecentEmbedEnvelope,
  normalizeEmbedView,
  sanitizeHostContext,
  sanitizeThemeTokens,
  validateHostContext,
} from '../packages/embed-sdk/src/index.js';

assert.equal(typeof SmartAIEmbed.init, 'function');

const envelope = createEmbedEnvelope('context.replace', { context: {} }, {
  sessionId: 'ses_test',
  sequence: 3,
});
assert.equal(isEmbedEnvelope(envelope), true);
assert.equal(isRecentEmbedEnvelope(envelope), true);
assert.equal(envelope.protocol, 'smartai.embed');
assert.equal(envelope.protocolVersion, '1.0');
assert.equal(envelope.sequence, 3);

const context = sanitizeHostContext({
  scene: 'candidate',
  enterpriseRef: { system: 'customer-ats', id: 'tenant-1' },
  jobRef: { system: 'customer-ats', id: 'job-1', version: 7 },
  candidateRef: { system: 'customer-ats', id: 'candidate-1' },
  contextVersion: 12,
  nonce: 'nonce-1',
  expiresAt: new Date(Date.now() + 60000).toISOString(),
  display: { candidateName: 'must not cross postMessage' },
});
assert.equal(context.scene, 'candidate');
assert.equal(context.jobRef.id, 'job-1');
assert.equal(context.contextVersion, 12);
assert.equal('display' in context, false);
assert.equal('permissions' in context, false);
assert.equal(validateHostContext(context).valid, true);
assert.equal(validateHostContext({ ...context, nonce: null }).code, 'CONTEXT_METADATA_REQUIRED');

const theme = sanitizeThemeTokens({
  brandColor: '#173f4f',
  accentColor: 'url(https://example.invalid/track)',
  radius: 99,
  density: 'compact',
});
assert.equal(theme['--navy'], '#173f4f');
assert.equal(theme['--cyan'], undefined);
assert.equal(theme['--embed-radius'], '8px');
assert.equal(theme['--embed-density'], 'compact');

assert.equal(normalizeEmbedView('talent'), 'talent');
assert.equal(normalizeEmbedView('talent/detail'), 'talent');
assert.equal(normalizeEmbedView('javascript:alert(1)'), 'workspace');

const messageListeners = new Set();
const transferredPorts = [];
const hostMessages = [];
let frameRemoved = false;
const fakeFrameWindow = {
  postMessage(message, targetOrigin, ports = []) {
    assert.equal(targetOrigin, 'https://embed.smartai.test');
    hostMessages.push(message);
    if (ports[0]) transferredPorts.push(ports[0]);
  },
};
const fakeFrame = {
  contentWindow: fakeFrameWindow,
  setAttribute() {},
  remove() { frameRemoved = true; },
};
const fakeContainer = {
  replaceChildren(frame) { assert.equal(frame, fakeFrame); },
};
globalThis.window = {
  location: new URL('https://ats.customer.test/jobs/1'),
  addEventListener(type, listener) { if (type === 'message') messageListeners.add(listener); },
  removeEventListener(type, listener) { if (type === 'message') messageListeners.delete(listener); },
  setTimeout,
  clearTimeout,
};
globalThis.document = {
  createElement(tag) { assert.equal(tag, 'iframe'); return fakeFrame; },
  querySelector() { return fakeContainer; },
  documentElement: { style: { setProperty() {} } },
};

const tick = () => new Promise((resolve) => setTimeout(resolve, 20));
const validContext = () => ({
  scene: 'job',
  jobRef: { system: 'customer-ats', id: 'job-1' },
  contextVersion: 1,
  nonce: 'nonce-protocol-test',
  expiresAt: new Date(Date.now() + 60000).toISOString(),
});
const dispatchWindowMessage = (data) => {
  assert.equal(messageListeners.size, 1);
  [...messageListeners][0]({
    source: fakeFrameWindow,
    origin: 'https://embed.smartai.test',
    data,
  });
};

let readyCount = 0;
const protocolEvents = [];
const protocolErrors = [];
let navigationBeforeInit = 0;
let renewalBeforeInit = 0;
const protocolClient = createSmartAIEmbed({
  container: fakeContainer,
  appUrl: 'https://embed.smartai.test/?shell=embed&surface=sidebar',
  context: validContext(),
  bootstrapToken: 'test-bootstrap-token',
  onEvent(event) { protocolEvents.push(event); },
  onReady() { readyCount += 1; },
  onNavigate() { navigationBeforeInit += 1; },
  async onSessionRenew() { renewalBeforeInit += 1; return {}; },
  onError(error) { protocolErrors.push(error); },
});
assert.equal(messageListeners.size, 1);

const firstReady = createEmbedEnvelope(
  'embed.ready',
  { supportedProtocolVersions: ['1.0'] },
  { sequence: 1 },
);
const strictModeReady = createEmbedEnvelope(
  'embed.ready',
  { supportedProtocolVersions: ['1.0'], strictModeRemount: true },
  { sequence: 2 },
);
dispatchWindowMessage(firstReady);
dispatchWindowMessage(strictModeReady);
assert.equal(hostMessages.length, 0, 'ready debounce must not send a token immediately');
await tick();
const hostInit = hostMessages.at(-1);
assert.equal(hostInit.type, 'host.init');
assert.equal(hostInit.replyTo, strictModeReady.messageId);
assert.equal(hostInit.payload.bootstrapToken, 'test-bootstrap-token');
assert.equal(hostMessages.filter((message) => message.type === 'host.init').length, 1);
assert.equal(transferredPorts.length, 1, 'StrictMode ready messages must share one MessageChannel');
const activePort = transferredPorts[0];

activePort.postMessage(createEmbedEnvelope(
  'navigation.requested',
  { intent: 'before-initialized' },
  { sessionId: protocolClient.sessionId, sequence: 999, replyTo: hostInit.messageId },
));
activePort.postMessage(createEmbedEnvelope(
  'session.renew.request',
  {},
  { sessionId: protocolClient.sessionId, sequence: 998, replyTo: hostInit.messageId },
));
activePort.postMessage(createEmbedEnvelope(
  'embed.initialized',
  {},
  { sessionId: 'wrong-session', sequence: 997, replyTo: hostInit.messageId },
));
activePort.postMessage(createEmbedEnvelope(
  'embed.initialized',
  {},
  { sessionId: protocolClient.sessionId, sequence: 996, replyTo: 'wrong-host-init' },
));
activePort.postMessage(createEmbedEnvelope(
  'embed.initialized',
  { capabilities: {} },
  { sessionId: protocolClient.sessionId, sequence: 2, replyTo: hostInit.messageId },
));
await tick();
assert.equal(readyCount, 1, 'invalid pre-initialization messages must not starve the valid session');
assert.equal(protocolEvents.some((event) => event.payload?.intent === 'before-initialized'), false);
assert.equal(navigationBeforeInit, 0);
assert.equal(renewalBeforeInit, 0);

dispatchWindowMessage(createEmbedEnvelope('embed.ready', {}, { sequence: 3 }));
await tick();
assert.equal(hostMessages.filter((message) => message.type === 'host.init').length, 1, 'production must never resend the bootstrap token');
assert.equal(transferredPorts.length, 1);
assert.equal(protocolErrors.at(-1).code, 'EMBED_READY_AFTER_INIT');
assert.equal(protocolErrors.at(-1).action, 'reinitialize');

const eventCountBeforeDestroy = protocolEvents.length;
protocolClient.destroy();
activePort.postMessage(createEmbedEnvelope(
  'navigation.requested',
  { intent: 'after-destroy' },
  { sessionId: protocolClient.sessionId, sequence: 3 },
));
await tick();
activePort.close();
assert.equal(protocolEvents.length, eventCountBeforeDestroy);
assert.equal(frameRemoved, true);
assert.equal(messageListeners.size, 0);

hostMessages.length = 0;
transferredPorts.length = 0;
frameRemoved = false;
let demoReadyCount = 0;
const demoClient = createSmartAIEmbed({
  container: fakeContainer,
  appUrl: 'https://embed.smartai.test/?shell=embed&surface=sidebar',
  context: validContext(),
  demoMode: true,
  onReady() { demoReadyCount += 1; },
});
dispatchWindowMessage(createEmbedEnvelope('embed.ready', {}, { sequence: 1 }));
await tick();
const firstDemoPort = transferredPorts[0];
dispatchWindowMessage(createEmbedEnvelope('embed.ready', {}, { sequence: 2 }));
await tick();
assert.equal(hostMessages.filter((message) => message.type === 'host.init').length, 2, 'demo mode may rebuild after a remount');
assert.equal(hostMessages.every((message) => message.payload.bootstrapToken === null), true);
const secondDemoInit = hostMessages.at(-1);
const secondDemoPort = transferredPorts[1];
secondDemoPort.postMessage(createEmbedEnvelope(
  'embed.initialized',
  { capabilities: {} },
  { sessionId: demoClient.sessionId, sequence: 1, replyTo: secondDemoInit.messageId },
));
await tick();
assert.equal(demoReadyCount, 1);
demoClient.destroy();
firstDemoPort.close();
secondDemoPort.close();
assert.equal(messageListeners.size, 0);
assert.equal(frameRemoved, true);

const baseGrant = (overrides = {}) => ({
  sessionId: 'trusted-session',
  bootstrapToken: 'trusted-bootstrap-token',
  protocolVersion: '1.0',
  expiresAt: new Date(Date.now() + 60000).toISOString(),
  embedUrl: 'https://embed.smartai.test/app',
  ...overrides,
});
const initOptions = (grant) => ({
  container: fakeContainer,
  embedOrigin: 'https://embed.smartai.test',
  context: validContext(),
  tokenProvider: async () => grant,
});

await assert.rejects(
  SmartAIEmbed.init(initOptions(baseGrant({ embedUrl: 'https://attacker.test/app' }))),
  /configured embedOrigin/,
);
await assert.rejects(
  SmartAIEmbed.init(initOptions(baseGrant({ protocolVersion: '2.0' }))),
  /protocolVersion 1\.0/,
);
await assert.rejects(
  SmartAIEmbed.init(initOptions(baseGrant({ sessionId: '' }))),
  /valid sessionId/,
);
await assert.rejects(
  SmartAIEmbed.init(initOptions(baseGrant({ expiresAt: new Date(Date.now() - 1000).toISOString() }))),
  /future expiresAt/,
);

hostMessages.length = 0;
transferredPorts.length = 0;
frameRemoved = false;
const tokenProviderInputs = [];
let renewalAttempt = 0;
const renewalErrors = [];
const smartClientPromise = SmartAIEmbed.init({
  container: fakeContainer,
  embedOrigin: 'https://embed.smartai.test/base-path',
  context: validContext(),
  async tokenProvider(input) {
    tokenProviderInputs.push({ ...input });
    if (input.reason === 'initialize') return baseGrant();
    renewalAttempt += 1;
    if (renewalAttempt === 1) return baseGrant({ sessionId: 'attacker-session' });
    return baseGrant({ bootstrapToken: 'renewed-bootstrap-token' });
  },
  onError(error) { renewalErrors.push(error); },
});
await tick();
dispatchWindowMessage(createEmbedEnvelope('embed.ready', {}, { sequence: 1 }));
await tick();
const smartHostInit = hostMessages.at(-1);
const smartIframePort = transferredPorts.at(-1);
smartIframePort.postMessage(createEmbedEnvelope(
  'session.renew.request',
  {},
  { sessionId: 'trusted-session', sequence: 999, replyTo: smartHostInit.messageId },
));
await tick();
assert.equal(tokenProviderInputs.length, 1, 'renewal must not run before embed.initialized');
smartIframePort.postMessage(createEmbedEnvelope(
  'embed.initialized',
  { capabilities: {} },
  { sessionId: 'trusted-session', sequence: 1, replyTo: smartHostInit.messageId },
));
const smartClient = await smartClientPromise;

const renewalResponses = [];
smartIframePort.onmessage = (event) => renewalResponses.push(event.data);
smartIframePort.start?.();
const maliciousRenewPayload = {
  reason: 'initialize',
  sessionId: 'attacker-session',
  requestedScopes: ['admin'],
  extra: 'must-not-reach-token-provider',
};
smartIframePort.postMessage(createEmbedEnvelope(
  'session.renew.request',
  maliciousRenewPayload,
  { sessionId: 'trusted-session', sequence: 2 },
));
await tick();
assert.deepEqual(tokenProviderInputs[1], { reason: 'renew', sessionId: 'trusted-session' });
assert.equal(renewalResponses[0].payload.error.code, 'SESSION_RENEW_FAILED');
assert.match(renewalErrors[0].message, /different sessionId/);

smartIframePort.postMessage(createEmbedEnvelope(
  'session.renew.request',
  maliciousRenewPayload,
  { sessionId: 'trusted-session', sequence: 3 },
));
await tick();
assert.deepEqual(tokenProviderInputs[2], { reason: 'renew', sessionId: 'trusted-session' });
assert.equal(renewalResponses[1].payload.bootstrapToken, 'renewed-bootstrap-token');
assert.equal(renewalResponses[1].payload.expiresAt > new Date().toISOString(), true);

smartClient.destroy();
smartIframePort.close();
assert.equal(messageListeners.size, 0);
assert.equal(frameRemoved, true);

console.log('Embed SDK protocol tests passed.');
