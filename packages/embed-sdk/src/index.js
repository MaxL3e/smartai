export const EMBED_PROTOCOL = 'smartai.embed';
export const EMBED_PROTOCOL_VERSION = '1.0';

export const EMBED_VIEWS = new Set([
  'workspace',
  'roleplan',
  'tasks',
  'talent',
  'interviews',
  'evaluation',
  'knowledge',
  'audit',
]);

const THEME_TOKEN_MAP = {
  brandColor: '--navy',
  primaryColor: '--navy',
  brandHoverColor: '--navy-hover',
  accentColor: '--cyan',
  surfaceColor: '--surface',
  textColor: '--ink',
  borderColor: '--line',
};

function randomId(prefix) {
  if (globalThis.crypto?.randomUUID) return `${prefix}_${globalThis.crypto.randomUUID()}`;
  return `${prefix}_${Date.now()}_${Math.random().toString(36).slice(2)}`;
}

function isSafeColor(value) {
  return typeof value === 'string' && (
    /^#[0-9a-f]{3,8}$/i.test(value)
    || /^rgb(a)?\([\d\s,.%]+\)$/i.test(value)
    || /^hsl(a)?\([\d\s,.%]+\)$/i.test(value)
  );
}

export function createEmbedEnvelope(type, payload = {}, options = {}) {
  return {
    protocol: EMBED_PROTOCOL,
    protocolVersion: EMBED_PROTOCOL_VERSION,
    sessionId: options.sessionId || null,
    messageId: options.messageId || randomId('msg'),
    replyTo: options.replyTo || null,
    type,
    sequence: options.sequence || 0,
    sentAt: new Date().toISOString(),
    payload,
  };
}

export function isEmbedEnvelope(value) {
  return Boolean(
    value
    && value.protocol === EMBED_PROTOCOL
    && value.protocolVersion === EMBED_PROTOCOL_VERSION
    && (value.sessionId === null || typeof value.sessionId === 'string')
    && typeof value.type === 'string'
    && typeof value.messageId === 'string'
    && Number.isSafeInteger(value.sequence)
    && value.sequence >= 0
    && typeof value.sentAt === 'string'
    && value.payload
    && typeof value.payload === 'object',
  );
}

export function isRecentEmbedEnvelope(value, maxAgeMs = 120000) {
  if (!isEmbedEnvelope(value)) return false;
  const sentAt = Date.parse(value.sentAt);
  return Number.isFinite(sentAt) && Math.abs(Date.now() - sentAt) <= maxAgeMs;
}

export function normalizeEmbedView(value, fallback = 'workspace') {
  const rootView = typeof value === 'string' ? value.split('/')[0] : value;
  return EMBED_VIEWS.has(rootView) ? rootView : fallback;
}

export function sanitizeHostContext(value = {}) {
  const context = value && typeof value === 'object' ? value : {};
  const scene = ['job', 'candidate', 'task'].includes(context.scene) ? context.scene : 'job';
  const cleanRef = (ref) => {
    if (!ref || typeof ref !== 'object') return null;
    const id = String(ref.id || '').slice(0, 160);
    if (!id) return null;
    return { system: String(ref.system || 'ats').slice(0, 80), id, version: ref.version == null ? null : String(ref.version).slice(0, 80) };
  };
  return {
    scene,
    enterpriseRef: cleanRef(context.enterpriseRef),
    jobRef: cleanRef(context.jobRef),
    taskRef: cleanRef(context.taskRef),
    candidateRef: cleanRef(context.candidateRef),
    applicationRef: cleanRef(context.applicationRef),
    hostRoute: typeof context.hostRoute === 'string' ? context.hostRoute.slice(0, 120) : null,
    returnIntent: typeof context.returnIntent === 'string' ? context.returnIntent.slice(0, 80) : 'return_to_context',
    contextVersion: Math.max(0, Number(context.contextVersion) || 0),
    nonce: typeof context.nonce === 'string' ? context.nonce.slice(0, 160) : null,
    expiresAt: Number.isFinite(Date.parse(context.expiresAt)) ? new Date(context.expiresAt).toISOString() : null,
    locale: typeof context.locale === 'string' ? context.locale.slice(0, 20) : 'zh-CN',
    timeZone: typeof context.timeZone === 'string' ? context.timeZone.slice(0, 60) : 'Asia/Shanghai',
  };
}

