import { createSmartAIEmbed } from '../../packages/embed-sdk/src/index.js';

const jobs = [
  { id: 'R2026-0718', title: '高级后端开发工程师', department: '数字科技部', location: '北京' },
  { id: 'R2026-0712', title: '财务共享中心经理', department: '财务管理部', location: '上海' },
  { id: 'R2026-0709', title: '能源市场分析师', department: '战略发展部', location: '北京' },
  { id: 'R2026-0626', title: '合规风控主管', department: '法律合规部', location: '深圳' },
];

const candidates = [
  { id: '1', name: '陈思远', title: '高级后端开发工程师', stage: '名单确认', score: 92, updated: '10分钟前' },
  { id: '2', name: '周雨晴', title: 'Java开发工程师', stage: '人才搜索', score: 88, updated: '25分钟前' },
  { id: '3', name: '何嘉伟', title: '平台研发工程师', stage: '待筛选', score: 84, updated: '昨天' },
  { id: '4', name: '林清妍', title: '后端研发工程师', stage: '待筛选', score: 79, updated: '昨天' },
];

const themes = {
  energy: { brandColor: '#173f4f', brandHoverColor: '#0f3441', accentColor: '#198269', borderColor: '#dce3e5', density: 'compact', radius: 5 },
  neutral: { brandColor: '#29333a', brandHoverColor: '#1f272c', accentColor: '#49717b', borderColor: '#d8dde0', density: 'compact', radius: 4 },
  classic: { brandColor: '#244e75', brandHoverColor: '#193b5a', accentColor: '#2b7f92', borderColor: '#d8e0e6', density: 'comfortable', radius: 6 },
};

const elements = {
  container: document.querySelector('#smartai-container'),
  region: document.querySelector('#plugin-region'),
  atsContent: document.querySelector('#ats-content'),
  jobSelect: document.querySelector('#job-select'),
  candidateSelect: document.querySelector('#candidate-select'),
  themeSelect: document.querySelector('#theme-select'),
  connection: document.querySelector('#connection-state'),
  surfaceLabel: document.querySelector('#surface-label'),
  lastEvent: document.querySelector('#last-event'),
  toast: document.querySelector('#host-toast'),
};

let mode = 'job-sidebar';
let embed = null;
let contextVersion = 1;

function option(value, label) {
  const item = document.createElement('option');
  item.value = value;
  item.textContent = label;
  return item;
}

jobs.forEach((job) => elements.jobSelect.append(option(job.id, job.title)));
candidates.forEach((candidate) => elements.candidateSelect.append(option(candidate.id, candidate.name)));

function selectedJob() {
  return jobs.find((item) => item.id === elements.jobSelect.value) || jobs[0];
}

function selectedCandidate() {
  return candidates.find((item) => item.id === elements.candidateSelect.value) || candidates[0];
}

function hostContext() {
  const job = selectedJob();
  const candidate = selectedCandidate();
  const candidateScene = mode === 'candidate-sidebar';
  return {
    scene: candidateScene ? 'candidate' : 'job',
    enterpriseRef: { system: 'huadong-energy-ats', id: 'tenant-hdny' },
    jobRef: { system: 'huadong-energy-ats', id: job.id, version: '7' },
    taskRef: { system: 'smartai', id: job.id, version: '3' },
    candidateRef: candidateScene ? { system: 'huadong-energy-ats', id: candidate.id, version: '12' } : null,
    applicationRef: candidateScene ? { system: 'huadong-energy-ats', id: `APP-${job.id}-${candidate.id}`, version: '5' } : null,
    hostRoute: candidateScene ? 'candidate.detail' : 'job.detail',
    returnIntent: 'return_to_context',
    contextVersion: contextVersion++,
    nonce: crypto.randomUUID(),
    expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    locale: 'zh-CN',
    timeZone: 'Asia/Shanghai',
  };
}

function appUrl() {
  const surface = mode === 'workspace' ? 'workspace' : 'sidebar';
  const view = mode === 'workspace' ? 'workspace' : 'workspace';
  return new URL(`${import.meta.env.BASE_URL}?shell=embed&surface=${surface}&view=${view}`, window.location.origin).href;
}

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.classList.add('visible');
  window.clearTimeout(showToast.timer);
  showToast.timer = window.setTimeout(() => elements.toast.classList.remove('visible'), 2200);
}

