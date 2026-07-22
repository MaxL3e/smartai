import { useEffect, useMemo, useState } from 'react';
import {
  Activity,
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Bell,
  BookOpenText,
  Bot,
  BriefcaseBusiness,
  Building2,
  CalendarDays,
  Check,
  CheckCircle2,
  ChevronDown,
  Circle,
  CircleHelp,
  Clock3,
  Database,
  Download,
  Eye,
  FileText,
  GraduationCap,
  History,
  LayoutDashboard,
  ListFilter,
  LockKeyhole,
  MapPin,
  MoreHorizontal,
  Play,
  Plus,
  Search,
  Send,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  UploadCloud,
  UserCheck,
  UsersRound,
  Video,
  X,
} from 'lucide-react';

const navItems = [
  { id: 'workspace', label: '智能体工作台', icon: LayoutDashboard },
  { id: 'tasks', label: '招聘任务', icon: BriefcaseBusiness, count: 4 },
  { id: 'talent', label: '人才匹配', icon: UsersRound, count: 12 },
  { id: 'interviews', label: '面试协同', icon: Video, count: 3 },
  { id: 'evaluation', label: '综合评价', icon: BadgeCheck },
];

const manageItems = [
  { id: 'knowledge', label: '知识库', icon: BookOpenText },
  { id: 'audit', label: '运行审计', icon: Activity },
];

const flowSteps = [
  { title: '岗位方案', note: '已确认' },
  { title: '人才搜索', note: '匹配完成' },
  { title: '名单确认', note: '待确认' },
  { title: '在线面试', note: '未开始' },
  { title: '综合评价', note: '未开始' },
];

const candidatesSeed = [
  {
    id: 1,
    name: '陈思远',
    initials: '陈',
    title: '高级后端开发工程师',
    company: '云启科技',
    years: '7年',
    education: '硕士',
    school: '北京邮电大学',
    score: 92,
    status: '强烈推荐',
    tone: 'green',
    highlights: ['大型分布式系统', 'Java / Spring Cloud', '央企数字化项目'],
    risks: ['管理经验信息不足'],
    evidence: [
      { label: '核心技术能力', value: 28, max: 30, quote: '主导订单中心微服务改造，峰值吞吐提升3.2倍。' },
      { label: '项目复杂度', value: 26, max: 30, quote: '负责12个核心服务的架构治理与稳定性建设。' },
      { label: '行业与业务', value: 19, max: 20, quote: '连续两年参与大型央企数字化平台建设。' },
      { label: '成果证据', value: 19, max: 20, quote: '故障率下降47%，交付周期缩短30%。' },
    ],
  },
  {
    id: 2,
    name: '周雨晴',
    initials: '周',
    title: 'Java开发工程师',
    company: '智源信息',
    years: '6年',
    education: '硕士',
    school: '华中科技大学',
    score: 88,
    status: '推荐',
    tone: 'green',
    highlights: ['高并发交易系统', '技术方案设计', '跨团队协作'],
    risks: ['央企项目经验较少'],
    evidence: [
      { label: '核心技术能力', value: 27, max: 30, quote: '熟练使用Java技术栈并承担核心模块设计。' },
      { label: '项目复杂度', value: 27, max: 30, quote: '参与日均千万级交易系统重构。' },
      { label: '行业与业务', value: 16, max: 20, quote: '具备金融科技业务经验，领域迁移成本较低。' },
      { label: '成果证据', value: 18, max: 20, quote: '通过缓存分层将接口耗时降低42%。' },
    ],
  },
  {
    id: 3,
    name: '何嘉伟',
    initials: '何',
    title: '平台研发工程师',
    company: '数联软件',
    years: '8年',
    education: '本科',
    school: '西安电子科技大学',
    score: 84,
    status: '推荐',
    tone: 'blue',
    highlights: ['平台工程', 'DevOps体系', '稳定性治理'],
    risks: ['学历与优先条件有差距'],
    evidence: [
      { label: '核心技术能力', value: 26, max: 30, quote: '建设统一研发效能平台，覆盖200余名研发人员。' },
      { label: '项目复杂度', value: 28, max: 30, quote: '负责多云环境持续交付与可观测体系。' },
      { label: '行业与业务', value: 14, max: 20, quote: '平台经验突出，业务系统交付经验一般。' },
      { label: '成果证据', value: 16, max: 20, quote: '发布失败率由8.6%下降至1.9%。' },
    ],
  },
  {
    id: 4,
    name: '林清妍',
    initials: '林',
    title: '后端研发工程师',
    company: '远望互联',
    years: '5年',
    education: '硕士',
    school: '同济大学',
    score: 79,
    status: '待确认',
    tone: 'amber',
    highlights: ['基础扎实', '学习能力', '数据平台经验'],
    risks: ['核心项目主导经验偏少'],
    evidence: [
      { label: '核心技术能力', value: 25, max: 30, quote: '参与数据服务平台核心接口开发。' },
      { label: '项目复杂度', value: 22, max: 30, quote: '项目规模匹配，但主导职责证据不足。' },
      { label: '行业与业务', value: 16, max: 20, quote: '具备企业服务领域经验。' },
      { label: '成果证据', value: 16, max: 20, quote: '负责模块连续18个月无重大生产事故。' },
    ],
  },
  {
    id: 5,
    name: '王博文',
    initials: '王',
    title: 'Java工程师',
    company: '星河网络',
    years: '4年',
    education: '本科',
    school: '南京理工大学',
    score: 71,
    status: '谨慎考虑',
    tone: 'gray',
    highlights: ['Java基础', '执行力'],
    risks: ['项目复杂度不足', '缺少架构设计证据'],
    evidence: [
      { label: '核心技术能力', value: 23, max: 30, quote: '能够独立完成常规业务模块开发。' },
      { label: '项目复杂度', value: 18, max: 30, quote: '经历以中小型应用维护为主。' },
      { label: '行业与业务', value: 15, max: 20, quote: '有企业服务经验，相关程度一般。' },
      { label: '成果证据', value: 15, max: 20, quote: '简历成果描述缺少量化数据。' },
    ],
  },
];

const knowledgeSeed = [
  { id: 1, type: '岗位知识', title: '软件研发岗位族标准（2026版）', format: 'DOCX', owner: '组织人事部', updated: '2026-07-18', status: '可用', refs: 26, version: 'v3.2' },
  { id: 2, type: '岗位知识', title: '高级后端开发工程师历史JD合集', format: 'XLSX', owner: '数字科技部', updated: '2026-07-16', status: '可用', refs: 18, version: 'v2.6' },
  { id: 3, type: '人才画像', title: '研发岗位高绩效人才特征分析', format: 'PDF', owner: '人才发展中心', updated: '2026-07-12', status: '可用', refs: 41, version: 'v1.8' },
  { id: 4, type: '人才画像', title: '后端岗位试用期评价数据（脱敏）', format: 'XLSX', owner: '人力共享中心', updated: '2026-07-09', status: '可用', refs: 33, version: 'v2.1' },
  { id: 5, type: '制度流程', title: '社会招聘管理办法', format: 'PDF', owner: '组织人事部', updated: '2026-06-28', status: '可用', refs: 12, version: 'v4.0' },
  { id: 6, type: '制度流程', title: '招聘审批权限与面试官规则', format: 'DOCX', owner: '组织人事部', updated: '2026-06-26', status: '可用', refs: 9, version: 'v2.4' },
  { id: 7, type: '岗位知识', title: '2024-2025研发招聘复盘', format: 'PPTX', owner: '招聘中心', updated: '2026-06-20', status: '待复核', refs: 3, version: 'v1.0' },
  { id: 8, type: '制度流程', title: '候选人数据合规与留存规范', format: 'PDF', owner: '法律合规部', updated: '2026-06-18', status: '可用', refs: 17, version: 'v1.5' },
  { id: 9, type: '岗位知识', title: '数据治理岗位族任职资格标准', format: 'DOCX', owner: '数字科技部', updated: '2026-07-20', status: '可用', refs: 15, version: 'v1.4' },
  { id: 10, type: '人才画像', title: '数据治理项目骨干成功特征分析', format: 'PDF', owner: '人才发展中心', updated: '2026-07-19', status: '可用', refs: 11, version: 'v1.2' },
];

