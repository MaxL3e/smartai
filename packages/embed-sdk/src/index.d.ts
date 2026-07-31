export type ExternalRef = { system: string; id: string; version?: string | number | null };

export type HostContext = {
  scene: 'job' | 'candidate' | 'task';
  enterpriseRef?: ExternalRef | null;
  jobRef: ExternalRef;
  taskRef?: ExternalRef | null;
  candidateRef?: ExternalRef | null;
  applicationRef?: ExternalRef | null;
  hostRoute?: string | null;
  returnIntent?: string;
  contextVersion: number;
  nonce: string;
  expiresAt: string;
  locale?: string;
  timeZone?: string;
};

export type BootstrapGrant = {
  sessionId: string;
  bootstrapToken: string;
  embedUrl: string;
  expiresAt: string;
  protocolVersion: '1.0';
  context?: HostContext;
};

export type TokenProviderInput =
  | { reason: 'initialize' }
  | { reason: 'renew'; sessionId: string };

export type EmbedClient = {
  readonly sessionId: string;
  readonly iframe: HTMLIFrameElement;
  setContext(context: HostContext): void;
  updateContext(context: HostContext): void;
  setTheme(theme: Record<string, unknown>): void;
  updateTheme(theme: Record<string, unknown>): void;
  navigate(route: string): void;
  openRoute(route: string): void;
  setVisibility(visible: boolean): void;
  refreshSession(bootstrapToken: string, expiresAt: string): void;
  openStandalone(): void;
  destroy(): void;
};

export declare const SmartAIEmbed: {
  init(options: {
    container: string | HTMLElement;
    embedOrigin: string;
    tokenProvider(input: TokenProviderInput): Promise<BootstrapGrant>;
    context: HostContext;
    initialRoute?: string;
    surface?: 'sidebar' | 'workspace';
    hostCapabilities?: string[];
    theme?: Record<string, unknown>;
    initializationTimeoutMs?: number;
    onReady?(metadata: unknown): void;
    onEvent?(event: unknown): void;
    onNavigate?(request: unknown): void;
    onError?(error: unknown): void;
  }): Promise<EmbedClient>;
};

export declare function createSmartAIEmbed(options: Record<string, unknown>): EmbedClient;