function describeEvent(message) {
  const labels = {
    'embed.ready': '插件已加载',
    'embed.initialized': '协议模拟已就绪',
    'context.accepted': '演示上下文已本地映射',
    'route.changed': '插件视图已切换',
    'navigation.requested': '请求返回 ATS',
    'action.completed': '业务操作已完成',
  };
  elements.lastEvent.textContent = labels[message.type] || `收到 ${message.type}`;
}

function mountEmbed() {
  embed?.destroy();
  elements.connection.classList.remove('connected');
  elements.connection.lastChild.textContent = '等待连接';
  elements.region.classList.toggle('workspace-mode', mode === 'workspace');
  elements.atsContent.classList.toggle('workspace-hidden', mode === 'workspace');
  elements.surfaceLabel.textContent = mode === 'workspace' ? '全页工作区' : mode === 'candidate-sidebar' ? '候选人侧栏' : '岗位侧栏';
  embed = createSmartAIEmbed({
    container: elements.container,
    appUrl: appUrl(),
    context: hostContext(),
    demoMode: true,
    theme: themes[elements.themeSelect.value],
    view: 'workspace',
    capabilities: ['CONTEXT_PUSH', 'HOST_NAVIGATION', 'AUTH_REFRESH', 'THEME_TOKENS'],
    onReady() {
      elements.connection.classList.add('connected');
      elements.connection.lastChild.textContent = '协议演示会话';
    },
    onEvent: describeEvent,
    onNavigate(payload) {
      if (payload.intent === 'open_workspace') {
        setMode('workspace');
        showToast('ATS 已切换到智能体全页工作区');
      } else {
        if (mode === 'workspace') setMode('job-sidebar');
        showToast('已返回 ATS 当前业务对象');
      }
    },
    async onSessionRenew() {
      await new Promise((resolve) => window.setTimeout(resolve, 350));
      showToast('本地协议模拟已生成新的演示令牌');
      return { bootstrapToken: `demo_${Date.now()}`, expiresAt: new Date(Date.now() + 5 * 60 * 1000).toISOString() };
    },
    onError(error) {
      elements.connection.classList.remove('connected');
      elements.connection.lastChild.textContent = error?.code || '连接异常';
    },
  });
}

function setMode(nextMode) {
  mode = nextMode;
  document.querySelectorAll('[data-mode]').forEach((button) => button.classList.toggle('active', button.dataset.mode === mode));
  elements.candidateSelect.closest('label').classList.toggle('disabled', mode !== 'candidate-sidebar');
  mountEmbed();
}

function updateAtsJob() {
  const job = selectedJob();
  document.querySelector('#breadcrumb-role').textContent = job.title;
  document.querySelector('#ats-role-title').textContent = job.title;
  document.querySelector('#ats-job-code').textContent = job.id;
  document.querySelector('#ats-department').textContent = job.department;
}

document.querySelector('#candidate-table').innerHTML = candidates.map((candidate) => `
  <tr data-candidate="${candidate.id}">
    <td><span class="candidate-avatar">${candidate.name.slice(0, 1)}</span><span><strong>${candidate.name}</strong><small>${candidate.title}</small></span></td>
    <td><span class="stage">${candidate.stage}</span></td>
    <td><b class="score">${candidate.score}</b></td>
    <td>${candidate.updated}</td>
    <td>李佳</td>
  </tr>
`).join('');

document.querySelectorAll('[data-mode]').forEach((button) => button.addEventListener('click', () => setMode(button.dataset.mode)));
elements.jobSelect.addEventListener('change', () => {
  updateAtsJob();
  embed?.updateContext(hostContext());
  showToast('岗位上下文已推送给智能体');
});
elements.candidateSelect.addEventListener('change', () => {
  if (mode !== 'candidate-sidebar') setMode('candidate-sidebar');
  else embed?.updateContext(hostContext());
  showToast('候选人上下文已推送给智能体');
});
elements.themeSelect.addEventListener('change', () => {
  embed?.updateTheme(themes[elements.themeSelect.value]);
  showToast('客户主题令牌已更新');
});
document.querySelector('#renew-session').addEventListener('click', () => {
  embed?.refreshSession(`demo_${Date.now()}`, new Date(Date.now() + 5 * 60 * 1000).toISOString());
  showToast('演示会话已在本地协议中续期');
});
document.querySelector('#candidate-table').addEventListener('click', (event) => {
  const row = event.target.closest('tr[data-candidate]');
  if (!row) return;
  elements.candidateSelect.value = row.dataset.candidate;
  setMode('candidate-sidebar');
});

updateAtsJob();
setMode('job-sidebar');