const initialEvents = [
  { time: '14:32:16', title: '完成人才库检索', detail: '在 2,846 份简历中筛选出 12 位候选人', type: 'success' },
  { time: '14:31:48', title: '应用岗位评分卡', detail: '使用“高级后端开发工程师评分卡 v3.2”', type: 'success' },
  { time: '14:31:22', title: '读取知识库资料', detail: '引用 4 份岗位知识与 2 份人才画像', type: 'success' },
  { time: '14:30:59', title: '人工确认岗位方案', detail: '招聘经理李佳确认 JD 与推荐标准', type: 'human' },
];

const initialTasks = [
  { code: 'R2026-0718', role: '高级后端开发工程师', dept: '数字科技部', city: '北京', count: '2人', headcount: 2, stage: '名单确认', progress: 48, owner: '李佳', due: '08-15', tone: 'blue', recruitmentType: '社会招聘', priority: '高', requirement: '负责集团级数字化平台核心服务的架构设计与研发。' },
  { code: 'R2026-0712', role: '财务共享中心经理', dept: '财务管理部', city: '上海', count: '1人', headcount: 1, stage: '在线面试', progress: 66, owner: '王楠', due: '08-08', tone: 'amber', recruitmentType: '社会招聘', priority: '中', requirement: '负责财务共享中心运营管理与流程优化。' },
  { code: 'R2026-0709', role: '能源市场分析师', dept: '战略发展部', city: '北京', count: '3人', headcount: 3, stage: '人才搜索', progress: 31, owner: '张晨', due: '08-20', tone: 'green', recruitmentType: '校园招聘', priority: '中', requirement: '跟踪能源市场趋势并形成经营分析建议。' },
  { code: 'R2026-0626', role: '合规风控主管', dept: '法律合规部', city: '深圳', count: '1人', headcount: 1, stage: '综合评价', progress: 86, owner: '陈敏', due: '07-30', tone: 'gray', recruitmentType: '社会招聘', priority: '高', requirement: '建立业务合规审查和风险预警机制。' },
];

function loadTasks() {
  try {
    const stored = window.localStorage.getItem('smartai.recruitmentTasks');
    return stored ? JSON.parse(stored) : initialTasks;
  } catch {
    return initialTasks;
  }
}

function getRolePlan(task) {
  const role = task?.role || '';
  if (role.includes('数据')) {
    return {
      duties: [
        '负责集团数据标准、主数据和数据质量体系的规划与持续建设。',
        '识别跨系统数据问题，建立质量监控、问题闭环和治理度量机制。',
        '协同业务与技术团队推进数据资产盘点、目录建设和标准落地。',
        '沉淀数据治理制度、方法与工具，支撑经营分析和智能化应用。',
      ],
      requirements: [
        '5年以上数据治理、数据管理或企业数据平台建设经验',
        '熟悉数据标准、主数据、元数据与数据质量管理方法',
        '具有大型企业跨部门数据治理项目的规划和落地经验',
        '具备良好的业务理解、沟通协调与项目推动能力',
      ],
      tags: ['大型企业数据治理', '数据标准体系', '跨部门项目推动'],
      scoreRules: [
        { label: '治理专业能力', weight: 30, detail: '数据标准、主数据、质量与元数据' },
        { label: '项目复杂度', weight: 30, detail: '组织范围、承担角色、落地规模' },
        { label: '业务与行业', weight: 20, detail: '业务理解、大型企业治理经验' },
        { label: '成果证据', weight: 20, detail: '质量提升、覆盖范围、应用成效' },
      ],
      sourceIds: [9, 10, 1, 5],
    };
  }
  if (role.includes('后端') || role.includes('Java')) {
    return {
      duties: [
        '负责集团数字化平台核心业务服务的设计、研发与持续演进。',
        '参与技术架构评审，识别系统风险并推动性能和稳定性治理。',
        '沉淀通用研发规范与技术组件，提升团队工程交付效率。',
        '协同产品、业务和测试团队，保障重点项目按计划高质量落地。',
      ],
      requirements: [
        '5年以上 Java 后端研发经验，具备复杂业务系统设计能力',
        '熟悉 Spring Boot、Spring Cloud、MySQL 与消息中间件',
        '具有高并发、分布式系统的性能治理和稳定性建设经验',
        '具备良好的跨团队沟通、技术方案表达与项目推动能力',
      ],
      tags: ['央企数字化项目经验', '大型分布式系统经验', '技术带教经验'],
      scoreRules: [
        { label: '核心技术能力', weight: 30, detail: '技术栈覆盖、架构设计、问题诊断' },
        { label: '项目复杂度', weight: 30, detail: '系统规模、承担角色、复杂问题处理' },
        { label: '行业与业务', weight: 20, detail: '企业服务、央企数字化、业务理解' },
        { label: '成果证据', weight: 20, detail: '可量化效果、稳定性、交付质量' },
      ],
      sourceIds: [1, 2, 3, 4],
    };
  }
  return {
    duties: [
      '负责集团数字化平台核心业务服务的设计、研发与持续演进。',
      '参与专业方案评审，识别关键风险并推动质量与效能提升。',
      '沉淀通用工作规范与专业方法，提升团队协作和交付效率。',
      '协同相关部门，保障重点项目按计划高质量落地。',
    ],
    requirements: [
      '5年以上相关岗位工作经验，具备复杂项目设计与落地能力',
      '熟悉岗位核心专业方法、工作流程与常用工具',
      '具有大型企业复杂项目的问题分析和推动经验',
      '具备良好的跨团队沟通、方案表达与项目推动能力',
    ],
    tags: ['大型企业项目经验', '复杂项目落地', '跨团队协作'],
    scoreRules: [
      { label: '核心专业能力', weight: 30, detail: '专业知识、方案设计、问题诊断' },
      { label: '项目复杂度', weight: 30, detail: '项目规模、承担角色、复杂问题处理' },
      { label: '行业与业务', weight: 20, detail: '大型企业经验、业务理解' },
      { label: '成果证据', weight: 20, detail: '可量化效果、质量、交付成果' },
    ],
    sourceIds: [1, 3, 5, 6],
  };
}