export function validateHostContext(context) {
  if (!context?.jobRef) return { valid: false, code: 'JOB_CONTEXT_REQUIRED' };
  if (!context.nonce || !context.expiresAt || context.contextVersion < 1) return { valid: false, code: 'CONTEXT_METADATA_REQUIRED' };
  if (Date.parse(context.expiresAt) <= Date.now()) return { valid: false, code: 'CONTEXT_EXPIRED' };
  return { valid: true, code: null };
}

export function sanitizeThemeTokens(theme = {}) {
  const safe = {};
  for (const [key, cssVariable] of Object.entries(THEME_TOKEN_MAP)) {
    if (isSafeColor(theme[key])) safe[cssVariable] = theme[key];
  }
  if (['compact', 'comfortable'].includes(theme.density)) safe['--embed-density'] = theme.density;
  if (Number.isFinite(theme.radius)) safe['--embed-radius'] = `${Math.min(8, Math.max(0, theme.radius))}px`;
  return safe;
}

export function applyEmbedTheme(theme, root = document.documentElement) {
  const safeTokens = sanitizeThemeTokens(theme);
  Object.entries(safeTokens).forEach(([key, value]) => root.style.setProperty(key, value));
  return safeTokens;
}

export function readEmbedConfiguration(locationLike = window.location, documentLike = document) {
  const params = new URLSearchParams(locationLike.search);
  const isEmbedded = params.get('shell') === 'embed';
  const surface = params.get('surface') === 'sidebar' ? 'sidebar' : 'workspace';
  let parentOrigin = null;
  try {
    if (documentLike.referrer) parentOrigin = new URL(documentLike.referrer).origin;
  } catch {
    parentOrigin = null;
  }
  return {
    isEmbedded,
    surface,
    initialView: normalizeEmbedView(params.get('view')),
    parentOrigin,
  };
}

function parseTrustedEmbedOrigin(value) {
  let url;
  try {
    url = new URL(value);
  } catch {
    throw new Error('embedOrigin must be a valid absolute URL');
  }
  if (!['http:', 'https:'].includes(url.protocol) || url.origin === 'null') {
    throw new Error('embedOrigin must use HTTP or HTTPS');
  }
  return url.origin;
}

function validateBootstrapGrant(value, embedOrigin, expectedSessionId = null) {
  if (!value || typeof value !== 'object') throw new Error('tokenProvider must return a bootstrap grant');
  if (typeof value.sessionId !== 'string' || !value.sessionId.trim() || value.sessionId.length > 160) {
    throw new Error('tokenProvider must return a valid sessionId');
  }
  if (expectedSessionId && value.sessionId !== expectedSessionId) {
    throw new Error('tokenProvider returned a different sessionId');
  }
  if (value.protocolVersion !== EMBED_PROTOCOL_VERSION) {
    throw new Error(`tokenProvider must return protocolVersion ${EMBED_PROTOCOL_VERSION}`);
  }
  if (typeof value.bootstrapToken !== 'string' || !value.bootstrapToken.trim()) {
    throw new Error('tokenProvider must return a valid bootstrapToken');
  }
  const expiresAt = Date.parse(value.expiresAt);
  if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    throw new Error('tokenProvider must return a future expiresAt');
  }
  if (typeof value.embedUrl !== 'string' || !value.embedUrl.trim()) {
    throw new Error('tokenProvider must return embedUrl');
  }

  let embedUrl;
  try {
    embedUrl = new URL(value.embedUrl, `${embedOrigin}/`);
  } catch {
    throw new Error('tokenProvider must return a valid embedUrl');
  }
  if (embedUrl.origin !== embedOrigin) {
    throw new Error('tokenProvider grant embedUrl must use the configured embedOrigin');
  }

  return {
    sessionId: value.sessionId,
    bootstrapToken: value.bootstrapToken,
    protocolVersion: value.protocolVersion,
    expiresAt: new Date(expiresAt).toISOString(),
    embedUrl,
    context: value.context,
  };
}