function classNames(...values) {
  return values.filter(Boolean).join(' ');
}

function StatusPill({ tone = 'gray', children }) {
  return <span className={`status-pill ${tone}`}>{children}</span>;
}

function PageHeader({ eyebrow, title, description, actions }) {
  return (
    <div className="page-header">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </div>
  );
}

function App() {
  const [view, setView] = useState('workspace');
  const [flowStep, setFlowStep] = useState(2);
  const [selectedCandidate, setSelectedCandidate] = useState(1);
  const [selectedCandidates, setSelectedCandidates] = useState([1, 2]);
  const [knowledge, setKnowledge] = useState(knowledgeSeed);
  const [events, setEvents] = useState(initialEvents);
  const [tasks, setTasks] = useState(loadTasks);
  const [activeTaskId, setActiveTaskId] = useState(initialTasks[0].code);
  const [toast, setToast] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [taskModalOpen, setTaskModalOpen] = useState(false);

  const candidate = candidatesSeed.find((item) => item.id === selectedCandidate);
  const activeTask = tasks.find((item) => item.code === activeTaskId) || tasks[0];

  useEffect(() => {
    window.localStorage.setItem('smartai.recruitmentTasks', JSON.stringify(tasks));
  }, [tasks]);

  function notify(message) {
    setToast(message);
    window.clearTimeout(window.__smartToast);
    window.__smartToast = window.setTimeout(() => setToast(''), 2600);
  }

  function pushEvent(title, detail, type = 'human') {
    const now = new Date().toLocaleTimeString('zh-CN', { hour12: false });
    setEvents((items) => [{ time: now, title, detail, type }, ...items]);
  }

  function advanceFlow() {
    if (flowStep >= 4) {
      setView('evaluation');
      notify('已进入综合评价环节');
      return;
    }
    const next = flowStep + 1;
    setFlowStep(next);
    const messages = {
      3: ['候选名单已确认', `已选择 ${selectedCandidates.length} 位候选人，准备发起面试`],
      4: ['在线面试已发起', '邀请已发送，智能体将持续同步应答状态'],
    };
    const [title, detail] = messages[next];
    pushEvent(title, detail);
    notify(title);
  }

  function toggleCandidate(id) {
    setSelectedCandidates((ids) =>
      ids.includes(id) ? ids.filter((item) => item !== id) : [...ids, id],
    );
  }

  function addKnowledge(item) {
    setKnowledge((items) => [
      {
        id: Date.now(),
        type: item.type,
        title: item.title || '未命名知识资料',
        format: item.format || 'DOCX',
        owner: item.owner || '当前用户',
        updated: '2026-07-22',
        status: '解析中',
        refs: 0,
        version: 'v1.0',
      },
      ...items,
    ]);
    setModalOpen(false);
    pushEvent('知识资料已上传', item.title || '未命名知识资料进入解析队列');
    notify('资料已进入解析队列');
  }

  function createTask(form) {
    const now = new Date();
    const datePart = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
    const sequence = String(tasks.length + 1).padStart(2, '0');
    const code = `R${datePart}-${sequence}`;
    const due = form.due ? form.due.slice(5).replace('-', '-') : '待确定';
    const task = {
      code,
      role: form.role.trim(),
      dept: form.dept,
      city: form.city.trim(),
      count: `${form.headcount}人`,
      headcount: Number(form.headcount),
      stage: '岗位方案',
      progress: 12,
      owner: '李佳',
      due,
      tone: 'green',
      recruitmentType: form.recruitmentType,
      priority: form.priority,
      requirement: form.requirement.trim(),
    };
    setTasks((items) => [task, ...items]);
    setActiveTaskId(code);
    setTaskModalOpen(false);
    setView('roleplan');
    pushEvent('创建招聘任务', `${task.role} · ${task.dept} · 招聘 ${task.count}`, 'human');
    notify(`招聘任务 ${code} 已创建`);
  }

  const context = {
    view,
    setView,
    flowStep,
    setFlowStep,
    candidate,
    selectedCandidate,
    setSelectedCandidate,
    selectedCandidates,
    toggleCandidate,
    knowledge,
    tasks,
    activeTask,
    setActiveTaskId,
    events,
    notify,
    pushEvent,
    advanceFlow,
    setModalOpen,
    setTaskModalOpen,
  };

  return (
    <div className="app-shell">
      <Sidebar view={view} setView={setView} taskCount={tasks.length} />
      <div className="app-column">
        <Topbar setView={setView} />
        <main className="main-content">
          {view === 'workspace' && <Workspace {...context} />}
          {view === 'roleplan' && <RolePlan {...context} />}
          {view === 'tasks' && <Tasks {...context} />}
          {view === 'talent' && <Talent {...context} />}
          {view === 'interviews' && <Interviews {...context} />}
          {view === 'evaluation' && <Evaluation {...context} />}
          {view === 'knowledge' && <Knowledge {...context} />}
          {view === 'audit' && <Audit {...context} />}
        </main>
      </div>
      {modalOpen && <KnowledgeModal onClose={() => setModalOpen(false)} onSubmit={addKnowledge} />}
      {taskModalOpen && <TaskModal onClose={() => setTaskModalOpen(false)} onSubmit={createTask} />}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toast}
        </div>
      )}
    </div>
  );
}

function Sidebar({ view, setView, taskCount }) {
  return (
    <aside className="sidebar">
      <button className="brand" onClick={() => setView('workspace')} aria-label="返回工作台">
        <span className="brand-mark"><Sparkles size={21} /></span>
        <span><strong>知聘</strong><small>招聘智能体</small></span>
      </button>

      <nav className="primary-nav" aria-label="主导航">
        <span className="nav-label">招聘执行</span>
        {navItems.map((item) => <NavItem key={item.id} item={item.id === 'tasks' ? { ...item, count: taskCount } : item} active={view === item.id} onClick={() => setView(item.id)} />)}
        <span className="nav-label manage-label">智能体管理</span>
        {manageItems.map((item) => <NavItem key={item.id} item={item} active={view === item.id} onClick={() => setView(item.id)} />)}
      </nav>

      <div className="sidebar-foot">
        <div className="secure-note">
          <ShieldCheck size={18} />
          <span><strong>安全运行</strong><small>全部操作已留痕</small></span>
        </div>
        <button className="user-menu">
          <span className="avatar avatar-blue">李</span>
          <span><strong>李佳</strong><small>招聘经理</small></span>
          <MoreHorizontal size={17} />
        </button>
      </div>
    </aside>
  );
}

function NavItem({ item, active, onClick }) {
  const Icon = item.icon;
  return (
    <button className={classNames('nav-item', active && 'active')} onClick={onClick} aria-label={item.label}>
      <Icon size={18} />
      <span>{item.label}</span>
      {item.count && <em>{item.count}</em>}
    </button>
  );
}

function Topbar({ setView }) {
  return (
    <header className="topbar">
      <div className="mobile-brand"><Sparkles size={18} /><strong>知聘</strong></div>
      <button className="enterprise-switcher">
        <span className="enterprise-icon"><Building2 size={17} /></span>
        <span>华岳能源集团</span>
        <ChevronDown size={15} />
      </button>
      <div className="topbar-actions">
        <button className="command-search" onClick={() => setView('talent')}>
          <Search size={16} />
          <span>搜索候选人或任务</span>
          <kbd>⌘ K</kbd>
        </button>
        <button className="icon-button" title="帮助"><CircleHelp size={18} /></button>
        <button className="icon-button notification" title="通知"><Bell size={18} /><i /></button>
      </div>
    </header>
  );
}

function Workspace({ flowStep, selectedCandidates, setView, advanceFlow, events }) {
  return (
    <>
      <PageHeader
        eyebrow="招聘任务 / R2026-0718"
        title="高级后端开发工程师招聘"
        description="数字科技部 · 北京 · 社会招聘 2 人"
        actions={
          <>
            <button className="btn secondary" onClick={() => setView('audit')}><Activity size={17} />运行记录</button>
            <button className="btn primary" onClick={advanceFlow}>
              {flowStep === 2 ? '确认候选名单' : flowStep === 3 ? '发起在线面试' : '查看综合评价'}
              <ArrowRight size={17} />
            </button>
          </>
        }
      />

      <section className="flow-surface" aria-label="招聘流程进度">
        <div className="flow-topline">
          <div><Bot size={18} /><strong>智能体执行中</strong><span className="pulse-dot" />正在等待人工确认候选名单</div>
          <span>已运行 8 分 42 秒</span>
        </div>
        <div className="flow-steps">
          {flowSteps.map((step, index) => {
            const completed = index < flowStep;
            const active = index === flowStep;
            return (
              <button className={classNames('flow-step', completed && 'completed', active && 'current')} key={step.title} onClick={() => index === 0 && setView('roleplan')}>
                <div className="step-line" />
                <span className="step-dot">{completed ? <Check size={14} /> : index + 1}</span>
                <div><strong>{step.title}</strong><small>{completed ? step.note : active ? '等待人工确认' : step.note}</small></div>
              </button>
            );
          })}
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="panel task-panel">
          <div className="panel-heading">
            <div><span className="section-kicker">本轮产出</span><h2>人才匹配结果</h2></div>
            <button className="text-button" onClick={() => setView('talent')}>查看全部 12 人 <ArrowRight size={15} /></button>
          </div>
          <div className="metric-strip">
            <div><span>人才库检索</span><strong>2,846</strong><small>份有效简历</small></div>
            <div><span>进入推荐</span><strong>12</strong><small>匹配度 ≥ 70</small></div>
            <div><span>强推荐</span><strong>3</strong><small>匹配度 ≥ 88</small></div>
            <div><span>已选择</span><strong>{selectedCandidates.length}</strong><small>等待确认</small></div>
          </div>
          <div className="candidate-preview-list">
            {candidatesSeed.slice(0, 3).map((person, index) => (
              <button className="candidate-preview" key={person.id} onClick={() => setView('talent')}>
                <span className="rank">{String(index + 1).padStart(2, '0')}</span>
                <span className={`avatar avatar-${index + 1}`}>{person.initials}</span>
                <span className="candidate-copy"><strong>{person.name}</strong><small>{person.title} · {person.years}</small></span>
                <span className="candidate-tags"><em>{person.highlights[0]}</em><em>{person.highlights[1]}</em></span>
                <span className="match-score"><strong>{person.score}</strong><small>匹配度</small></span>
                <ArrowRight size={16} />
              </button>
            ))}
          </div>
          <div className="decision-bar">
            <div><UserCheck size={20} /><span><strong>需要你的确认</strong><small>智能体建议邀请前 3 位候选人进入在线面试</small></span></div>
            <button className="btn primary" onClick={advanceFlow}>确认 {selectedCandidates.length} 人并继续 <ArrowRight size={17} /></button>
          </div>
        </section>

        <aside className="panel agent-panel">
          <div className="panel-heading">
            <div><span className="section-kicker">实时状态</span><h2>智能体动态</h2></div>
            <button className="icon-button small" title="查看运行审计" onClick={() => setView('audit')}><History size={16} /></button>
          </div>
          <div className="agent-identity">
            <span className="agent-orbit"><Bot size={25} /></span>
            <div><strong>招聘执行智能体</strong><small><i />在线 · 受控执行</small></div>
          </div>
          <div className="event-list compact">
            {events.slice(0, 4).map((event, index) => (
              <div className="event-item" key={`${event.time}-${index}`}>
                <span className={classNames('event-mark', event.type)}>{event.type === 'human' ? <UserCheck size={13} /> : <Check size={13} />}</span>
                <div><strong>{event.title}</strong><p>{event.detail}</p><time>{event.time}</time></div>
              </div>
            ))}
          </div>
          <button className="full-link" onClick={() => setView('audit')}>查看完整运行记录 <ArrowRight size={15} /></button>
        </aside>
      </div>

      <section className="knowledge-citations">
        <div className="citation-title"><Database size={20} /><div><strong>本次任务的知识依据</strong><small>所有生成与推荐均可回溯至企业资料</small></div></div>
        <div className="citation-files">
          <button onClick={() => setView('knowledge')}><FileText size={17} /><span><strong>研发岗位族标准</strong><small>岗位知识 · v3.2</small></span><Eye size={15} /></button>
          <button onClick={() => setView('knowledge')}><FileText size={17} /><span><strong>高绩效人才特征分析</strong><small>人才画像 · v1.8</small></span><Eye size={15} /></button>
          <button onClick={() => setView('knowledge')}><FileText size={17} /><span><strong>社会招聘管理办法</strong><small>制度流程 · v4.0</small></span><Eye size={15} /></button>
        </div>
        <button className="text-button" onClick={() => setView('knowledge')}>管理知识库 <ArrowRight size={15} /></button>
      </section>
    </>
  );
}