export function createSmartAIEmbed(options) {
  if (!options?.container) throw new Error('container is required');
  if (!options?.appUrl) throw new Error('appUrl is required');

  const container = typeof options.container === 'string'
    ? document.querySelector(options.container)
    : options.container;
  if (!container) throw new Error('embed container was not found');

  const appUrl = new URL(options.appUrl, window.location.href);
  const localHosts = new Set(['localhost', '127.0.0.1', '[::1]']);
  if (!['http:', 'https:'].includes(appUrl.protocol)) throw new Error('appUrl must use HTTP or HTTPS');
  if (appUrl.protocol !== 'https:' && !localHosts.has(appUrl.hostname)) throw new Error('appUrl must use HTTPS outside local development');
  if (!options.demoMode && appUrl.origin === window.location.origin) throw new Error('Production embeds must use a dedicated SmartAI origin');
  const targetOrigin = appUrl.origin;
  const sessionId = options.sessionId || randomId('ses');
  let sequence = 0;
  let lifecycle = 'waiting-ready';
  let inboundSequence = 0;
  let channelPort = null;
  let initializationTimer = null;
  let readyDebounceTimer = null;
  let pendingReadyReplyTo = null;
  let pendingInitMessageId = null;
  let context = sanitizeHostContext(options.context);
  let theme = options.theme || {};
  const readyDebounceMs = 12;
  const initialContextValidation = validateHostContext(context);
  if (!initialContextValidation.valid) throw new Error(`Invalid host context: ${initialContextValidation.code}`);

  const iframe = document.createElement('iframe');
  iframe.className = options.className || 'smartai-embed-frame';
  iframe.title = options.title || '知聘招聘智能体';
  iframe.src = appUrl.href;
  iframe.referrerPolicy = 'strict-origin-when-cross-origin';
  iframe.sandbox = 'allow-scripts allow-forms allow-downloads allow-same-origin';
  iframe.allow = "camera 'none'; microphone 'none'; geolocation 'none'";
  iframe.setAttribute('data-smartai-embed', EMBED_PROTOCOL_VERSION);

  function teardownChannelPort() {
    const port = channelPort;
    channelPort = null;
    if (!port) return;
    port.onmessage = null;
    port.onmessageerror = null;
    try {
      port.close();
    } catch {
      // The browser may already have closed a transferred port.
    }
  }

  function clearReadyDebounce() {
    window.clearTimeout(readyDebounceTimer);
    readyDebounceTimer = null;
    pendingReadyReplyTo = null;
  }

  function failClosed(error) {
    if (lifecycle === 'failed' || lifecycle === 'destroyed') return;
    lifecycle = 'failed';
    window.clearTimeout(initializationTimer);
    initializationTimer = null;
    clearReadyDebounce();
    pendingInitMessageId = null;
    teardownChannelPort();
    options.onError?.({ code: 'EMBED_PROTOCOL_FAILED', recoverable: false, ...error });
  }

  function armInitializationTimer() {
    window.clearTimeout(initializationTimer);
    initializationTimer = window.setTimeout(() => {
      if (lifecycle === 'initialized' || lifecycle === 'failed' || lifecycle === 'destroyed') return;
      failClosed({ code: 'EMBED_INIT_TIMEOUT', recoverable: true });
    }, options.initializationTimeoutMs || 8000);
  }

  function send(type, payload = {}, extra = {}) {
    if (lifecycle === 'failed' || lifecycle === 'destroyed' || !iframe.contentWindow) return null;
    sequence += 1;
    const message = createEmbedEnvelope(type, payload, { sessionId, sequence, ...extra });
    if (channelPort) channelPort.postMessage(message);
    else iframe.contentWindow.postMessage(message, targetOrigin);
    return message.messageId;
  }

  function initialize(replyTo = null) {
    clearReadyDebounce();
    teardownChannelPort();
    lifecycle = 'initializing';
    inboundSequence = 0;
    sequence += 1;
    const message = createEmbedEnvelope('host.init', {
      context,
      theme,
      route: { view: normalizeEmbedView(options.view || appUrl.searchParams.get('view')), surface: appUrl.searchParams.get('surface') || 'workspace' },
      capabilities: options.capabilities || ['CONTEXT_PUSH', 'HOST_NAVIGATION', 'AUTH_REFRESH', 'THEME_TOKENS'],
      bootstrapToken: options.bootstrapToken || null,
      demoMode: Boolean(options.demoMode),
    }, { sessionId, sequence, replyTo });
    pendingInitMessageId = message.messageId;
    if (typeof MessageChannel === 'function') {
      const channel = new MessageChannel();
      const port = channel.port1;
      channelPort = port;
      port.onmessage = (event) => {
        if (channelPort === port) handleEnvelope(event.data);
      };
      port.onmessageerror = () => {
        if (channelPort !== port) return;
        failClosed({ code: 'MESSAGE_CHANNEL_ERROR', recoverable: true });
      };
      port.start?.();
      iframe.contentWindow.postMessage(message, targetOrigin, [channel.port2]);
    } else {
      iframe.contentWindow.postMessage(message, targetOrigin);
    }
    armInitializationTimer();
  }

  function scheduleInitialization(message) {
    const canDebounce = lifecycle === 'waiting-ready' || lifecycle === 'ready-debounce';
    if (!canDebounce && !options.demoMode) {
      if (lifecycle === 'initializing' || lifecycle === 'initialized') {
        failClosed({ code: 'EMBED_READY_AFTER_INIT', recoverable: false, action: 'reinitialize' });
      }
      return;
    }
    if (!canDebounce && lifecycle === 'destroyed') return;

    if (options.demoMode && !canDebounce) {
      window.clearTimeout(initializationTimer);
      initializationTimer = null;
      teardownChannelPort();
      pendingInitMessageId = null;
      inboundSequence = 0;
    }

    lifecycle = 'ready-debounce';
    pendingReadyReplyTo = message.messageId;
    window.clearTimeout(readyDebounceTimer);
    readyDebounceTimer = window.setTimeout(() => {
      if (lifecycle !== 'ready-debounce') return;
      const replyTo = pendingReadyReplyTo;
      initialize(replyTo);
    }, readyDebounceMs);
    options.onEvent?.(message);
  }

  async function handleEnvelope(message) {
    if (lifecycle === 'destroyed' || !isRecentEmbedEnvelope(message)) return;
    let messageSize;
    try {
      messageSize = JSON.stringify(message).length;
    } catch {
      return;
    }
    if (messageSize > 65536) return;
    if (message.type === 'embed.ready') {
      if (message.sessionId !== null) return;
      scheduleInitialization(message);
      return;
    }

    if (lifecycle === 'initializing') {
      const allowedDuringInitialization = ['embed.initialized', 'context.rejected', 'error'];
      if (
        message.sessionId !== sessionId
        || !allowedDuringInitialization.includes(message.type)
        || message.replyTo !== pendingInitMessageId
        || message.sequence <= inboundSequence
      ) return;
      inboundSequence = message.sequence;
      options.onEvent?.(message);
      if (message.type === 'embed.initialized') {
        lifecycle = 'initialized';
        pendingInitMessageId = null;
        window.clearTimeout(initializationTimer);
        initializationTimer = null;
        options.onReady?.({ sessionId, iframe, capabilities: message.payload.capabilities || {} });
        return;
      }
      failClosed({ code: 'EMBED_INITIALIZATION_REJECTED', recoverable: false, ...message.payload });
      return;
    }

    if (lifecycle !== 'initialized' || message.sessionId !== sessionId || message.type === 'embed.initialized' || message.sequence <= inboundSequence) return;
    inboundSequence = message.sequence;
    options.onEvent?.(message);
    if (message.type === 'navigation.requested') {
      options.onNavigate?.(message.payload);
      return;
    }
    if (message.type === 'context.rejected') {
      failClosed({ code: 'CONTEXT_REJECTED', recoverable: true, ...message.payload });
      return;
    }
    if (message.type === 'session.renew.request' && options.onSessionRenew) {
      try {
        const renewed = await options.onSessionRenew(message.payload);
        send('session.renew.response', renewed || {}, { replyTo: message.messageId });
      } catch (error) {
        send('session.renew.response', { error: { code: 'SESSION_RENEW_FAILED', recoverable: true } }, { replyTo: message.messageId });
        options.onError?.(error);
      }
    }
    if (message.type === 'error') failClosed(message.payload);
  }

  function handleMessage(event) {
    if (lifecycle === 'destroyed' || event.source !== iframe.contentWindow || event.origin !== targetOrigin || !isEmbedEnvelope(event.data)) return;
    handleEnvelope(event.data);
  }

  window.addEventListener('message', handleMessage);
  container.replaceChildren(iframe);
  armInitializationTimer();

  function updateContext(nextContext) {
    const sanitizedContext = sanitizeHostContext(nextContext);
    const validation = validateHostContext(sanitizedContext);
    if (!validation.valid) throw new Error(`Invalid host context: ${validation.code}`);
    context = sanitizedContext;
    send('context.replace', { context });
  }

  function updateTheme(nextTheme) {
    theme = nextTheme || {};
    send('theme.update', { theme });
  }

  function openRoute(view) {
    send('route.open', { view: normalizeEmbedView(view) });
  }

  return {
    sessionId,
    iframe,
    updateContext,
    setContext: updateContext,
    updateTheme,
    setTheme: updateTheme,
    openRoute,
    navigate: openRoute,
    setVisibility(visible) {
      send('visibility.change', { visible: Boolean(visible) });
    },
    refreshSession(bootstrapToken, expiresAt) {
      send('session.renew.response', { bootstrapToken, expiresAt });
    },
    openStandalone() {
      send('navigation.requested', { intent: 'open_standalone', context });
    },
    destroy() {
      if (lifecycle === 'destroyed') return;
      send('destroy');
      lifecycle = 'destroyed';
      window.clearTimeout(initializationTimer);
      initializationTimer = null;
      clearReadyDebounce();
      pendingInitMessageId = null;
      window.removeEventListener('message', handleMessage);
      teardownChannelPort();
      iframe.remove();
    },
  };
}