function RolePlan({ setView, notify, pushEvent, activeTask }) {
  const generatedPlan = useMemo(() => getRolePlan(activeTask), [activeTask]);
  const [editing, setEditing] = useState(false);
  const [summary, setSummary] = useState(activeTask?.requirement || '负责核心业务工作，持续提升组织效能与业务支撑能力。');
  const [requirements, setRequirements] = useState(generatedPlan.requirements);
  const scoreRules = generatedPlan.scoreRules;

  function savePlan() {
    setEditing(false);
    pushEvent('人工确认岗位方案', '招聘经理确认 JD、任职标准与评分卡 v3.2', 'human');
    notify('岗位方案已保存并确认');
  }

  return (
    <>
      <PageHeader
        eyebrow={`招聘任务 ${activeTask?.code || 'R2026-0718'} / 岗位方案`}
        title={activeTask?.role || '高级后端开发工程师'}
        description={`${activeTask?.dept || '数字科技部'} · ${activeTask?.city || '北京'} · 招聘 ${activeTask?.count || '2人'} · 智能体生成方案待审核`}
        actions={<><button className="btn secondary" onClick={() => setEditing((value) => !value)}>{editing ? <X size={16} /> : <FileText size={16} />}{editing ? '取消编辑' : '编辑方案'}</button><button className="btn primary" onClick={savePlan}><CheckCircle2 size={17} />确认岗位方案</button></>}
      />
      <section className="plan-source-band">
        <div><Sparkles size={19} /><span><strong>岗位方案由智能体生成</strong><small>融合 6 份岗位资料、18 次历史招聘与 27 条入职后表现记录</small></span></div>
        <StatusPill tone="green">知识依据完整</StatusPill>
      </section>
      <div className="role-plan-layout">
        <section className="panel role-document">
          <div className="panel-heading"><div><span className="section-kicker">岗位说明书</span><h2>JD 建议稿</h2></div><span className="version-label"><History size={14} />基于历史版本 v2.6</span></div>
          <div className="document-block">
            <h3>岗位职责概述</h3>
            {editing ? <textarea value={summary} onChange={(event) => setSummary(event.target.value)} /> : <p>{summary}</p>}
          </div>
          <div className="document-block">
            <h3>核心职责</h3>
            <ol>
              {generatedPlan.duties.map((item) => <li key={item}>{item}</li>)}
            </ol>
          </div>
          <div className="document-block">
            <h3>任职要求</h3>
            <div className="requirement-list">
              {requirements.map((item, index) => editing ? (
                <label key={index}><span>{index + 1}</span><input value={item} onChange={(event) => setRequirements((items) => items.map((value, itemIndex) => itemIndex === index ? event.target.value : value))} /></label>
              ) : <p key={item}><Check size={14} />{item}</p>)}
            </div>
          </div>
          <div className="document-block optional-block">
            <h3>优先条件</h3>
            <div className="tag-list">{generatedPlan.tags.map((item) => <span key={item}>{item}</span>)}</div>
          </div>
        </section>
        <aside className="plan-side">
          <section className="panel scoring-card">
            <div className="panel-heading"><div><span className="section-kicker">人才推荐标准</span><h2>固定评分卡</h2></div><StatusPill tone="blue">v3.2</StatusPill></div>
            <div className="score-rule-list">
              {scoreRules.map((rule) => <div className="score-rule" key={rule.label}><span className="rule-weight">{rule.weight}<small>%</small></span><div><strong>{rule.label}</strong><p>{rule.detail}</p><i><b style={{ width: `${rule.weight * 2.5}%` }} /></i></div></div>)}
            </div>
            <div className="threshold-list"><div><span>强烈推荐</span><strong>≥ 88</strong></div><div><span>推荐</span><strong>80-87</strong></div><div><span>待确认</span><strong>70-79</strong></div></div>
          </section>
          <section className="panel source-panel">
            <div className="panel-heading"><div><span className="section-kicker">生成依据</span><h2>知识来源</h2></div><button className="icon-button small" onClick={() => setView('knowledge')}><ArrowRight size={16} /></button></div>
            {generatedPlan.sourceIds.map((id) => knowledgeSeed.find((item) => item.id === id)).filter(Boolean).map((item) => <button className="plan-source" key={item.id} onClick={() => setView('knowledge')}><FileText size={17} /><span><strong>{item.title}</strong><small>{item.type} · {item.version}</small></span><Eye size={14} /></button>)}
          </section>
          <button className="back-workspace" onClick={() => setView('workspace')}><ArrowLeft size={16} />返回招聘任务工作台</button>
        </aside>
      </div>
    </>
  );
}

function Tasks({ setView, tasks, setActiveTaskId, setTaskModalOpen }) {
  return (
    <>
      <PageHeader eyebrow="招聘执行" title="招聘任务" description="统一管理由智能体协同执行的招聘任务"
        actions={<button className="btn primary" onClick={() => setTaskModalOpen(true)}><Plus size={17} />新建招聘任务</button>} />
      <section className="toolbar-band">
        <label className="search-field"><Search size={16} /><input placeholder="搜索岗位、部门或任务编号" /></label>
        <button className="btn secondary"><ListFilter size={16} />全部状态<ChevronDown size={14} /></button>
        <button className="btn secondary"><Building2 size={16} />全部部门<ChevronDown size={14} /></button>
      </section>
      <section className="table-panel">
        <div className="data-table task-table">
          <div className="table-row table-head"><span>招聘任务</span><span>招聘信息</span><span>当前阶段</span><span>负责人</span><span>计划完成</span><span /></div>
          {tasks.map((task) => (
            <button className="table-row" key={task.code} onClick={() => { setActiveTaskId(task.code); setView(task.code === 'R2026-0718' ? 'workspace' : 'roleplan'); }}>
              <span className="cell-main"><strong>{task.role}</strong><small>{task.code} · {task.dept}</small></span>
              <span><strong>{task.city} · {task.count}</strong><small>社会招聘</small></span>
              <span className="progress-cell"><StatusPill tone={task.tone}>{task.stage}</StatusPill><i><b style={{ width: `${task.progress}%` }} /></i></span>
              <span className="owner-cell"><em className="mini-avatar">{task.owner.slice(0, 1)}</em>{task.owner}</span>
              <span>{task.due}</span>
              <span><ArrowRight size={16} /></span>
            </button>
          ))}
        </div>
      </section>
    </>
  );
}

function TaskModal({ onClose, onSubmit }) {
  const [form, setForm] = useState({
    role: '',
    dept: '数字科技部',
    city: '北京',
    headcount: 1,
    recruitmentType: '社会招聘',
    priority: '中',
    due: '2026-08-31',
    requirement: '',
    useKnowledge: true,
  });

  function update(field, value) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function submit(event) {
    event.preventDefault();
    onSubmit(form);
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <form className="modal task-modal" onSubmit={submit} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div><span className="section-kicker">招聘需求</span><h2>新建招聘任务</h2><p>创建后由智能体生成岗位方案和人才推荐标准</p></div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>

        <div className="form-section">
          <h3>基本信息</h3>
          <div className="form-grid">
            <label className="span-2"><span>招聘岗位 <em>*</em></span><input autoFocus required value={form.role} onChange={(event) => update('role', event.target.value)} placeholder="例如：数据治理专家" /></label>
            <label><span>需求部门</span><select value={form.dept} onChange={(event) => update('dept', event.target.value)}><option>数字科技部</option><option>财务管理部</option><option>战略发展部</option><option>法律合规部</option><option>组织人事部</option></select></label>
            <label><span>工作地点</span><input required value={form.city} onChange={(event) => update('city', event.target.value)} /></label>
            <label><span>招聘人数</span><input required type="number" min="1" max="99" value={form.headcount} onChange={(event) => update('headcount', event.target.value)} /></label>
            <label><span>招聘类型</span><select value={form.recruitmentType} onChange={(event) => update('recruitmentType', event.target.value)}><option>社会招聘</option><option>校园招聘</option><option>内部竞聘</option></select></label>
            <label><span>期望完成日期</span><input required type="date" value={form.due} onChange={(event) => update('due', event.target.value)} /></label>
            <label><span>优先级</span><select value={form.priority} onChange={(event) => update('priority', event.target.value)}><option>高</option><option>中</option><option>低</option></select></label>
          </div>
        </div>

        <div className="form-section">
          <h3>招聘需求</h3>
          <label className="textarea-label"><span>岗位背景与核心要求 <em>*</em></span><textarea required value={form.requirement} onChange={(event) => update('requirement', event.target.value)} placeholder="简要描述招聘原因、核心职责、必须具备的经验或能力。智能体将据此检索企业知识并生成岗位方案。" /></label>
        </div>

        <label className="knowledge-switch">
          <span className={classNames('checkbox', form.useKnowledge && 'checked')}>{form.useKnowledge && <Check size={13} />}</span>
          <input type="checkbox" checked={form.useKnowledge} onChange={(event) => update('useKnowledge', event.target.checked)} />
          <BookOpenText size={18} />
          <span><strong>使用企业知识库生成岗位方案</strong><small>将检索历史 JD、岗位族标准、人才画像和招聘制度</small></span>
          <StatusPill tone="green">推荐</StatusPill>
        </label>

        <div className="modal-actions"><button type="button" className="btn secondary" onClick={onClose}>取消</button><button type="submit" className="btn primary"><Sparkles size={16} />创建并生成岗位方案</button></div>
      </form>
    </div>
  );
}

function Talent({ selectedCandidate, setSelectedCandidate, selectedCandidates, toggleCandidate, setView, notify, pushEvent }) {
  const selected = candidatesSeed.find((item) => item.id === selectedCandidate);
  function confirmSelection() {
    pushEvent('候选名单已确认', `人工确认 ${selectedCandidates.length} 位候选人进入在线面试`);
    notify('候选名单已确认');
    setView('interviews');
  }
  return (
    <>
      <PageHeader eyebrow="高级后端开发工程师 / 人才搜索" title="人才匹配" description="从 2,846 份有效简历中检索到 12 位推荐候选人"
        actions={<button className="btn primary" onClick={confirmSelection}><Send size={17} />确认名单并发起面试</button>} />
      <section className="match-summary">
        <div><span>评分卡</span><strong>高级后端开发工程师 v3.2</strong><button title="查看评分规则"><Eye size={15} /></button></div>
        <div><span>检索范围</span><strong>集团人才库 · 近 5 年</strong></div>
        <div><span>自动排除</span><strong>硬性条件不符 167 人</strong></div>
        <button className="text-button"><SlidersHorizontal size={16} />调整匹配策略</button>
      </section>
      <div className="talent-layout">
        <section className="candidate-list-panel">
          <div className="list-toolbar"><label className="search-field"><Search size={16} /><input placeholder="搜索候选人" /></label><button className="icon-button small" title="筛选"><ListFilter size={16} /></button></div>
          <div className="selection-note"><span>推荐候选人</span><strong>已选择 {selectedCandidates.length} / 12</strong></div>
          <div className="candidate-list">
            {candidatesSeed.map((person, index) => (
              <button className={classNames('candidate-row', selectedCandidate === person.id && 'selected')} key={person.id} onClick={() => setSelectedCandidate(person.id)}>
                <span className={classNames('checkbox', selectedCandidates.includes(person.id) && 'checked')} onClick={(event) => { event.stopPropagation(); toggleCandidate(person.id); }}>{selectedCandidates.includes(person.id) && <Check size={13} />}</span>
                <span className="rank">{String(index + 1).padStart(2, '0')}</span>
                <span className={`avatar avatar-${(index % 3) + 1}`}>{person.initials}</span>
                <span className="candidate-copy"><strong>{person.name}</strong><small>{person.title} · {person.years}</small></span>
                <span className={`score-orb ${person.tone}`}><strong>{person.score}</strong><small>分</small></span>
              </button>
            ))}
          </div>
        </section>
        <CandidateDetail person={selected} selected={selectedCandidates.includes(selected.id)} onToggle={() => toggleCandidate(selected.id)} />
      </div>
    </>
  );
}

function CandidateDetail({ person, selected, onToggle }) {
  return (
    <section className="candidate-detail">
      <div className="candidate-detail-head">
        <div className="person-identity"><span className="avatar avatar-large">{person.initials}</span><div><div className="name-line"><h2>{person.name}</h2><StatusPill tone={person.tone}>{person.status}</StatusPill></div><p>{person.title} · {person.company}</p></div></div>
        <div className="hero-score"><strong>{person.score}</strong><span>/ 100</span><small>综合匹配度</small></div>
      </div>
      <div className="profile-facts">
        <span><BriefcaseBusiness size={16} /><small>工作经验</small><strong>{person.years}</strong></span>
        <span><GraduationCap size={16} /><small>最高学历</small><strong>{person.education}</strong></span>
        <span><Building2 size={16} /><small>毕业院校</small><strong>{person.school}</strong></span>
        <span><MapPin size={16} /><small>意向地点</small><strong>北京</strong></span>
      </div>
      <div className="detail-section">
        <div className="detail-title"><h3>评分依据</h3><span><ShieldCheck size={14} />全部结论均有原文证据</span></div>
        <div className="evidence-list">
          {person.evidence.map((item) => (
            <div className="evidence-row" key={item.label}>
              <div className="evidence-score"><span>{item.label}</span><strong>{item.value}<small>/{item.max}</small></strong></div>
              <div className="score-track"><i style={{ width: `${(item.value / item.max) * 100}%` }} /></div>
              <blockquote>“{item.quote}” <button>查看简历原文</button></blockquote>
            </div>
          ))}
        </div>
      </div>
      <div className="fit-grid">
        <div><h3><CheckCircle2 size={16} />推荐依据</h3>{person.highlights.map((item) => <span key={item}>{item}</span>)}</div>
        <div><h3><CircleHelp size={16} />待核实项</h3>{person.risks.map((item) => <span key={item}>{item}</span>)}</div>
      </div>
      <div className="detail-section source-section">
        <div className="detail-title"><h3>画像对照来源</h3><button>查看全部来源 <ArrowRight size={14} /></button></div>
        <div className="source-row"><BookOpenText size={17} /><span><strong>研发岗位高绩效人才特征分析</strong><small>人才画像 · v1.8 · 引用 6 个特征</small></span><Eye size={15} /></div>
      </div>
      <div className="sticky-actions"><button className="btn secondary"><Download size={16} />下载简历</button><button className={classNames('btn', selected ? 'selected-button' : 'primary')} onClick={onToggle}>{selected ? <Check size={17} /> : <Plus size={17} />}{selected ? '已加入面试名单' : '加入面试名单'}</button></div>
    </section>
  );
}