export const SmartAIEmbed = {
  async init(options) {
    if (typeof options?.tokenProvider !== 'function') throw new Error('tokenProvider is required');
    if (!options.embedOrigin) throw new Error('embedOrigin is required');
    const embedOrigin = parseTrustedEmbedOrigin(options.embedOrigin);
    const grant = validateBootstrapGrant(
      await options.tokenProvider({ reason: 'initialize' }),
      embedOrigin,
    );
    const trustedSessionId = grant.sessionId;

    const initialView = normalizeEmbedView(options.initialRoute);
    const embedUrl = new URL(grant.embedUrl.href);
    embedUrl.searchParams.set('shell', 'embed');
    embedUrl.searchParams.set('surface', options.surface === 'sidebar' ? 'sidebar' : 'workspace');
    embedUrl.searchParams.set('view', initialView);

    return new Promise((resolveClient, rejectClient) => {
      let settled = false;
      let client;
      client = createSmartAIEmbed({
        container: options.container,
        appUrl: embedUrl.href,
        context: grant.context || options.context,
        theme: options.theme,
        view: initialView,
        sessionId: trustedSessionId,
        bootstrapToken: grant.bootstrapToken,
        capabilities: options.hostCapabilities,
        initializationTimeoutMs: options.initializationTimeoutMs,
        onEvent: options.onEvent,
        async onSessionRenew() {
          const renewed = validateBootstrapGrant(
            await options.tokenProvider({ reason: 'renew', sessionId: trustedSessionId }),
            embedOrigin,
            trustedSessionId,
          );
          return { bootstrapToken: renewed.bootstrapToken, expiresAt: renewed.expiresAt };
        },
        onReady(metadata) {
          options.onReady?.(metadata);
          if (!settled) {
            settled = true;
            resolveClient(client);
          }
        },
        onNavigate: options.onNavigate,
        onError(error) {
          options.onError?.(error);
          if (!settled) {
            settled = true;
            client?.destroy();
            rejectClient(Object.assign(new Error(error?.code || 'SmartAI embed initialization failed'), { details: error }));
          }
        },
      });
    });
  },
};