function Interviews({ selectedCandidates, setView, notify, pushEvent }) {
  const interviewees = candidatesSeed.filter((item) => selectedCandidates.includes(item.id)).slice(0, 3);
  const [statuses, setStatuses] = useState(() => Object.fromEntries(interviewees.map((item, index) => [item.id, index === 0 ? '已完成' : '待作答'])));
  function collect(person) {
    setStatuses((value) => ({ ...value, [person.id]: '已完成' }));
    pushEvent('收到在线面试结果', `${person.name} 已完成面试，评价报告生成完毕`, 'success');
    notify(`${person.name}的面试结果已回收`);
  }
  return (
    <>
      <PageHeader eyebrow="高级后端开发工程师 / 在线面试" title="面试协同" description="智能体负责邀约、提醒、结果回收与结构化评价"
        actions={<button className="btn primary" onClick={() => setView('evaluation')}><BadgeCheck size={17} />查看综合评价</button>} />
      <div className="interview-overview">
        <div><span className="overview-icon blue"><Send size={19} /></span><span><small>已发送邀请</small><strong>{interviewees.length}</strong></span></div>
        <div><span className="overview-icon green"><CheckCircle2 size={19} /></span><span><small>已完成</small><strong>{Object.values(statuses).filter((s) => s === '已完成').length}</strong></span></div>
        <div><span className="overview-icon amber"><Clock3 size={19} /></span><span><small>待作答</small><strong>{Object.values(statuses).filter((s) => s === '待作答').length}</strong></span></div>
        <div><span className="overview-icon gray"><CalendarDays size={19} /></span><span><small>最晚完成</small><strong className="date-strong">07月25日</strong></span></div>
      </div>
      <section className="interview-list">
        <div className="panel-heading"><div><span className="section-kicker">在线面试批次</span><h2>研发岗位第一批次</h2></div><button className="btn secondary"><Send size={16} />批量提醒</button></div>
        {interviewees.map((person, index) => {
          const done = statuses[person.id] === '已完成';
          return (
            <article className="interview-card" key={person.id}>
              <div className="interview-person"><span className={`avatar avatar-${index + 1}`}>{person.initials}</span><span><strong>{person.name}</strong><small>{person.title}</small></span></div>
              <div className="interview-schedule"><span><CalendarDays size={15} />邀请时间</span><strong>2026-07-{22 - index} 14:30</strong></div>
              <div className="interview-schedule"><span><Clock3 size={15} />答题时长</span><strong>{done ? '26 分 18 秒' : '限时 40 分钟'}</strong></div>
              <div><StatusPill tone={done ? 'green' : 'amber'}>{done ? '评价已生成' : '等待候选人'}</StatusPill></div>
              <div className="interview-action">
                {done ? <button className="btn secondary" onClick={() => setView('evaluation')}><Eye size={16} />查看评价</button> : <button className="btn secondary" onClick={() => collect(person)}><Play size={16} />模拟完成</button>}
                <button className="icon-button small"><MoreHorizontal size={17} /></button>
              </div>
            </article>
          );
        })}
      </section>
      <section className="guardrail-band"><LockKeyhole size={20} /><div><strong>智能体执行边界</strong><p>邀请发送、状态提醒和结果回收可自动完成；面试评价仅作为决策参考，进入下一轮与最终录用必须由招聘负责人确认。</p></div><button className="text-button">查看规则</button></section>
    </>
  );
}

function Evaluation({ setView, notify, pushEvent }) {
  const person = candidatesSeed[0];
  const [confirmed, setConfirmed] = useState(false);
  function confirm() {
    setConfirmed(true);
    pushEvent('综合评价已确认', '招聘经理确认陈思远进入业务面试', 'human');
    notify('评价已确认并进入下一轮');
  }
  return (
    <>
      <PageHeader eyebrow="高级后端开发工程师 / 综合评价" title="候选人综合评价" description="汇总简历匹配、在线面试与人工评价，结论可解释、可复核"
        actions={<><button className="btn secondary"><Download size={16} />导出报告</button><button className="btn primary" onClick={confirm}>{confirmed ? <Check size={17} /> : <UserCheck size={17} />}{confirmed ? '已确认进入下一轮' : '确认进入下一轮'}</button></>} />
      <section className="evaluation-hero">
        <div className="eval-person"><span className="avatar avatar-large">{person.initials}</span><div><h2>{person.name}</h2><p>{person.title} · {person.company}</p><span>候选人编号 C2026-00428</span></div></div>
        <div className="final-score"><span>综合建议</span><strong>90<small>/100</small></strong><StatusPill tone="green">建议进入下一轮</StatusPill></div>
        <div className="eval-conclusion"><Sparkles size={19} /><p>技术能力与岗位要求高度匹配，复杂项目经验和量化成果证据充分。建议业务面试重点核实团队协作方式与技术管理意愿。</p></div>
      </section>
      <div className="evaluation-grid">
        <section className="panel score-breakdown">
          <div className="panel-heading"><div><span className="section-kicker">分项结果</span><h2>独立评分构成</h2></div><span className="evidence-badge"><ShieldCheck size={14} />证据覆盖率 96%</span></div>
          {[
            ['简历匹配', 92, '岗位评分卡 v3.2', '40%'],
            ['AI在线面试', 88, '结构化面试题库 v2.1', '35%'],
            ['能力测评', 89, '研发能力测评 v1.6', '25%'],
          ].map(([label, score, source, weight]) => (
            <div className="breakdown-row" key={label}><span className="breakdown-score">{score}</span><div><strong>{label}</strong><small>{source} · 权重 {weight}</small><i><b style={{ width: `${score}%` }} /></i></div><button><Eye size={16} />查看证据</button></div>
          ))}
        </section>
        <aside className="panel review-panel">
          <div className="panel-heading"><div><span className="section-kicker">风险与复核</span><h2>需要人工关注</h2></div></div>
          <div className="review-item amber"><CircleHelp size={18} /><div><strong>管理意愿尚未充分验证</strong><p>候选人具有带教经历，但未表达明确的团队管理意愿。</p><span>建议业务面试追问</span></div></div>
          <div className="review-item green"><CheckCircle2 size={18} /><div><strong>未发现硬性风险</strong><p>资质、履历一致性与基础合规检查均通过。</p></div></div>
        </aside>
      </div>
      <section className="interview-evidence">
        <div className="panel-heading"><div><span className="section-kicker">在线面试</span><h2>关键回答与评价证据</h2></div><button className="btn secondary"><Video size={16} />查看完整面试</button></div>
        <div className="qa-row"><span className="qa-index">01</span><div><strong>请介绍一次你主导的复杂系统改造，以及你的关键决策。</strong><blockquote>“订单中心在业务增长后出现明显瓶颈，我先用链路追踪确认主要问题集中在同步调用和热点数据……”</blockquote></div><span className="qa-score">92<small>表达与证据</small></span></div>
        <div className="qa-row"><span className="qa-index">02</span><div><strong>当技术方案与业务交付时间发生冲突时，你如何处理？</strong><blockquote>“我会先区分不可妥协的稳定性底线和可延后的优化项，再把风险转换成业务方能理解的影响……”</blockquote></div><span className="qa-score">87<small>判断与协作</small></span></div>
      </section>
    </>
  );
}

function Knowledge({ knowledge, setModalOpen }) {
  const [type, setType] = useState('全部资料');
  const [query, setQuery] = useState('');
  const tabs = ['全部资料', '岗位知识', '人才画像', '制度流程'];
  const filtered = useMemo(() => knowledge.filter((item) => (type === '全部资料' || item.type === type) && item.title.includes(query)), [knowledge, type, query]);
  const counts = {
    岗位知识: knowledge.filter((i) => i.type === '岗位知识').length,
    人才画像: knowledge.filter((i) => i.type === '人才画像').length,
    制度流程: knowledge.filter((i) => i.type === '制度流程').length,
  };
  return (
    <>
      <PageHeader eyebrow="智能体管理" title="企业招聘知识库" description="沉淀企业岗位标准、人才成功特征与招聘制度，为智能体提供可信依据"
        actions={<button className="btn primary" onClick={() => setModalOpen(true)}><Plus size={17} />新增知识</button>} />
      <section className="knowledge-overview">
        <div className="knowledge-stat"><span className="knowledge-icon role"><BriefcaseBusiness size={20} /></span><span><small>岗位知识</small><strong>{counts.岗位知识}<em> 份资料</em></strong><i>覆盖 18 个岗位族</i></span></div>
        <div className="knowledge-stat"><span className="knowledge-icon portrait"><UsersRound size={20} /></span><span><small>人才画像</small><strong>{counts.人才画像}<em> 份资料</em></strong><i>关联 326 条任职结果</i></span></div>
        <div className="knowledge-stat"><span className="knowledge-icon policy"><ShieldCheck size={20} /></span><span><small>制度流程</small><strong>{counts.制度流程}<em> 份资料</em></strong><i>7 项规则正在生效</i></span></div>
        <div className="knowledge-health"><span><BadgeCheck size={19} />知识健康度</span><strong>94%</strong><i><b style={{ width: '94%' }} /></i><small>2 份资料建议复核</small></div>
      </section>
      <section className="knowledge-workspace">
        <div className="knowledge-tabs" role="tablist">
          {tabs.map((tab) => <button role="tab" aria-selected={type === tab} className={type === tab ? 'active' : ''} onClick={() => setType(tab)} key={tab}>{tab}<span>{tab === '全部资料' ? knowledge.length : counts[tab]}</span></button>)}
        </div>
        <div className="knowledge-toolbar"><label className="search-field"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索知识标题、部门或标签" /></label><button className="btn secondary"><ListFilter size={16} />状态<ChevronDown size={14} /></button><button className="btn secondary"><History size={16} />最近更新</button></div>
        <div className="data-table knowledge-table">
          <div className="table-row table-head"><span>资料名称</span><span>知识分类</span><span>版本与状态</span><span>维护部门</span><span>智能体引用</span><span>更新时间</span><span /></div>
          {filtered.map((item) => (
            <button className="table-row" key={item.id}>
              <span className="file-cell"><em className={`file-icon ${item.format.toLowerCase()}`}><FileText size={18} /></em><span><strong>{item.title}</strong><small>{item.format} · 已完成内容解析</small></span></span>
              <span><StatusPill tone={item.type === '岗位知识' ? 'blue' : item.type === '人才画像' ? 'green' : 'gray'}>{item.type}</StatusPill></span>
              <span><strong>{item.version}</strong><small className={item.status === '可用' ? 'success-text' : 'warning-text'}>{item.status === '可用' ? '●' : '▲'} {item.status}</small></span>
              <span>{item.owner}</span>
              <span><strong>{item.refs}</strong><small>近30天</small></span>
              <span>{item.updated}</span>
              <span><MoreHorizontal size={17} /></span>
            </button>
          ))}
          {!filtered.length && <div className="empty-state"><Search size={22} /><strong>未找到相关资料</strong><span>请调整搜索词或知识分类</span></div>}
        </div>
      </section>
      <section className="governance-note"><ShieldCheck size={20} /><div><strong>知识治理规则已启用</strong><p>上传资料将经过敏感信息识别、重复内容检测与人工复核；人才画像不会使用年龄、性别、婚育等敏感属性。</p></div><button className="text-button">查看治理规则 <ArrowRight size={15} /></button></section>
    </>
  );
}

function KnowledgeModal({ onClose, onSubmit }) {
  const [form, setForm] = useState({ type: '岗位知识', title: '', owner: '组织人事部', format: 'DOCX' });
  function submit(event) {
    event.preventDefault();
    onSubmit(form);
  }
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <form className="modal" onSubmit={submit} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header"><div><span className="section-kicker">知识维护</span><h2>新增企业知识</h2></div><button type="button" className="icon-button" onClick={onClose}><X size={18} /></button></div>
        <label><span>知识分类</span><select value={form.type} onChange={(e) => setForm({ ...form, type: e.target.value })}><option>岗位知识</option><option>人才画像</option><option>制度流程</option></select></label>
        <label><span>资料名称</span><input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="例如：研发岗位任职资格标准" /></label>
        <label><span>维护部门</span><select value={form.owner} onChange={(e) => setForm({ ...form, owner: e.target.value })}><option>组织人事部</option><option>招聘中心</option><option>人才发展中心</option><option>数字科技部</option><option>法律合规部</option></select></label>
        <label><span>知识文件</span><div className="upload-zone"><UploadCloud size={25} /><strong>选择文件或拖放到此处</strong><small>支持 PDF、Word、Excel、PPT，单个文件不超过 50MB</small><input type="file" onChange={(e) => { const name = e.target.files?.[0]?.name; if (name) setForm({ ...form, title: form.title || name.replace(/\.[^.]+$/, ''), format: name.split('.').pop().toUpperCase() }); }} /></div></label>
        <div className="modal-note"><ShieldCheck size={17} /><span>资料上传后不会立即用于智能体决策，需完成解析与人工复核。</span></div>
        <div className="modal-actions"><button type="button" className="btn secondary" onClick={onClose}>取消</button><button type="submit" className="btn primary"><UploadCloud size={16} />上传并解析</button></div>
      </form>
    </div>
  );
}

function Audit({ events }) {
  return (
    <>
      <PageHeader eyebrow="智能体管理" title="运行审计" description="查看智能体的执行步骤、知识引用、人工决策和系统写入记录"
        actions={<button className="btn secondary"><Download size={16} />导出审计日志</button>} />
      <section className="audit-summary">
        <div><Bot size={20} /><span><small>智能体执行</small><strong>38 次</strong></span></div>
        <div><UserCheck size={20} /><span><small>人工确认</small><strong>6 次</strong></span></div>
        <div><BookOpenText size={20} /><span><small>知识引用</small><strong>27 次</strong></span></div>
        <div><ShieldCheck size={20} /><span><small>规则拦截</small><strong>0 次</strong></span></div>
      </section>
      <section className="audit-workspace">
        <div className="knowledge-toolbar"><label className="search-field"><Search size={16} /><input placeholder="搜索动作、操作者或任务" /></label><button className="btn secondary"><CalendarDays size={16} />今天<ChevronDown size={14} /></button><button className="btn secondary"><ListFilter size={16} />全部类型<ChevronDown size={14} /></button></div>
        <div className="audit-context"><div><span className="agent-orbit small"><Bot size={19} /></span><span><strong>高级后端开发工程师招聘</strong><small>任务 R2026-0718 · 招聘执行智能体</small></span></div><StatusPill tone="blue">执行中</StatusPill></div>
        <div className="audit-timeline">
          {events.map((event, index) => (
            <div className="audit-event" key={`${event.time}-${index}`}>
              <time>今天<br /><strong>{event.time}</strong></time>
              <span className={classNames('audit-dot', event.type)}>{event.type === 'human' ? <UserCheck size={14} /> : <Check size={14} />}</span>
              <div><div className="audit-event-title"><strong>{event.title}</strong><StatusPill tone={event.type === 'human' ? 'blue' : 'green'}>{event.type === 'human' ? '人工操作' : '自动执行'}</StatusPill></div><p>{event.detail}</p><div className="audit-meta"><span><Bot size={13} />招聘执行智能体</span><span><ShieldCheck size={13} />策略校验通过</span><button><Eye size={14} />查看输入输出</button></div></div>
            </div>
          ))}
        </div>
      </section>
    </>
  );
}

export default App;
