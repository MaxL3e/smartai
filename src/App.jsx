import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Activity,
  AlertTriangle,
  Archive,
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
  ChevronRight,
  Circle,
  CircleHelp,
  Clock3,
  Download,
  Edit3,
  Eye,
  FileText,
  GraduationCap,
  History,
  LayoutDashboard,
  ListFilter,
  LockKeyhole,
  MapPin,
  MoreHorizontal,
  RefreshCw,
  Play,
  Plus,
  Save,
  Search,
  Send,
  Settings2,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  UploadCloud,
  UserCheck,
  UserRound,
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
  { title: '岗位方案', completedNote: '已确认', activeNote: '等待人工确认', pendingNote: '等待生成' },
  { title: '人才搜索', completedNote: '匹配完成', activeNote: '智能体检索中', pendingNote: '未开始' },
  { title: '名单确认', completedNote: '名单已确认', activeNote: '等待人工确认', pendingNote: '未开始' },
  { title: '在线面试', completedNote: '结果已回收', activeNote: '面试进行中', pendingNote: '未开始' },
  { title: '综合评价', completedNote: '评价已确认', activeNote: '等待人工确认', pendingNote: '未开始' },
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
  { id: 6, name: '赵铭泽', initials: '赵', title: '高级Java工程师', company: '瀚海数科', years: '6年', education: '本科', school: '北京工业大学', score: 76, status: '待确认', tone: 'amber', highlights: ['微服务开发', '性能优化'], risks: ['大型项目证据不足'], evidence: [{ label: '核心技术能力', value: 24, max: 30, quote: '负责会员中心微服务拆分与接口治理。' }, { label: '项目复杂度', value: 21, max: 30, quote: '承担核心模块研发，整体架构参与度有限。' }, { label: '行业与业务', value: 16, max: 20, quote: '具备企业服务业务经验。' }, { label: '成果证据', value: 15, max: 20, quote: '接口平均耗时下降28%。' }] },
  { id: 7, name: '许若涵', initials: '许', title: '研发工程师', company: '中科云图', years: '5年', education: '硕士', school: '东南大学', score: 74, status: '待确认', tone: 'amber', highlights: ['算法工程化', '数据服务'], risks: ['后端技术栈覆盖一般'], evidence: [{ label: '核心技术能力', value: 22, max: 30, quote: '完成算法服务工程化和服务接口封装。' }, { label: '项目复杂度', value: 22, max: 30, quote: '参与跨团队数据服务平台建设。' }, { label: '行业与业务', value: 16, max: 20, quote: '有政企数字化交付经验。' }, { label: '成果证据', value: 14, max: 20, quote: '模型服务部署效率提升35%。' }] },
  { id: 8, name: '唐子墨', initials: '唐', title: '后端开发工程师', company: '凌云软件', years: '5年', education: '本科', school: '重庆大学', score: 69, status: '谨慎考虑', tone: 'gray', highlights: ['业务开发', '交付稳定'], risks: ['低于当前推荐阈值'], evidence: [{ label: '核心技术能力', value: 22, max: 30, quote: '负责多个企业应用后端模块开发。' }, { label: '项目复杂度', value: 18, max: 30, quote: '项目技术复杂度相对有限。' }, { label: '行业与业务', value: 15, max: 20, quote: '熟悉常规企业管理业务。' }, { label: '成果证据', value: 14, max: 20, quote: '负责模块按期交付，线上运行稳定。' }] },
  { id: 9, name: '顾明轩', initials: '顾', title: 'Java开发工程师', company: '启明科技', years: '4年', education: '硕士', school: '大连理工大学', score: 67, status: '谨慎考虑', tone: 'gray', highlights: ['学习能力', '技术基础'], risks: ['工作年限不足', '主导经验不足'], evidence: [{ label: '核心技术能力', value: 22, max: 30, quote: '熟悉Java常用开发框架。' }, { label: '项目复杂度', value: 17, max: 30, quote: '以功能模块研发为主。' }, { label: '行业与业务', value: 15, max: 20, quote: '具有企业软件开发经验。' }, { label: '成果证据', value: 13, max: 20, quote: '缺少可量化成果说明。' }] },
  { id: 10, name: '宋安然', initials: '宋', title: '平台开发工程师', company: '恒星信息', years: '6年', education: '本科', school: '合肥工业大学', score: 73, status: '待确认', tone: 'amber', highlights: ['中台建设', '组件封装'], risks: ['业务理解证据一般'], evidence: [{ label: '核心技术能力', value: 24, max: 30, quote: '建设统一权限与消息组件。' }, { label: '项目复杂度', value: 22, max: 30, quote: '平台覆盖公司多个业务系统。' }, { label: '行业与业务', value: 13, max: 20, quote: '业务领域经历相对分散。' }, { label: '成果证据', value: 14, max: 20, quote: '减少重复开发约20%。' }] },
  { id: 11, name: '蒋晨曦', initials: '蒋', title: '资深研发工程师', company: '原点网络', years: '9年', education: '本科', school: '武汉理工大学', score: 82, status: '推荐', tone: 'blue', highlights: ['技术治理', '复杂系统迁移'], risks: ['近期行业跨度较大'], evidence: [{ label: '核心技术能力', value: 27, max: 30, quote: '主导遗留系统服务化迁移与技术债治理。' }, { label: '项目复杂度', value: 26, max: 30, quote: '系统迁移涉及7条业务线。' }, { label: '行业与业务', value: 13, max: 20, quote: '最近项目行业关联度一般。' }, { label: '成果证据', value: 16, max: 20, quote: '发布频率提升2倍，回滚率下降60%。' }] },
  { id: 12, name: '沈知行', initials: '沈', title: 'Java技术专家', company: '天工智联', years: '10年', education: '硕士', school: '浙江大学', score: 86, status: '推荐', tone: 'blue', highlights: ['架构治理', '高可用体系', '团队带教'], risks: ['薪资期望待确认'], evidence: [{ label: '核心技术能力', value: 29, max: 30, quote: '负责核心交易平台架构演进与高可用建设。' }, { label: '项目复杂度', value: 28, max: 30, quote: '平台日均处理亿级业务事件。' }, { label: '行业与业务', value: 14, max: 20, quote: '行业背景不同但复杂度高度匹配。' }, { label: '成果证据', value: 15, max: 20, quote: '核心系统可用性提升至99.99%。' }] },
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
  { id: 1, taskId: 'R2026-0718', date: '2026-07-22', time: '14:32:16', title: '完成人才库检索', detail: '在 2,846 份简历中筛选出 12 位候选人', type: 'success', actor: '招聘执行智能体', input: '岗位画像 v3.2、集团人才库', output: '推荐候选人 12 位，自动排除 167 位' },
  { id: 2, taskId: 'R2026-0718', date: '2026-07-22', time: '14:31:48', title: '应用岗位评分卡', detail: '使用“高级后端开发工程师评分卡 v3.2”', type: 'success', actor: '招聘执行智能体', input: '评分卡 v3.2', output: '四维评分规则校验通过' },
  { id: 3, taskId: 'R2026-0718', date: '2026-07-22', time: '14:31:22', title: '读取知识库资料', detail: '引用 4 份岗位知识与 2 份人才画像', type: 'success', actor: '招聘执行智能体', input: '知识检索：后端研发、岗位画像', output: '命中 6 份有效资料' },
  { id: 4, taskId: 'R2026-0718', date: '2026-07-22', time: '14:30:59', title: '人工确认岗位方案', detail: '招聘经理李佳确认 JD 与推荐标准', type: 'human', actor: '李佳', input: '岗位方案草稿 v3.1', output: '发布岗位方案 v3.2' },
];

const initialNotifications = [
  { id: 1, title: '候选名单待确认', detail: '高级后端开发工程师已有 12 位推荐候选人', time: '8分钟前', read: false, target: 'talent' },
  { id: 2, title: '在线面试已完成', detail: '陈思远的在线面试评价已生成', time: '36分钟前', read: false, target: 'evaluation' },
  { id: 3, title: '知识资料建议复核', detail: '2024-2025研发招聘复盘存在 2 项时效性提示', time: '昨天', read: true, target: 'knowledge' },
];

const initialTasks = [
  { code: 'R2026-0718', role: '高级后端开发工程师', dept: '数字科技部', city: '北京', count: '2人', headcount: 2, stage: '名单确认', progress: 48, owner: '李佳', due: '08-15', tone: 'blue', recruitmentType: '社会招聘', priority: '高', requirement: '负责集团级数字化平台核心服务的架构设计与研发。' },
  { code: 'R2026-0712', role: '财务共享中心经理', dept: '财务管理部', city: '上海', count: '1人', headcount: 1, stage: '在线面试', progress: 66, owner: '王楠', due: '08-08', tone: 'amber', recruitmentType: '社会招聘', priority: '中', requirement: '负责财务共享中心运营管理与流程优化。' },
  { code: 'R2026-0709', role: '能源市场分析师', dept: '战略发展部', city: '北京', count: '3人', headcount: 3, stage: '人才搜索', progress: 31, owner: '张晨', due: '08-20', tone: 'green', recruitmentType: '校园招聘', priority: '中', requirement: '跟踪能源市场趋势并形成经营分析建议。' },
  { code: 'R2026-0626', role: '合规风控主管', dept: '法律合规部', city: '深圳', count: '1人', headcount: 1, stage: '综合评价', progress: 86, owner: '陈敏', due: '07-30', tone: 'gray', recruitmentType: '社会招聘', priority: '高', requirement: '建立业务合规审查和风险预警机制。' },
];

const initialCandidateSelections = {
  'R2026-0718': [1, 2],
  'R2026-0712': [1, 3],
  'R2026-0626': [2, 3],
};

const initialInterviewStatuses = {
  'R2026-0718': { 1: '已完成', 2: '待作答' },
  'R2026-0712': { 1: '已完成', 3: '待作答' },
  'R2026-0626': { 2: '已完成', 3: '已完成' },
};

function loadStored(key, fallback) {
  try {
    const stored = window.localStorage.getItem(key);
    return stored ? JSON.parse(stored) : fallback;
  } catch {
    return fallback;
  }
}

function downloadText(filename, content, type = 'text/plain;charset=utf-8') {
  const blob = new Blob([content], { type });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

function localDateString(date = new Date()) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
}

function addDays(date, days) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function inferTaskFromPrompt(prompt, previous = {}) {
  const departments = ['数字科技部', '财务管理部', '战略发展部', '法律合规部', '组织人事部', '人才发展中心', '招聘中心'];
  const cities = ['北京', '上海', '深圳', '广州', '杭州', '南京', '成都', '武汉', '西安', '天津', '重庆', '苏州'];
  const numberMap = { 一: 1, 两: 2, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 };
  const normalizedPrompt = prompt.trim().replace(/[。！!？?]+$/, '');
  const countMatch = prompt.match(/([1-9]\d*|[一两二三四五六七八九十])\s*(?:名|位|人)/);
  const actionTail = prompt.match(/(?:紧急招聘|招聘|招募|寻找|拟招|急招|招)\s*([^，。；,;]{0,32})/)?.[1]?.trim() || '';
  const actionRole = actionTail
    .replace(/^(?:[1-9]\d*|[一两二三四五六七八九十])\s*(?:名|位|人|个)?\s*/, '')
    .replace(/\s*(?:[1-9]\d*|[一两二三四五六七八九十])\s*(?:名|位|人)\s*$/, '')
    .trim();
  const validActionRole = actionRole && !/^(希望|要求|计划|预计|完成|到岗|在|于)/.test(actionRole) ? actionRole : '';
  const explicitRole = prompt.match(/(?:岗位(?:名称)?(?:是|为|：|:))\s*([^，。；,;]{2,24})/);
  const conciseRole = normalizedPrompt.match(/^([^，。；,;\s]{2,18}(?:经理|主管|专员|专家|工程师|分析师|顾问|总监|负责人|设计师|架构师|研究员|技术员|销售|客服|会计|法务|审计|运营))(?:\s|，|。|；|,|;|$)/);
  const roleCandidate = explicitRole?.[1] || validActionRole || conciseRole?.[1];
  const role = (roleCandidate || previous.role || '待确认岗位').trim().replace(/^(在|一名|一位|一个)/, '').replace(/岗位$/, '') || '待确认岗位';
  const detectedDepartment = departments.find((item) => prompt.includes(item)) || prompt.match(/([\u4e00-\u9fa5A-Za-z0-9]{2,12}(?:事业部|分公司|研究院|中心|部门|部))/)?.[1];
  const detectedCity = cities.find((item) => prompt.includes(item));
  const department = detectedDepartment || previous.dept || '组织人事部';
  const city = detectedCity || previous.city || '北京';
  const headcount = countMatch ? (Number(countMatch[1]) || numberMap[countMatch[1]] || 1) : previous.headcount || 1;
  const recruitmentTypeMatch = /校招|校园招聘|应届/.test(prompt) ? '校园招聘' : /内招|内部竞聘|内部招聘/.test(prompt) ? '内部竞聘' : /社招|社会招聘/.test(prompt) ? '社会招聘' : '';
  const recruitmentType = recruitmentTypeMatch || previous.recruitmentType || '社会招聘';
  const priorityMatch = /紧急|尽快|急招|高优/.test(prompt) ? '高' : /低优|不着急/.test(prompt) ? '低' : /普通|常规|中优/.test(prompt) ? '中' : '';
  const priority = priorityMatch || previous.priority || '中';
  const now = new Date();
  let due = previous.due || localDateString(addDays(now, 30));
  const fullDate = prompt.match(/(20\d{2})[年\-/\.](\d{1,2})[月\-/\.](\d{1,2})日?/);
  const monthDay = prompt.match(/(\d{1,2})月(\d{1,2})日/);
  const monthEnd = prompt.match(/(\d{1,2})月底/);
  if (fullDate) due = `${fullDate[1]}-${String(fullDate[2]).padStart(2, '0')}-${String(fullDate[3]).padStart(2, '0')}`;
  else if (monthDay) {
    const year = Number(monthDay[1]) < now.getMonth() + 1 ? now.getFullYear() + 1 : now.getFullYear();
    due = `${year}-${String(monthDay[1]).padStart(2, '0')}-${String(monthDay[2]).padStart(2, '0')}`;
  } else if (monthEnd) {
    const month = Number(monthEnd[1]);
    const year = month < now.getMonth() + 1 ? now.getFullYear() + 1 : now.getFullYear();
    due = localDateString(new Date(year, month, 0));
  } else if (/两周|14天/.test(prompt)) due = localDateString(addDays(now, 14));
  else if (/一周|7天/.test(prompt)) due = localDateString(addDays(now, 7));
  else if (/下个月/.test(prompt)) due = localDateString(addDays(now, 30));
  const confirmedFields = {
    ...(previous.confirmedFields || {}),
    role: Boolean(roleCandidate) || previous.confirmedFields?.role || false,
    dept: Boolean(detectedDepartment) || previous.confirmedFields?.dept || false,
    city: Boolean(detectedCity) || previous.confirmedFields?.city || false,
    headcount: Boolean(countMatch) || previous.confirmedFields?.headcount || false,
    recruitmentType: Boolean(recruitmentTypeMatch) || previous.confirmedFields?.recruitmentType || false,
    priority: Boolean(priorityMatch) || previous.confirmedFields?.priority || false,
    due: Boolean(fullDate || monthDay || monthEnd || /两周|14天|一周|7天|下个月/.test(prompt)) || previous.confirmedFields?.due || false,
  };
  return {
    role,
    dept: department,
    city,
    headcount,
    recruitmentType,
    priority,
    due,
    requirement: [previous.requirement, prompt].filter(Boolean).join('；').slice(-500),
    useKnowledge: !/不使用知识库|不用知识库/.test(prompt),
    confirmedFields,
  };
}

function stageIndex(stage) {
  return { 岗位方案: 0, 人才搜索: 1, 名单确认: 2, 在线面试: 3, 综合评价: 4, 已完成: 5 }[stage] ?? 0;
}

function nextStage(stage) {
  return { 岗位方案: '人才搜索', 人才搜索: '名单确认', 名单确认: '在线面试', 在线面试: '综合评价', 综合评价: '已完成' }[stage] || stage;
}

const defaultPlanThresholds = { strong: 88, recommended: 80, review: 70 };

function resolveRolePlan(task, plan) {
  return {
    ...plan,
    duties: task?.planDuties || plan.duties,
    requirements: task?.planRequirements || plan.requirements,
    tags: task?.planTags || plan.tags,
    scoreRules: task?.planScoreRules || plan.scoreRules,
    thresholds: task?.planThresholds || defaultPlanThresholds,
  };
}

function getRolePlan(task) {
  const role = task?.role || '';
  if (role.includes('数据')) {
    return resolveRolePlan(task, {
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
    });
  }
  if (role.includes('后端') || role.includes('Java')) {
    return resolveRolePlan(task, {
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
    });
  }
  return resolveRolePlan(task, {
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
  });
}

function getCandidates(task) {
  const role = task?.role || '';
  const plan = getRolePlan(task);
  const isTechnicalRole = role.includes('后端') || role.includes('Java');
  const governanceTitles = ['数据治理专家', '数据标准工程师', '主数据管理顾问', '数据质量工程师', '数据资产运营经理', '数据管理工程师', '元数据管理顾问', '数据平台工程师', '数据治理分析师', '数据产品经理', '数据架构师', '数据管理专家'];
  const governanceHighlights = [
    ['数据标准体系', '主数据治理', '大型集团项目'],
    ['数据质量管理', '标准落地', '跨部门协同'],
    ['主数据平台', '治理制度', '业务流程梳理'],
    ['质量监控', '问题闭环', '指标体系'],
  ];
  return candidatesSeed.map((person, index) => {
    const isGovernance = role.includes('数据');
    const title = isTechnicalRole ? person.title : isGovernance ? governanceTitles[index] : index < 4 ? role : `${role}相关岗位`;
    const highlights = isTechnicalRole ? person.highlights : isGovernance ? governanceHighlights[index % governanceHighlights.length] : [`${role}相关经验`, '大型企业项目', index % 2 ? '跨部门协同' : '复杂项目落地'];
    const fallbackQuotes = [
        `在${person.company}负责岗位核心体系建设，形成了可复用的工作标准。`,
        `牵头跨部门复杂项目落地，覆盖多个业务单元并建立持续运营机制。`,
        `能够结合大型企业业务场景识别关键问题，并协调业务与技术团队推进。`,
        `项目关键指标提升${20 + index * 2}%，相关成果已在组织内推广应用。`,
    ];
    const evidence = plan.scoreRules.map((rule, ruleIndex) => {
      const existing = person.evidence.find((item) => item.label === rule.label) || person.evidence[ruleIndex];
      return {
        label: rule.label,
        value: Math.round(person.score * rule.weight / 100),
        max: rule.weight,
        quote: existing?.quote || fallbackQuotes[ruleIndex % fallbackQuotes.length],
      };
    });
    return { ...person, title, highlights, risks: [index % 3 === 0 ? '团队管理范围待核实' : index % 3 === 1 ? '行业迁移经验待核实' : '成果口径需进一步确认'], evidence };
  });
}

function recommendationForScore(score, thresholds = defaultPlanThresholds) {
  if (score >= thresholds.strong) return { status: '强烈推荐', tone: 'green' };
  if (score >= thresholds.recommended) return { status: '推荐', tone: 'blue' };
  if (score >= thresholds.review) return { status: '待确认', tone: 'amber' };
  return { status: '谨慎评估', tone: 'gray' };
}

function applyMatchStrategy(person, strategy, thresholds) {
  const score = person.evidence.reduce((total, item) => {
    const weight = strategy?.[item.label] ?? item.max;
    return total + (item.value / item.max) * weight;
  }, 0);
  const roundedScore = Math.round(score);
  return { ...person, score: roundedScore, ...recommendationForScore(roundedScore, thresholds) };
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
  const [selectedCandidate, setSelectedCandidate] = useState(() => loadStored('smartai.selectedCandidate', 1));
  const [knowledge, setKnowledge] = useState(() => loadStored('smartai.knowledge', knowledgeSeed));
  const [events, setEvents] = useState(() => loadStored('smartai.events', initialEvents));
  const [tasks, setTasks] = useState(() => loadStored('smartai.recruitmentTasks', initialTasks));
  const [activeTaskId, setActiveTaskId] = useState(() => loadStored('smartai.activeTaskId', initialTasks[0].code));
  const [candidateSelections, setCandidateSelections] = useState(() => {
    const stored = loadStored('smartai.selectedCandidates', {});
    const normalized = Array.isArray(stored) ? { [initialTasks[0].code]: stored } : stored;
    return { ...initialCandidateSelections, ...normalized };
  });
  const [interviewStatusByTask, setInterviewStatusByTask] = useState(() => {
    const stored = loadStored('smartai.interviewStatuses', {});
    const legacy = stored && Object.values(stored).some((value) => typeof value === 'string');
    const normalized = legacy ? { [initialTasks[0].code]: stored } : stored;
    return { ...initialInterviewStatuses, ...normalized };
  });
  const [evaluationDecisions, setEvaluationDecisions] = useState(() => loadStored('smartai.evaluationDecisions', {}));
  const [matchStrategy, setMatchStrategy] = useState(() => loadStored('smartai.matchStrategy', { minScore: 70, sort: 'score', 核心技术能力: 30, 治理专业能力: 30, 核心专业能力: 30, 项目复杂度: 30, 行业与业务: 20, 业务与行业: 20, 成果证据: 20 }));
  const [notifications, setNotifications] = useState(() => loadStored('smartai.notifications', initialNotifications));
  const [toast, setToast] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [dialog, setDialog] = useState(null);
  const [globalSearchOpen, setGlobalSearchOpen] = useState(false);
  const [notificationOpen, setNotificationOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const activeTask = tasks.find((item) => item.code === activeTaskId) || tasks[0];
  const candidatePool = useMemo(() => {
    const plan = getRolePlan(activeTask);
    return getCandidates(activeTask).map((person) => applyMatchStrategy(person, matchStrategy, plan.thresholds));
  }, [activeTask, matchStrategy]);
  const candidate = candidatePool.find((item) => item.id === selectedCandidate) || candidatePool[0];
  const selectedCandidates = candidateSelections[activeTask?.code] || [];
  const interviewStatuses = interviewStatusByTask[activeTask?.code] || {};
  const flowStep = stageIndex(activeTask?.stage);

  function setSelectedCandidates(update) {
    setCandidateSelections((all) => {
      const current = all[activeTask.code] || [];
      const next = typeof update === 'function' ? update(current) : update;
      return { ...all, [activeTask.code]: next };
    });
  }

  function setInterviewStatuses(update) {
    setInterviewStatusByTask((all) => {
      const current = all[activeTask.code] || {};
      const next = typeof update === 'function' ? update(current) : update;
      return { ...all, [activeTask.code]: next };
    });
  }

  useEffect(() => {
    window.localStorage.setItem('smartai.recruitmentTasks', JSON.stringify(tasks));
  }, [tasks]);
  useEffect(() => window.localStorage.setItem('smartai.selectedCandidates', JSON.stringify(candidateSelections)), [candidateSelections]);
  useEffect(() => window.localStorage.setItem('smartai.selectedCandidate', JSON.stringify(selectedCandidate)), [selectedCandidate]);
  useEffect(() => window.localStorage.setItem('smartai.knowledge', JSON.stringify(knowledge)), [knowledge]);
  useEffect(() => window.localStorage.setItem('smartai.events', JSON.stringify(events)), [events]);
  useEffect(() => window.localStorage.setItem('smartai.activeTaskId', JSON.stringify(activeTaskId)), [activeTaskId]);
  useEffect(() => window.localStorage.setItem('smartai.interviewStatuses', JSON.stringify(interviewStatusByTask)), [interviewStatusByTask]);
  useEffect(() => window.localStorage.setItem('smartai.evaluationDecisions', JSON.stringify(evaluationDecisions)), [evaluationDecisions]);
  useEffect(() => window.localStorage.setItem('smartai.matchStrategy', JSON.stringify(matchStrategy)), [matchStrategy]);
  useEffect(() => window.localStorage.setItem('smartai.notifications', JSON.stringify(notifications)), [notifications]);
  useEffect(() => {
    function onKeyDown(event) {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault();
        setGlobalSearchOpen(true);
      }
      if (event.key === 'Escape') {
        setDialog(null);
        setGlobalSearchOpen(false);
        setNotificationOpen(false);
        setProfileOpen(false);
        setModalOpen(false);
        setTaskModalOpen(false);
      }
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  function notify(message) {
    setToast(message);
    window.clearTimeout(window.__smartToast);
    window.__smartToast = window.setTimeout(() => setToast(''), 2600);
  }

  function pushEvent(title, detail, type = 'human', taskId = activeTask?.code) {
    const now = new Date();
    setEvents((items) => [{ id: Date.now(), taskId, date: localDateString(now), time: now.toLocaleTimeString('zh-CN', { hour12: false }), title, detail, type, actor: type === 'human' ? '李佳' : '招聘执行智能体', input: `任务 ${taskId || '-'}`, output: detail }, ...items]);
  }

  function updateActiveTask(changes) {
    setTasks((items) => items.map((task) => task.code === activeTaskId ? { ...task, ...changes } : task));
  }

  function advanceFlow() {
    if (activeTask.stage === '已完成') {
      setView('evaluation');
      notify('该招聘任务已完成');
      return;
    }
    const target = nextStage(activeTask.stage);
    const progressMap = { 人才搜索: 30, 名单确认: 48, 在线面试: 66, 综合评价: 86, 已完成: 100 };
    updateActiveTask({ stage: target, progress: progressMap[target], tone: target === '在线面试' ? 'amber' : target === '已完成' ? 'green' : 'blue' });
    const messages = {
      人才搜索: ['岗位方案已确认', '智能体开始检索集团人才库'],
      名单确认: ['人才搜索已完成', '已生成候选人匹配排序，等待人工确认'],
      在线面试: ['候选名单已确认', `已向 ${selectedCandidates.length} 位候选人发起在线面试`],
      综合评价: ['面试结果已汇总', '已生成候选人综合评价报告'],
      已完成: ['招聘评价已归档', '任务已完成并进入结果回流阶段'],
    };
    const [title, detail] = messages[target];
    pushEvent(title, detail, target === '名单确认' ? 'success' : 'human');
    notify(title);
    if (target === '人才搜索' || target === '名单确认') setView('talent');
    if (target === '在线面试') setView('interviews');
    if (target === '综合评价' || target === '已完成') setView('evaluation');
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
        updated: localDateString(),
        status: item.status || '待复核',
        refs: 0,
        version: 'v1.0',
        archived: false,
        description: item.description || '等待维护人员补充资料说明。',
        tags: item.tags ? item.tags.split(/[，,]/).map((tag) => tag.trim()).filter(Boolean) : [],
      },
      ...items,
    ]);
    setModalOpen(false);
    pushEvent('知识资料已上传', item.title || '未命名知识资料进入解析队列');
    notify('资料已进入解析队列');
  }

  function updateKnowledge(id, changes) {
    setKnowledge((items) => items.map((item) => item.id === id ? { ...item, ...changes } : item));
  }

  function removeKnowledge(id) {
    const item = knowledge.find((entry) => entry.id === id);
    setKnowledge((items) => items.map((entry) => entry.id === id ? { ...entry, archived: true } : entry));
    pushEvent('知识资料已归档', item?.title || '知识资料', 'human');
    setDialog(null);
    notify('知识资料已归档');
  }

  function restoreKnowledge(id) {
    const item = knowledge.find((entry) => entry.id === id);
    setKnowledge((items) => items.map((entry) => entry.id === id ? { ...entry, archived: false } : entry));
    pushEvent('恢复知识资料', item?.title || '知识资料', 'human');
    setDialog(null);
    notify('知识资料已恢复');
  }

  function createTask(form) {
    const now = new Date();
    const datePart = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
    const prefix = `R${datePart}-`;
    const sequence = String(Math.max(0, ...tasks.filter((task) => task.code.startsWith(prefix)).map((task) => Number(task.code.slice(prefix.length)) || 0)) + 1).padStart(2, '0');
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
      useKnowledge: form.useKnowledge,
    };
    setTasks((items) => [task, ...items]);
    setCandidateSelections((items) => ({ ...items, [code]: [] }));
    setInterviewStatusByTask((items) => ({ ...items, [code]: {} }));
    setActiveTaskId(code);
    setTaskModalOpen(false);
    setView('roleplan');
    pushEvent('创建招聘任务', `${task.role} · ${task.dept} · 招聘 ${task.count}`, 'human', code);
    setNotifications((items) => [{ id: Date.now(), title: '岗位方案待确认', detail: `${task.role}的智能体建议稿已生成`, time: '刚刚', read: false, target: 'roleplan' }, ...items]);
    notify(`招聘任务 ${code} 已创建`);
  }

  function updateTask(code, changes) {
    setTasks((items) => items.map((task) => task.code === code ? { ...task, ...changes } : task));
    pushEvent('更新招聘任务', `${code} 的任务信息已更新`, 'human', code);
    notify('招聘任务已更新');
  }

  function archiveTask(code) {
    setTasks((items) => items.map((task) => task.code === code ? { ...task, archived: true } : task));
    if (activeTaskId === code) {
      const nextTask = tasks.find((task) => task.code !== code && !task.archived);
      if (nextTask) setActiveTaskId(nextTask.code);
    }
    pushEvent('归档招聘任务', `任务 ${code} 已移入归档`, 'human', code);
    setDialog(null);
    setView('tasks');
    notify('招聘任务已归档');
  }

  function restoreTask(code) {
    setTasks((items) => items.map((task) => task.code === code ? { ...task, archived: false } : task));
    setActiveTaskId(code);
    pushEvent('恢复招聘任务', `任务 ${code} 已恢复到进行中列表`, 'human', code);
    setDialog(null);
    setView('tasks');
    notify('招聘任务已恢复');
  }

  function openDialog(type, data = {}) {
    setDialog({ type, ...data });
  }

  const context = {
    view,
    setView,
    flowStep,
    candidate,
    candidatePool,
    selectedCandidate,
    setSelectedCandidate,
    selectedCandidates,
    toggleCandidate,
    knowledge,
    updateKnowledge,
    removeKnowledge,
    restoreKnowledge,
    tasks,
    activeTask,
    setActiveTaskId,
    updateActiveTask,
    updateTask,
    archiveTask,
    restoreTask,
    events,
    interviewStatuses,
    setInterviewStatuses,
    evaluationDecisions,
    setEvaluationDecisions,
    matchStrategy,
    setMatchStrategy,
    notifications,
    setNotifications,
    notify,
    pushEvent,
    advanceFlow,
    setModalOpen,
    setTaskModalOpen,
    openDialog,
  };

  return (
    <div className="app-shell">
      <Sidebar view={view} setView={setView} taskCount={tasks.filter((task) => !task.archived).length} interviewCount={selectedCandidates.length} onProfile={() => setProfileOpen((value) => !value)} profileOpen={profileOpen} />
      <div className="app-column">
        <Topbar setView={setView} notifications={notifications} notificationOpen={notificationOpen} setNotificationOpen={setNotificationOpen} setNotifications={setNotifications} onSearch={() => setGlobalSearchOpen(true)} openDialog={openDialog} />
        <main className={classNames('main-content', `view-${view}`)}>
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
      {globalSearchOpen && <GlobalSearch tasks={tasks} candidates={candidatePool} knowledge={knowledge} onClose={() => setGlobalSearchOpen(false)} onNavigate={(target, id) => { if (target === 'tasks' && id) { const task = tasks.find((item) => item.code === id); setActiveTaskId(id); setView(task?.stage === '岗位方案' ? 'roleplan' : 'workspace'); } else if (target === 'knowledge' && id) { const item = knowledge.find((entry) => entry.id === id); setView('knowledge'); if (item) setDialog({ type: 'knowledge', item }); } else { if (target === 'talent' && id) setSelectedCandidate(id); setView(target); } setGlobalSearchOpen(false); }} />}
      {dialog && <DetailDialog dialog={dialog} onClose={() => setDialog(null)} context={context} />}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toast}
        </div>
      )}
    </div>
  );
}

function Sidebar({ view, setView, taskCount, interviewCount, onProfile, profileOpen }) {
  const profileAreaRef = useRef(null);
  useEffect(() => {
    if (!profileOpen) return undefined;
    const closeOnOutsideClick = (event) => {
      if (!profileAreaRef.current?.contains(event.target)) onProfile();
    };
    window.addEventListener('pointerdown', closeOnOutsideClick);
    return () => window.removeEventListener('pointerdown', closeOnOutsideClick);
  }, [profileOpen, onProfile]);

  const openProfileView = (nextView) => {
    setView(nextView);
    onProfile();
  };

  return (
    <aside className="sidebar">
      <button className="brand" onClick={() => setView('workspace')} aria-label="返回工作台">
        <span className="brand-mark"><Sparkles size={21} /></span>
        <span><strong>知聘</strong><small>招聘智能体</small></span>
      </button>

      <nav className="primary-nav" aria-label="主导航">
        <span className="nav-label">招聘执行</span>
        {navItems.map((item) => <NavItem key={item.id} item={item.id === 'tasks' ? { ...item, count: taskCount } : item.id === 'interviews' ? { ...item, count: interviewCount } : item} active={view === item.id} onClick={() => setView(item.id)} />)}
        <span className="nav-label manage-label">智能体管理</span>
        {manageItems.map((item) => <NavItem key={item.id} item={item} active={view === item.id} onClick={() => setView(item.id)} />)}
      </nav>

      <div className="sidebar-foot">
        <div className="secure-note">
          <ShieldCheck size={18} />
          <span><strong>安全运行</strong><small>全部操作已留痕</small></span>
        </div>
        <div className="user-menu-wrap" ref={profileAreaRef}>
          <button className="user-menu" onClick={onProfile} aria-expanded={profileOpen} aria-haspopup="menu">
            <span className="avatar avatar-blue">李</span>
            <span><strong>李佳</strong><small>招聘经理</small></span>
            <MoreHorizontal size={17} />
          </button>
          {profileOpen && <div className="profile-menu" role="menu"><button role="menuitem" onClick={() => openProfileView('audit')}><UserRound size={15} />个人操作记录</button><button role="menuitem" onClick={() => openProfileView('knowledge')}><Settings2 size={15} />知识库管理</button><span>演示账号 · 数据仅保存在本机</span></div>}
        </div>
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

function Topbar({ setView, notifications, notificationOpen, setNotificationOpen, setNotifications, onSearch, openDialog }) {
  const unread = notifications.filter((item) => !item.read).length;
  function openNotification(item) {
    setNotifications((items) => items.map((entry) => entry.id === item.id ? { ...entry, read: true } : entry));
    setView(item.target);
    setNotificationOpen(false);
  }
  return (
    <header className="topbar">
      <div className="mobile-brand"><Sparkles size={18} /><strong>知聘</strong></div>
      <button className="enterprise-switcher" onClick={() => openDialog('enterprise')}>
        <span className="enterprise-icon"><Building2 size={17} /></span>
        <span>华岳能源集团</span>
        <ChevronDown size={15} />
      </button>
      <div className="topbar-actions">
        <button className="command-search" onClick={onSearch}>
          <Search size={16} />
          <span>搜索候选人或任务</span>
          <kbd>⌘ K</kbd>
        </button>
        <button className="icon-button" title="帮助" onClick={() => openDialog('help')}><CircleHelp size={18} /></button>
        <div className="notification-wrap">
          <button className="icon-button notification" title="通知" onClick={() => setNotificationOpen((value) => !value)} aria-expanded={notificationOpen}><Bell size={18} />{unread > 0 && <i />}</button>
          {notificationOpen && <div className="notification-panel">
            <div className="popover-head"><div><strong>通知中心</strong><small>{unread} 条未读</small></div><button onClick={() => setNotifications((items) => items.map((item) => ({ ...item, read: true })))}>全部已读</button></div>
            <div className="notification-list">{notifications.map((item) => <button className={classNames('notification-item', !item.read && 'unread')} key={item.id} onClick={() => openNotification(item)}><span className="notification-mark" /><span><strong>{item.title}</strong><p>{item.detail}</p><small>{item.time}</small></span><ChevronRight size={15} /></button>)}</div>
          </div>}
        </div>
      </div>
    </header>
  );
}

function Workspace({ flowStep, selectedCandidates, candidatePool, setSelectedCandidate, setView, advanceFlow, events, activeTask, updateActiveTask, notify, pushEvent }) {
  const stageMessage = {
    岗位方案: '正在等待人工确认岗位方案',
    人才搜索: '正在检索集团人才库并生成匹配排序',
    名单确认: '正在等待人工确认候选名单',
    在线面试: '正在跟踪候选人在线面试状态',
    综合评价: '正在等待人工确认综合评价',
    已完成: '任务已完成，等待招聘结果回流',
  }[activeTask.stage];
  const taskEvents = events.filter((event) => event.taskId === activeTask.code);
  const activityFeed = taskEvents.slice(0, 3);
  const [liveSeconds, setLiveSeconds] = useState(0);
  const [activityCursor, setActivityCursor] = useState(0);
  useEffect(() => {
    setLiveSeconds(0);
    setActivityCursor(0);
    const timer = window.setInterval(() => {
      setLiveSeconds((value) => {
        const next = value + 1;
        if (next % 4 === 0) setActivityCursor((cursor) => (cursor + 1) % Math.max(activityFeed.length, 1));
        return next;
      });
    }, 1000);
    return () => window.clearInterval(timer);
  }, [activeTask.code, activityFeed.length]);
  useEffect(() => {
    if (activeTask.stage !== '人才搜索') return undefined;
    const timer = window.setTimeout(() => {
      updateActiveTask({ stage: '名单确认', progress: 48, tone: 'blue' });
      pushEvent('完成人才库检索', `在 2,846 份简历中为${activeTask.role}筛选出 12 位候选人`, 'success');
      notify('人才搜索已完成，等待确认候选名单');
    }, 1400);
    return () => window.clearTimeout(timer);
  }, [activeTask.code, activeTask.stage]);
  const currentActivity = activityFeed[activityCursor % Math.max(activityFeed.length, 1)];
  const elapsedSeconds = 8 * 60 + 42 + liveSeconds;
  const elapsedLabel = `${Math.floor(elapsedSeconds / 60)} 分 ${String(elapsedSeconds % 60).padStart(2, '0')} 秒`;
  const primaryAction = {
    岗位方案: { label: '审核岗位方案', action: () => setView('roleplan') },
    人才搜索: { label: '运行人才搜索', action: advanceFlow },
    名单确认: { label: '确认候选名单', action: () => setView('talent') },
    在线面试: { label: '查看面试进度', action: () => setView('interviews') },
    综合评价: { label: '查看综合评价', action: () => setView('evaluation') },
    已完成: { label: '查看归档结果', action: () => setView('evaluation') },
  }[activeTask.stage];
  return (
    <>
      <PageHeader
        eyebrow={`招聘任务 / ${activeTask.code}`}
        title={`${activeTask.role}招聘`}
        description={`${activeTask.dept} · ${activeTask.city} · ${activeTask.recruitmentType} ${activeTask.count}`}
        actions={
          <>
            <button className="btn secondary" onClick={() => setView('audit')}><Activity size={17} />运行记录</button>
            <button className="btn primary" onClick={primaryAction.action}>
              {primaryAction.label}
              <ArrowRight size={17} />
            </button>
          </>
        }
      />

      <section className="flow-surface" aria-label="招聘流程进度">
        <div className="flow-topline">
          <div><Bot size={18} /><strong>{activeTask.stage === '已完成' ? '任务已完成' : '智能体执行中'}</strong><span className="pulse-dot" />{stageMessage}</div>
          <span>已运行 {elapsedLabel}</span>
        </div>
        <div className="flow-steps">
          {flowSteps.map((step, index) => {
            const completed = index < flowStep;
            const active = index === flowStep;
            return (
              <button type="button" aria-current={active ? 'step' : undefined} aria-label={`${step.title}，${completed ? step.completedNote : active ? step.activeNote : step.pendingNote}`} className={classNames('flow-step', completed && 'completed', active && 'current')} key={step.title} onClick={() => { if (index === 0) setView('roleplan'); if (index === 1 || index === 2) setView('talent'); if (index === 3) setView('interviews'); if (index === 4) setView('evaluation'); }}>
                <div className="step-line" />
                <span className="step-dot">{completed ? <Check size={14} /> : index + 1}</span>
                <div><strong>{step.title}</strong><small>{completed ? step.completedNote : active ? step.activeNote : step.pendingNote}</small></div>
              </button>
            );
          })}
        </div>
      </section>

      <div className="dashboard-grid">
        <section className="panel task-panel">
          {flowStep < 2 ? (
            <div className="workspace-gate">
              <span className={classNames('workspace-gate-icon', flowStep === 1 && 'searching')}>{flowStep === 0 ? <FileText size={26} /> : <Search size={26} />}</span>
              <div><span className="section-kicker">{flowStep === 0 ? '等待人工确认' : '智能体自动执行'}</span><h2>{flowStep === 0 ? '岗位方案已生成' : '正在检索集团人才库'}</h2><p>{flowStep === 0 ? '请确认岗位职责、任职标准与人才推荐评分卡，确认后智能体才会开始检索候选人。' : `正在应用“${activeTask.role}评分卡”提取简历证据并生成可解释排序。`}</p></div>
              <div className="workspace-gate-meta"><span><ShieldCheck size={15} />知识与规则已校验</span><span><History size={15} />全过程记录审计日志</span></div>
              {flowStep === 0 ? <button className="btn primary" onClick={() => setView('roleplan')}>审核岗位方案 <ArrowRight size={17} /></button> : <button className="btn secondary" onClick={() => setView('audit')}>查看实时记录 <Activity size={16} /></button>}
            </div>
          ) : <>
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
            {candidatePool.slice(0, 3).map((person, index) => (
              <button className="candidate-preview" key={person.id} onClick={() => { setSelectedCandidate(person.id); setView('talent'); }}>
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
            <button className="btn primary" onClick={() => setView('talent')}>前往确认 {selectedCandidates.length} 人 <ArrowRight size={17} /></button>
          </div>
          </>}
        </section>

        <aside className="panel agent-panel">
          <div className="panel-heading">
            <div><span className="section-kicker">实时状态</span><h2>智能体动态</h2></div>
            <button className="icon-button small" title="查看运行审计" onClick={() => setView('audit')}><History size={16} /></button>
          </div>
          <div className="agent-identity">
            <span className="agent-orbit"><Bot size={25} /></span>
            <div><strong>招聘执行智能体</strong><small><i />在线 · 受控执行 · 实时同步</small></div>
          </div>
          <div className="activity-now" aria-live="polite">
            <div className="activity-now-head"><span><i />实时处理</span><em>LIVE</em></div>
            <strong>{stageMessage}</strong>
            <p key={currentActivity?.id || activeTask.stage}>{currentActivity ? `最近节点：${currentActivity.title}` : '等待当前任务产生新的执行记录'}</p>
            <div className="activity-signal" aria-hidden="true">{[1, 2, 3, 4, 5, 6, 7, 8].map((item) => <i key={item} />)}</div>
          </div>
          <div className="event-list compact">
            {activityFeed.map((event, index) => (
              <div className={classNames('event-item', index === activityCursor && 'active')} key={`${event.time}-${index}`}>
                <span className={classNames('event-mark', event.type)}>{event.type === 'human' ? <UserCheck size={13} /> : <Check size={13} />}</span>
                <div><strong>{event.title}</strong><p>{event.detail}</p><time>{index === activityCursor ? '正在同步' : event.time}</time></div>
              </div>
            ))}
            {!activityFeed.length && <div className="activity-empty"><Clock3 size={15} />等待新的任务动态</div>}
          </div>
          <button className="full-link" onClick={() => setView('audit')}>查看完整运行记录 <ArrowRight size={15} /></button>
        </aside>
      </div>

    </>
  );
}

function RolePlan({ setView, notify, pushEvent, activeTask, updateActiveTask, knowledge, openDialog }) {
  const generatedPlan = useMemo(() => getRolePlan(activeTask), [activeTask]);
  const [editing, setEditing] = useState(false);
  const [summary, setSummary] = useState(activeTask?.requirement || '负责核心业务工作，持续提升组织效能与业务支撑能力。');
  const [requirements, setRequirements] = useState(activeTask?.planRequirements || generatedPlan.requirements);
  const scoreRules = generatedPlan.scoreRules;
  const thresholds = generatedPlan.thresholds;

  useEffect(() => {
    setEditing(false);
    setSummary(activeTask?.requirement || '负责核心业务工作，持续提升组织效能与业务支撑能力。');
    setRequirements(activeTask?.planRequirements || generatedPlan.requirements);
  }, [activeTask?.code, activeTask?.requirement, activeTask?.planRequirements, generatedPlan]);

  function savePlan() {
    setEditing(false);
    updateActiveTask({ requirement: summary, planRequirements: requirements, planConfirmed: true, stage: activeTask.stage === '岗位方案' ? '人才搜索' : activeTask.stage, progress: activeTask.stage === '岗位方案' ? 30 : activeTask.progress });
    pushEvent('人工确认岗位方案', '招聘经理确认 JD、任职标准与人才推荐评分卡', 'human');
    notify('岗位方案已保存并确认');
    setView('workspace');
  }

  return (
    <>
      <PageHeader
        eyebrow={`招聘任务 ${activeTask?.code || 'R2026-0718'} / 岗位方案`}
        title={activeTask?.role || '高级后端开发工程师'}
        description={`${activeTask?.dept || '数字科技部'} · ${activeTask?.city || '北京'} · 招聘 ${activeTask?.count || '2人'} · 智能体生成方案待审核`}
        actions={<><button className="btn secondary" onClick={() => setEditing((value) => !value)}>{editing ? <X size={16} /> : <FileText size={16} />}{editing ? '取消编辑' : '编辑JD'}</button><button className="btn secondary" onClick={() => openDialog('task-edit', { task: activeTask })}><Settings2 size={16} />任务设置</button><button className="btn primary" onClick={savePlan}><CheckCircle2 size={17} />{activeTask.planConfirmed ? '保存岗位方案' : '确认岗位方案'}</button></>}
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
          <div className="document-main-grid">
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
          </div>
          <div className="document-block optional-block">
            <h3>优先条件</h3>
            <div className="tag-list">{generatedPlan.tags.map((item) => <span key={item}>{item}</span>)}</div>
          </div>
        </section>
        <aside className="plan-side">
          <section className="panel scoring-card">
            <div className="panel-heading"><div><span className="section-kicker">人才推荐标准</span><h2>岗位评分卡</h2></div><button className="text-button" onClick={() => openDialog('scorecard-edit')}>编辑标准 <Edit3 size={14} /></button></div>
            <div className="score-rule-list">
              {scoreRules.map((rule, index) => <div className="score-rule" key={`${rule.label}-${index}`}><span className="rule-weight">{rule.weight}<small>%</small></span><div><strong>{rule.label}</strong><p>{rule.detail}</p><i><b style={{ width: `${rule.weight}%` }} /></i></div></div>)}
            </div>
            <div className="threshold-list"><div><span>强烈推荐</span><strong>≥ {thresholds.strong}</strong></div><div><span>推荐</span><strong>{thresholds.recommended}-{thresholds.strong - 1}</strong></div><div><span>待确认</span><strong>{thresholds.review}-{thresholds.recommended - 1}</strong></div></div>
          </section>
        </aside>
        <section className="panel source-panel">
          <div className="panel-heading"><div><span className="section-kicker">生成依据</span><h2>知识来源</h2></div><button className="text-button" onClick={() => setView('knowledge')}>管理知识库 <ArrowRight size={15} /></button></div>
          <div className="plan-source-grid">{generatedPlan.sourceIds.map((id) => knowledge.find((item) => item.id === id && !item.archived)).filter(Boolean).map((item) => <button className="plan-source" key={item.id} onClick={() => openDialog('knowledge', { item })}><FileText size={16} /><span><strong>{item.title}</strong><small>{item.type} · {item.version}</small></span><Eye size={13} /></button>)}</div>
        </section>
      </div>
    </>
  );
}

function Tasks({ setView, tasks, setActiveTaskId, setTaskModalOpen, openDialog }) {
  const [query, setQuery] = useState('');
  const [stageFilter, setStageFilter] = useState('全部状态');
  const [deptFilter, setDeptFilter] = useState('全部部门');
  const [showArchived, setShowArchived] = useState(false);
  const departments = ['全部部门', ...new Set(tasks.map((task) => task.dept))];
  const stages = ['全部状态', '岗位方案', '人才搜索', '名单确认', '在线面试', '综合评价', '已完成'];
  const filteredTasks = tasks.filter((task) => (showArchived ? task.archived : !task.archived) && (stageFilter === '全部状态' || task.stage === stageFilter) && (deptFilter === '全部部门' || task.dept === deptFilter) && `${task.role}${task.dept}${task.code}`.toLowerCase().includes(query.trim().toLowerCase()));
  return (
    <>
      <PageHeader eyebrow="招聘执行" title="招聘任务" description="统一管理由智能体协同执行的招聘任务"
        actions={<button className="btn primary" onClick={() => setTaskModalOpen(true)}><Plus size={17} />新建招聘任务</button>} />
      <section className="toolbar-band">
        <label className="search-field"><Search size={16} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索岗位、部门或任务编号" /></label>
        <label className="select-button"><ListFilter size={16} /><select value={stageFilter} onChange={(e) => setStageFilter(e.target.value)}>{stages.map((stage) => <option key={stage}>{stage}</option>)}</select><ChevronDown size={14} /></label>
        <label className="select-button"><Building2 size={16} /><select value={deptFilter} onChange={(e) => setDeptFilter(e.target.value)}>{departments.map((dept) => <option key={dept}>{dept}</option>)}</select><ChevronDown size={14} /></label>
        <button className={classNames('btn secondary', showArchived && 'is-active')} onClick={() => setShowArchived((value) => !value)}><Archive size={16} />{showArchived ? '返回进行中' : '查看归档'}</button>
      </section>
      <section className="table-panel">
        <div className="data-table task-table">
          <div className="table-row table-head"><span>招聘任务</span><span>招聘信息</span><span>当前阶段</span><span>负责人</span><span>计划完成</span><span /></div>
          {filteredTasks.map((task) => (
            <button className="table-row" key={task.code} onClick={() => { setActiveTaskId(task.code); setView(task.stage === '岗位方案' ? 'roleplan' : 'workspace'); }}>
              <span className="cell-main"><strong>{task.role}</strong><small>{task.code} · {task.dept}</small></span>
              <span><strong>{task.city} · {task.count}</strong><small>{task.recruitmentType}</small></span>
              <span className="progress-cell"><StatusPill tone={task.tone}>{task.stage}</StatusPill><i><b style={{ width: `${task.progress}%` }} /></i></span>
              <span className="owner-cell"><em className="mini-avatar">{task.owner.slice(0, 1)}</em>{task.owner}</span>
              <span>{task.due}</span>
              <span className="row-actions"><span onClick={(event) => { event.stopPropagation(); openDialog('task-edit', { task }); }} title="编辑任务"><MoreHorizontal size={16} /></span><ArrowRight size={16} /></span>
            </button>
          ))}
          {!filteredTasks.length && <div className="empty-state"><BriefcaseBusiness size={23} /><strong>{showArchived ? '暂无归档任务' : '未找到招聘任务'}</strong><span>请调整搜索词或筛选条件</span></div>}
        </div>
      </section>
    </>
  );
}

function TaskModal({ onClose, onSubmit }) {
  const [messages, setMessages] = useState([{ id: 1, role: 'assistant', text: '请告诉我这次想招什么人，可以像平时沟通需求一样描述。' }]);
  const [input, setInput] = useState('');
  const [draft, setDraft] = useState(null);
  const [pending, setPending] = useState('');

  useEffect(() => {
    if (!pending) return undefined;
    const timer = window.setTimeout(() => {
      const nextDraft = inferTaskFromPrompt(pending, draft || {});
      const complete = nextDraft.role !== '待确认岗位';
      const missingLabels = [
        ['dept', '需求部门'],
        ['city', '工作地点'],
        ['headcount', '招聘人数'],
        ['recruitmentType', '招聘类型'],
        ['priority', '优先级'],
        ['due', '完成时间'],
      ].filter(([key]) => !nextDraft.confirmedFields?.[key]).map(([, label]) => label);
      setDraft(nextDraft);
      setMessages((items) => [...items, { id: Date.now(), role: 'assistant', text: complete ? missingLabels.length ? `已识别“${nextDraft.role}”。${missingLabels.join('、')}暂使用演示默认值，你可以继续补充，或直接确认后在岗位方案中调整。` : `“${nextDraft.role}”招聘任务信息已整理完整，请核对草案后确认创建。` : '我还没有识别出明确的岗位名称，请再告诉我具体要招聘什么岗位。' }]);
      setPending('');
    }, 520);
    return () => window.clearTimeout(timer);
  }, [pending]);

  function send(event) {
    event.preventDefault();
    const content = input.trim();
    if (!content || pending) return;
    setMessages((items) => [...items, { id: Date.now(), role: 'user', text: content }]);
    setInput('');
    setPending(content);
  }

  const canCreate = draft && draft.role !== '待确认岗位' && !pending;
  const draftFields = draft ? [
    ['需求部门', draft.dept, 'dept'],
    ['工作地点', draft.city, 'city'],
    ['招聘人数', `${draft.headcount}人`, 'headcount'],
    ['招聘类型', draft.recruitmentType, 'recruitmentType'],
    ['优先级', draft.priority, 'priority'],
    ['期望完成', draft.due, 'due'],
    ['知识库', draft.useKnowledge ? '已启用' : '不使用', 'useKnowledge'],
  ] : [];
  const missingFields = draftFields.filter(([, , key]) => key !== 'useKnowledge' && !draft?.confirmedFields?.[key]);
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="modal task-modal task-chat-modal" role="dialog" aria-modal="true" aria-label="对话创建招聘任务" onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <div><span className="section-kicker">招聘需求智能体</span><h2>对话创建招聘任务</h2><p>自然描述需求，由智能体整理任务并生成岗位方案</p></div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button>
        </div>
        <div className="task-chat-body">
          <div className="chat-thread" aria-live="polite">
            {messages.map((message) => <div className={classNames('chat-message', message.role)} key={message.id}>{message.role === 'assistant' && <span className="chat-avatar"><Bot size={17} /></span>}<p>{message.text}</p></div>)}
            {pending && <div className="chat-message assistant"><span className="chat-avatar"><Bot size={17} /></span><p className="thinking-dots"><i /><i /><i /></p></div>}
          </div>
          {draft && <section className="generated-task-draft">
            <div className="draft-heading"><span><Sparkles size={17} /></span><div><strong>招聘任务草案</strong><small>{draft.role === '待确认岗位' ? '等待补充岗位名称' : '请核对智能体整理结果'}</small></div><StatusPill tone={draft.role === '待确认岗位' || missingFields.length ? 'amber' : 'green'}>{draft.role === '待确认岗位' ? '信息不足' : missingFields.length ? `待确认 ${missingFields.length}项` : '可确认'}</StatusPill></div>
            <div className="draft-role"><span><small>招聘岗位</small><strong>{draft.role}</strong></span><em className={draft.confirmedFields?.role ? 'confirmed' : 'inferred'}>{draft.confirmedFields?.role ? '已识别' : '待补充'}</em></div>
            <div className="draft-detail-grid">{draftFields.map(([label, value, key]) => {
              const confirmed = key === 'useKnowledge' || draft.confirmedFields?.[key];
              return <div className={confirmed ? '' : 'inferred'} key={key}><span><small>{label}</small>{!confirmed && <em>智能体暂填</em>}</span><strong>{value}</strong></div>;
            })}</div>
            <div className="draft-requirement"><small>已理解的需求</small><p>{draft.requirement}</p></div>
            {draft.role !== '待确认岗位' && missingFields.length > 0 && <div className="draft-missing"><AlertTriangle size={17} /><div><strong>还有 {missingFields.length} 项建议确认</strong><p>{missingFields.map(([label]) => label).join('、')}当前使用演示默认值，可继续在下方对话中补充。</p></div></div>}
          </section>}
        </div>
        <form className="chat-composer" onSubmit={send}>
          <textarea autoFocus value={input} onChange={(event) => setInput(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) send(event); }} placeholder="例如：数字科技部想在北京紧急招聘2名数据治理专家，希望8月底前到岗，要求有大型企业项目经验。" />
          <button className="icon-button chat-send" type="submit" disabled={!input.trim() || pending} title="发送需求"><Send size={18} /></button>
        </form>
        <div className="modal-actions"><button type="button" className="btn secondary" onClick={onClose}>取消</button><button type="button" className="btn primary" disabled={!canCreate} onClick={() => onSubmit(draft)}><Sparkles size={16} />确认并生成岗位方案</button></div>
      </section>
    </div>
  );
}

function DialogShell({ title, eyebrow, children, onClose, actions, wide = false }) {
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={onClose}>
      <section className={classNames('modal detail-modal', wide && 'wide')} role="dialog" aria-modal="true" aria-label={title} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-header"><div><span className="section-kicker">{eyebrow}</span><h2>{title}</h2></div><button type="button" className="icon-button" onClick={onClose} aria-label="关闭"><X size={18} /></button></div>
        <div className="detail-modal-body">{children}</div>
        {actions && <div className="modal-actions">{actions}</div>}
      </section>
    </div>
  );
}

function GlobalSearch({ tasks, candidates, knowledge, onClose, onNavigate }) {
  const [query, setQuery] = useState('');
  const normalized = query.trim().toLowerCase();
  const results = normalized ? [
    ...tasks.filter((item) => !item.archived && `${item.role}${item.dept}${item.code}`.toLowerCase().includes(normalized)).map((item) => ({ id: item.code, target: 'tasks', type: '招聘任务', title: item.role, detail: `${item.code} · ${item.dept} · ${item.stage}`, icon: BriefcaseBusiness })),
    ...candidates.filter((item) => `${item.name}${item.title}${item.company}`.toLowerCase().includes(normalized)).map((item) => ({ id: item.id, target: 'talent', type: '候选人', title: item.name, detail: `${item.title} · 匹配度 ${item.score}`, icon: UserRound })),
    ...knowledge.filter((item) => !item.archived && `${item.title}${item.type}${item.owner}`.toLowerCase().includes(normalized)).map((item) => ({ id: item.id, target: 'knowledge', type: '知识资料', title: item.title, detail: `${item.type} · ${item.version} · ${item.owner}`, icon: BookOpenText })),
  ].slice(0, 12) : [];
  return (
    <div className="modal-backdrop search-backdrop" role="presentation" onMouseDown={onClose}>
      <section className="global-search" role="dialog" aria-modal="true" aria-label="全局搜索" onMouseDown={(event) => event.stopPropagation()}>
        <div className="global-search-input"><Search size={19} /><input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索任务、候选人或知识资料" /><button className="icon-button" onClick={onClose}><X size={17} /></button></div>
        {!normalized && <div className="search-suggestions"><span>快捷入口</span><div><button onClick={() => onNavigate('tasks')}><BriefcaseBusiness size={16} />招聘任务</button><button onClick={() => onNavigate('talent')}><UsersRound size={16} />人才匹配</button><button onClick={() => onNavigate('knowledge')}><BookOpenText size={16} />知识库</button></div></div>}
        {normalized && <div className="search-results">{results.length ? results.map((result) => { const Icon = result.icon; return <button key={`${result.target}-${result.id}`} onClick={() => onNavigate(result.target, result.id)}><span className="result-icon"><Icon size={17} /></span><span><small>{result.type}</small><strong>{result.title}</strong><p>{result.detail}</p></span><ArrowRight size={16} /></button>; }) : <div className="empty-state compact"><Search size={20} /><strong>没有找到相关内容</strong><span>可以尝试岗位名称、候选人姓名或资料标题</span></div>}</div>}
      </section>
    </div>
  );
}

function DetailDialog({ dialog, onClose, context }) {
  const { activeTask, updateActiveTask, updateTask, archiveTask, restoreTask, updateKnowledge, removeKnowledge, restoreKnowledge, notify, pushEvent, matchStrategy, setMatchStrategy, setInterviewStatuses, toggleCandidate } = context;
  if (dialog.type === 'help') return <DialogShell title="演示帮助中心" eyebrow="产品帮助" onClose={onClose}><div className="help-grid"><div><span>01</span><strong>创建招聘任务</strong><p>录入需求后，智能体会生成岗位方案、评分卡和知识引用。</p></div><div><span>02</span><strong>确认人才名单</strong><p>查看候选人匹配证据，调整策略并选择进入面试的人选。</p></div><div><span>03</span><strong>模拟面试闭环</strong><p>发送提醒、回收结果、查看转写和综合评价。</p></div><div><span>04</span><strong>维护企业知识</strong><p>新增、复核和归档岗位知识、人才画像与制度流程。</p></div></div><div className="dialog-note"><ShieldCheck size={17} />演示数据均为虚构数据，所有修改仅保存在当前浏览器。</div></DialogShell>;
  if (dialog.type === 'enterprise') return <DialogShell title="企业空间" eyebrow="当前租户" onClose={onClose}><div className="enterprise-dialog"><span className="enterprise-logo"><Building2 size={23} /></span><div><strong>华岳能源集团</strong><p>集团招聘智能体演示环境</p></div></div><div className="detail-list"><div><span>组织范围</span><strong>集团总部及 12 家下属企业</strong></div><div><span>数据环境</span><strong>演示数据 · 本地存储</strong></div><div><span>知识权限</span><strong>组织人事部 / 招聘中心</strong></div></div><div className="dialog-note"><LockKeyhole size={17} />正式接入客户系统后，企业空间将隔离任务、人才与知识数据。</div></DialogShell>;
  if (dialog.type === 'rules') return <DialogShell title="智能体执行边界" eyebrow="安全与治理" onClose={onClose}><div className="rule-dialog-list">{[['允许自动执行','人才检索、评分计算、邀约提醒、结果回收、报告草拟'],['必须人工确认','岗位发布、候选名单、淘汰决定、录用决定、Offer审批'],['禁止使用','年龄、性别、婚育、籍贯等与岗位胜任无关的敏感属性'],['全程留痕','知识引用、评分证据、人工修改和对外操作均记录审计日志']].map(([title, text], index) => <div key={title}><span>{index < 2 ? <CheckCircle2 size={17} /> : <ShieldCheck size={17} />}</span><div><strong>{title}</strong><p>{text}</p></div></div>)}</div></DialogShell>;
  if (dialog.type === 'governance') return <DialogShell title="知识治理规则" eyebrow="知识库管理" onClose={onClose}><div className="rule-dialog-list">{[['上传检查','识别候选人隐私、敏感属性、重复资料和文件有效期'],['解析复核','新资料默认进入待复核状态，确认后才参与智能体检索'],['版本管理','保留资料版本、维护部门、更新时间和引用记录'],['画像约束','只使用能力与行为证据，不使用受保护或非岗位相关属性']].map(([title, text]) => <div key={title}><span><ShieldCheck size={17} /></span><div><strong>{title}</strong><p>{text}</p></div></div>)}</div></DialogShell>;
  if (dialog.type === 'task-edit') { const task = dialog.task || activeTask; return <TaskEditDialog task={task} onClose={onClose} onSave={(changes) => { updateTask(task.code, changes); onClose(); }} onArchive={() => task.archived ? restoreTask(task.code) : archiveTask(task.code)} />; }
  if (dialog.type === 'knowledge') return <KnowledgeDetailDialog item={dialog.item} onClose={onClose} onSave={(changes) => { updateKnowledge(dialog.item.id, { ...changes, updated: localDateString() }); pushEvent('更新知识资料', `${changes.title || dialog.item.title} 已保存新版本`, 'human'); notify('知识资料已更新'); onClose(); }} onArchive={() => dialog.item.archived ? restoreKnowledge(dialog.item.id) : removeKnowledge(dialog.item.id)} />;
  if (dialog.type === 'resume') return <DialogShell title={`${dialog.person.name} · 简历原文`} eyebrow="候选人档案" onClose={onClose} wide actions={<button className="btn primary" onClick={() => { downloadText(`${dialog.person.name}-简历.txt`, buildResumeText(dialog.person)); notify('简历已下载'); }}><Download size={16} />下载简历</button>}><ResumeDocument person={dialog.person} highlight={dialog.highlight} /></DialogShell>;
  if (dialog.type === 'scorecard') return <DialogShell title="人才匹配评分卡" eyebrow="当前岗位规则" onClose={onClose}><ScorecardDetail task={activeTask} strategy={dialog.strategy || matchStrategy} /></DialogShell>;
  if (dialog.type === 'scorecard-edit') return <RoleScorecardDialog task={activeTask} onClose={onClose} onSave={({ rules, thresholds }) => {
    updateActiveTask({ planScoreRules: rules, planThresholds: thresholds });
    setMatchStrategy((current) => ({ ...current, ...Object.fromEntries(rules.map((rule) => [rule.label, rule.weight])), minScore: thresholds.review }));
    pushEvent('调整人才推荐标准', `评分卡更新为 ${rules.length} 个维度，推荐阈值 ${thresholds.review}-${thresholds.strong} 分`, 'human');
    notify('人才推荐标准已保存，候选人匹配结果已同步更新');
    onClose();
  }} />;
  if (dialog.type === 'strategy') return <MatchStrategyDialog task={activeTask} strategy={matchStrategy} onClose={onClose} onSave={(strategy) => { setMatchStrategy(strategy); pushEvent('调整人才匹配策略', `已更新最低匹配阈值 ${strategy.minScore} 分和评分维度权重`, 'human'); notify('匹配策略已保存'); onClose(); }} />;
  if (dialog.type === 'interview-actions') return <InterviewActionDialog person={dialog.person} done={dialog.done} onClose={onClose} onRemind={() => { pushEvent('发送面试提醒', `已向 ${dialog.person.name} 发送在线面试提醒`, 'success'); notify('面试提醒已发送'); }} onDeadline={() => context.openDialog('interview-deadline', { person: dialog.person })} onWithdraw={() => { toggleCandidate(dialog.person.id); setInterviewStatuses((items) => ({ ...items, [dialog.person.id]: '已撤回' })); pushEvent('撤回面试邀请', `${dialog.person.name} 已移出当前面试批次`, 'human'); notify('面试邀请已撤回'); onClose(); }} onTranscript={() => context.openDialog('transcript', { person: dialog.person })} />;
  if (dialog.type === 'interview-deadline') return <InterviewDeadlineDialog person={dialog.person} deadline={activeTask.interviewDeadline} onClose={onClose} onSave={(deadline) => { updateActiveTask({ interviewDeadline: deadline }); pushEvent('更新面试截止时间', `${dialog.person.name} 的面试截止时间调整为 ${deadline}`, 'human'); notify('面试截止时间已更新并通知候选人'); onClose(); }} />;
  if (dialog.type === 'evidence') return <DialogShell title={`${dialog.label} · 评价证据`} eyebrow="可解释评价" onClose={onClose}><div className="evidence-dialog"><div className="evidence-dialog-score"><strong>{dialog.score}</strong><span>/100</span><StatusPill tone="green">证据充分</StatusPill></div><blockquote>“{dialog.quote || '候选人在回答中给出了明确的情境、行动和量化结果，能够支持该项评分。'}”</blockquote><div className="detail-list"><div><span>评价来源</span><strong>{dialog.source || '结构化评价标准'}</strong></div><div><span>评价方式</span><strong>固定规则评分 + AI证据提取</strong></div><div><span>人工复核</span><strong>待招聘经理确认</strong></div></div></div></DialogShell>;
  if (dialog.type === 'transcript') { const person = dialog.person || context.candidate || candidatesSeed[0]; return <DialogShell title={`${person.name} · 在线面试完整记录`} eyebrow="面试转写" onClose={onClose} wide><div className="transcript-list">{[['00:42','面试智能体','请介绍一次你主导的复杂项目，以及你承担的关键职责。'],['01:03',person.name,person.evidence[0]?.quote || '候选人结合项目背景说明了关键行动与成果。'],['03:18','面试智能体','项目推进过程中最大的风险是什么？你如何控制？'],['03:31',person.name,person.evidence[1]?.quote || '候选人说明了风险识别、协同推进和结果复盘过程。']].map(([time, speaker, text]) => <div key={time}><time>{time}</time><span><strong>{speaker}</strong><p>{text}</p></span></div>)}</div></DialogShell>; }
  if (dialog.type === 'audit') return <DialogShell title={dialog.event.title} eyebrow="审计详情" onClose={onClose}><div className="detail-list"><div><span>任务编号</span><strong>{dialog.event.taskId || '-'}</strong></div><div><span>执行主体</span><strong>{dialog.event.actor || '招聘执行智能体'}</strong></div><div><span>执行时间</span><strong>{dialog.event.date || '2026-07-22'} {dialog.event.time}</strong></div><div><span>输入</span><strong>{dialog.event.input || dialog.event.detail}</strong></div><div><span>输出</span><strong>{dialog.event.output || dialog.event.detail}</strong></div><div><span>策略校验</span><strong className="success-text">通过 · 未触发敏感规则</strong></div></div></DialogShell>;
  return null;
}

function InterviewActionDialog({ person, done, onClose, onRemind, onDeadline, onWithdraw, onTranscript }) {
  return <DialogShell title={`${person.name} · 面试操作`} eyebrow="在线面试" onClose={onClose} actions={<button className="btn secondary" onClick={onClose}>关闭</button>}><div className="interview-action-dialog"><div className="person-identity"><span className="avatar avatar-large">{person.initials}</span><div><h3>{person.name}</h3><p>{person.title} · {person.company}</p></div></div><div className="action-menu-list">{done ? <button onClick={onTranscript}><Video size={17} /><span><strong>查看完整转写</strong><small>查看回答、时间轴和结构化评价证据</small></span><ArrowRight size={16} /></button> : <button onClick={onRemind}><Send size={17} /><span><strong>发送面试提醒</strong><small>通过默认渠道重新提醒候选人完成作答</small></span><ArrowRight size={16} /></button>}<button onClick={onDeadline}><CalendarDays size={17} /><span><strong>更新截止时间</strong><small>设置新的作答截止时间并通知候选人</small></span><ArrowRight size={16} /></button><button className="destructive-action" onClick={onWithdraw}><Archive size={17} /><span><strong>撤回面试邀请</strong><small>从当前批次移除该候选人，不会删除简历档案</small></span><ArrowRight size={16} /></button></div></div></DialogShell>;
}

function InterviewDeadlineDialog({ person, deadline, onClose, onSave }) {
  const [value, setValue] = useState(deadline || '2026-07-25');
  return <DialogShell title="更新面试截止时间" eyebrow={person.name} onClose={onClose} actions={<><button className="btn secondary" onClick={onClose}>取消</button><button className="btn primary" disabled={!value} onClick={() => onSave(value)}><Send size={16} />保存并通知</button></>}><div className="dialog-form deadline-dialog"><label><span>新的截止日期</span><input autoFocus type="date" value={value} onInput={(event) => setValue(event.currentTarget.value)} /></label><div className="dialog-note"><CalendarDays size={17} />保存后将生成操作记录，并模拟向候选人发送新的截止时间通知。</div></div></DialogShell>;
}

function RoleScorecardDialog({ task, onClose, onSave }) {
  const defaults = useMemo(() => getRolePlan({ ...task, planScoreRules: null, planThresholds: null }), [task]);
  const current = getRolePlan(task);
  const [rules, setRules] = useState(() => current.scoreRules.map((rule) => ({ ...rule })));
  const [thresholds, setThresholds] = useState(() => ({ ...current.thresholds }));
  const total = rules.reduce((sum, rule) => sum + Number(rule.weight || 0), 0);
  const rulesValid = rules.length >= 2 && rules.every((rule) => rule.label.trim() && rule.detail.trim() && Number(rule.weight) > 0);
  const thresholdsValid = thresholds.strong <= 100 && thresholds.strong > thresholds.recommended && thresholds.recommended > thresholds.review && thresholds.review >= 0;
  const valid = total === 100 && rulesValid && thresholdsValid;

  function updateRule(index, changes) {
    setRules((items) => items.map((rule, ruleIndex) => ruleIndex === index ? { ...rule, ...changes } : rule));
  }

  function addRule() {
    if (rules.length >= 6) return;
    setRules((items) => {
      const largestIndex = items.reduce((best, rule, index) => rule.weight > items[best].weight ? index : best, 0);
      const available = Math.min(10, Math.max(5, items[largestIndex].weight - 5));
      return [...items.map((rule, index) => index === largestIndex ? { ...rule, weight: rule.weight - available } : rule), { label: '新增评价维度', weight: available, detail: '请填写该维度需要核验的能力与证据' }];
    });
  }

  function removeRule(index) {
    if (rules.length <= 2) return;
    setRules((items) => {
      const removedWeight = Number(items[index].weight || 0);
      const remaining = items.filter((_, ruleIndex) => ruleIndex !== index);
      const largestIndex = remaining.reduce((best, rule, ruleIndex) => rule.weight > remaining[best].weight ? ruleIndex : best, 0);
      return remaining.map((rule, ruleIndex) => ruleIndex === largestIndex ? { ...rule, weight: rule.weight + removedWeight } : rule);
    });
  }

  function reset() {
    setRules(defaults.scoreRules.map((rule) => ({ ...rule })));
    setThresholds({ ...defaults.thresholds });
  }

  const actions = <><button className="btn secondary" onClick={reset}><RefreshCw size={16} />恢复智能体建议</button><button className="btn secondary" onClick={onClose}>取消</button><button className="btn primary" disabled={!valid} onClick={() => onSave({ rules: rules.map((rule) => ({ ...rule, weight: Number(rule.weight) })), thresholds: Object.fromEntries(Object.entries(thresholds).map(([key, value]) => [key, Number(value)])) })}><Save size={16} />保存推荐标准</button></>;

  return <DialogShell title="编辑人才推荐标准" eyebrow={`${task.role} / 岗位评分卡`} onClose={onClose} actions={actions} wide>
    <div className="scorecard-editor-intro"><SlidersHorizontal size={18} /><div><strong>标准将直接用于候选人排序</strong><p>可修改维度名称、证据口径、权重和推荐阈值。权重合计必须为 100%。</p></div><span className={classNames('scorecard-total-badge', total === 100 ? 'valid' : 'invalid')}>{total}%</span></div>
    <div className="scorecard-editor-list">
      {rules.map((rule, index) => <div className="scorecard-editor-row" key={index}>
        <span className="editor-index">{String(index + 1).padStart(2, '0')}</span>
        <label><span>评价维度</span><input value={rule.label} onChange={(event) => updateRule(index, { label: event.target.value })} /></label>
        <label className="evidence-input"><span>证据口径</span><input value={rule.detail} onChange={(event) => updateRule(index, { detail: event.target.value })} /></label>
        <label className="weight-input"><span>权重</span><span><input type="number" min="5" max="80" step="5" value={rule.weight} onChange={(event) => updateRule(index, { weight: Number(event.target.value) })} /><em>%</em></span></label>
        <button className="icon-button small danger-icon" title="删除维度" disabled={rules.length <= 2} onClick={() => removeRule(index)}><Trash2 size={15} /></button>
      </div>)}
    </div>
    <button className="add-score-rule" disabled={rules.length >= 6} onClick={addRule}><Plus size={15} />增加评价维度</button>
    <div className="threshold-editor">
      <div><span className="section-kicker">推荐等级</span><h3>设置分数阈值</h3><p>系统会自动生成连续区间，并同步到人才匹配列表。</p></div>
      <label><span>强烈推荐</span><div><em>≥</em><input type="number" min="1" max="100" value={thresholds.strong} onChange={(event) => setThresholds((items) => ({ ...items, strong: Number(event.target.value) }))} /></div></label>
      <label><span>推荐起点</span><div><em>≥</em><input type="number" min="1" max="99" value={thresholds.recommended} onChange={(event) => setThresholds((items) => ({ ...items, recommended: Number(event.target.value) }))} /></div></label>
      <label><span>待确认起点</span><div><em>≥</em><input type="number" min="0" max="98" value={thresholds.review} onChange={(event) => setThresholds((items) => ({ ...items, review: Number(event.target.value) }))} /></div></label>
    </div>
    {!valid && <div className="scorecard-editor-error"><AlertTriangle size={16} /><span>{total !== 100 ? `当前权重合计 ${total}%，请调整为 100%。` : !thresholdsValid ? '推荐阈值应满足：强烈推荐 > 推荐 > 待确认。' : '请完整填写每个评价维度及证据口径。'}</span></div>}
  </DialogShell>;
}

function MatchStrategyDialog({ task, strategy, onClose, onSave }) {
  const rules = getRolePlan(task).scoreRules;
  const [weights, setWeights] = useState(() => Object.fromEntries(rules.map((rule) => [rule.label, strategy[rule.label] ?? rule.weight])));
  const [minScore, setMinScore] = useState(strategy.minScore ?? 70);
  const [sort, setSort] = useState(strategy.sort ?? 'score');
  const total = Object.values(weights).reduce((sum, value) => sum + Number(value), 0);
  function save() {
    if (total !== 100) return;
    onSave({ ...strategy, ...weights, minScore: Number(minScore), sort });
  }
  return <DialogShell title="调整匹配策略" eyebrow={`${task.role} / 人才搜索`} onClose={onClose} actions={<><button className="btn secondary" onClick={() => { setWeights(Object.fromEntries(rules.map((rule) => [rule.label, rule.weight]))); setMinScore(getRolePlan(task).thresholds.review); setSort('score'); }}>恢复默认</button><button className="btn primary" disabled={total !== 100} onClick={save}><Save size={16} />保存策略</button></>}><div className="strategy-intro"><SlidersHorizontal size={18} /><span><strong>固定评分 + 可解释证据</strong><small>权重合计必须为 100%，调整后会重新计算当前任务的匹配分与排序。</small></span></div><div className="strategy-weights">{rules.map((rule) => <label key={rule.label}><span><strong>{rule.label}</strong><small>{rule.detail}</small></span><input type="range" min="0" max="60" step="5" value={weights[rule.label]} onChange={(event) => setWeights((items) => ({ ...items, [rule.label]: Number(event.target.value) }))} /><b>{weights[rule.label]}%</b></label>)}</div><div className={classNames('strategy-total', total === 100 ? 'valid' : 'invalid')}><span>权重合计</span><strong>{total}%</strong><small>{total === 100 ? '可保存' : '请调整为 100%'}</small></div><div className="form-grid dialog-form strategy-options"><label><span>最低匹配分</span><select value={minScore} onChange={(e) => setMinScore(e.target.value)}>{[...new Set([60, 70, 80, 85, Number(minScore)])].sort((a, b) => a - b).map((score) => <option value={score} key={score}>{score} 分</option>)}</select></label><label><span>默认排序</span><select value={sort} onChange={(e) => setSort(e.target.value)}><option value="score">综合匹配度</option><option value="name">候选人姓名</option></select></label></div></DialogShell>;
}

function TaskEditDialog({ task, onClose, onSave, onArchive }) {
  const [form, setForm] = useState({ ...task });
  const valid = form.role?.trim() && form.dept?.trim() && form.city?.trim() && Number(form.headcount) > 0;
  return <DialogShell title={task.archived ? '查看归档任务' : '编辑招聘任务'} eyebrow={task.code} onClose={onClose} actions={<><button className={task.archived ? 'btn secondary' : 'btn danger'} onClick={onArchive}>{task.archived ? <RefreshCw size={16} /> : <Archive size={16} />}{task.archived ? '恢复任务' : '归档任务'}</button><button className="btn secondary" onClick={onClose}>取消</button>{!task.archived && <button className="btn primary" disabled={!valid} onClick={() => onSave(form)}><Save size={16} />保存修改</button>}</>}><div className="form-grid dialog-form"><label className="span-2"><span>招聘岗位</span><input disabled={task.archived} value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })} /></label><label><span>需求部门</span><input disabled={task.archived} value={form.dept} onChange={(e) => setForm({ ...form, dept: e.target.value })} /></label><label><span>工作地点</span><input disabled={task.archived} value={form.city} onChange={(e) => setForm({ ...form, city: e.target.value })} /></label><label><span>招聘人数</span><input disabled={task.archived} type="number" min="1" value={form.headcount} onChange={(e) => setForm({ ...form, headcount: Number(e.target.value), count: `${e.target.value}人` })} /></label><label><span>招聘类型</span><select disabled={task.archived} value={form.recruitmentType} onChange={(e) => setForm({ ...form, recruitmentType: e.target.value })}><option>社会招聘</option><option>校园招聘</option><option>内部竞聘</option></select></label><label><span>优先级</span><select disabled={task.archived} value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}><option>高</option><option>中</option><option>低</option></select></label><label className="span-2"><span>核心要求</span><textarea disabled={task.archived} value={form.requirement} onChange={(e) => setForm({ ...form, requirement: e.target.value })} /></label></div></DialogShell>;
}

function KnowledgeDetailDialog({ item, onClose, onSave, onArchive }) {
  const [editing, setEditing] = useState(false);
  const initialForm = { ...item, description: item.description || '该资料已完成结构化解析，可用于岗位方案生成和人才评价。', tagsText: (item.tags || []).join('，') };
  const [form, setForm] = useState(initialForm);
  const valid = form.title?.trim() && form.owner?.trim();
  const actions = editing ? <><button className="btn secondary" onClick={() => { setForm(initialForm); setEditing(false); }}>取消</button><button className="btn primary" disabled={!valid} onClick={() => onSave({ ...form, tags: form.tagsText.split(/[，,]/).map((tag) => tag.trim()).filter(Boolean), version: bumpVersion(item.version) })}><Save size={16} />保存新版本</button></> : item.archived ? <button className="btn primary" onClick={onArchive}><RefreshCw size={16} />恢复资料</button> : <><button className="btn danger" onClick={onArchive}><Archive size={16} />归档</button><button className="btn primary" onClick={() => setEditing(true)}><Edit3 size={16} />编辑资料</button></>;
  return <DialogShell title={item.title} eyebrow={`${item.type} · ${item.version}`} onClose={onClose} actions={actions} wide>{editing ? <div className="form-grid dialog-form"><label className="span-2"><span>资料名称</span><input value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label><label><span>状态</span><select value={form.status} onChange={(e) => setForm({ ...form, status: e.target.value })}><option>可用</option><option>待复核</option><option>停用</option></select></label><label><span>维护部门</span><input value={form.owner} onChange={(e) => setForm({ ...form, owner: e.target.value })} /></label><label className="span-2"><span>标签</span><input value={form.tagsText} onChange={(e) => setForm({ ...form, tagsText: e.target.value })} placeholder="使用逗号分隔" /></label><label className="span-2"><span>资料说明</span><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label></div> : <><div className="knowledge-detail-summary"><span className={`file-icon ${item.format.toLowerCase()}`}><FileText size={20} /></span><div><strong>{item.format} · {item.version}</strong><p>{form.description}</p></div><StatusPill tone={item.status === '可用' ? 'green' : 'amber'}>{item.status}</StatusPill></div><div className="detail-list"><div><span>维护部门</span><strong>{item.owner}</strong></div><div><span>最近更新</span><strong>{item.updated}</strong></div><div><span>近30天引用</span><strong>{item.refs} 次</strong></div><div><span>解析状态</span><strong className="success-text">结构化解析完成</strong></div></div><div className="parsed-sections"><h3>已解析内容</h3><div><span>任职要求</span><strong>8 条</strong></div><div><span>能力标签</span><strong>12 项</strong></div><div><span>评价标准</span><strong>4 个维度</strong></div><div><span>适用岗位</span><strong>3 个岗位</strong></div></div></>}</DialogShell>;
}

function bumpVersion(version = 'v1.0') {
  const [major, minor] = version.replace('v', '').split('.').map(Number);
  return `v${major}.${(minor || 0) + 1}`;
}

function buildResumeText(person) {
  return [`候选人：${person.name}`, `当前岗位：${person.title}`, `当前公司：${person.company}`, `工作经验：${person.years}`, `最高学历：${person.education} · ${person.school}`, '', '核心经历与成果', ...person.evidence.map((item) => `- ${item.label}：${item.quote}`), '', `推荐依据：${person.highlights.join('、')}`, `待核实项：${person.risks.join('、')}`].join('\n');
}

function ResumeDocument({ person, highlight }) {
  return <div className="resume-document"><header><div><h3>{person.name}</h3><p>{person.title} · {person.company}</p></div><span>{person.education} · {person.years}</span></header><section><h4>个人概况</h4><p>具备扎实的专业基础和复杂项目实践经验，能够独立承担核心工作并协同多方推动项目交付。</p></section><section><h4>工作经历</h4>{person.evidence.map((item) => <div className={item.label === highlight ? 'highlight' : ''} key={item.label}><strong>{item.label}</strong><p>{item.quote}</p></div>)}</section><section><h4>专业能力</h4><div className="tag-list">{person.highlights.map((item) => <span key={item}>{item}</span>)}</div></section></div>;
}

function ScorecardDetail({ task, strategy }) {
  const plan = getRolePlan(task);
  return <><div className="scorecard-total"><span>总分</span><strong>100</strong><small>规则由招聘负责人维护，AI仅提取证据</small></div><div className="score-rule-list dialog-rules">{plan.scoreRules.map((rule) => { const weight = strategy?.[rule.label] ?? rule.weight; return <div className="score-rule" key={rule.label}><span className="rule-weight">{weight}<small>%</small></span><div><strong>{rule.label}</strong><p>{rule.detail}</p><i><b style={{ width: `${weight}%` }} /></i></div></div>; })}</div><div className="dialog-note"><ShieldCheck size={17} />每项得分必须关联简历或面试原文；信息不足时标记待核实，不自动推断。</div></>;
}

function Talent({ selectedCandidate, setSelectedCandidate, selectedCandidates, candidatePool, toggleCandidate, setView, notify, pushEvent, activeTask, updateActiveTask, setInterviewStatuses, matchStrategy, openDialog }) {
  const [query, setQuery] = useState('');
  const [minScore, setMinScore] = useState(matchStrategy.minScore || 70);
  const [onlySelected, setOnlySelected] = useState(false);
  const selected = candidatePool.find((item) => item.id === selectedCandidate) || candidatePool[0];
  const filteredCandidates = useMemo(() => candidatePool.filter((person) => person.score >= Number(minScore) && (!onlySelected || selectedCandidates.includes(person.id)) && `${person.name}${person.title}${person.company}${person.highlights.join('')}`.toLowerCase().includes(query.trim().toLowerCase())).sort((a, b) => matchStrategy.sort === 'name' ? a.name.localeCompare(b.name, 'zh-CN') : b.score - a.score), [candidatePool, query, minScore, onlySelected, selectedCandidates, matchStrategy.sort]);
  useEffect(() => setMinScore(matchStrategy.minScore || 70), [matchStrategy.minScore]);
  function confirmSelection() {
    if (!activeTask.planConfirmed && activeTask.stage === '岗位方案') {
      notify('请先确认岗位方案，再发起人才面试');
      setView('roleplan');
      return;
    }
    if (!selectedCandidates.length) {
      notify('请至少选择一位候选人');
      return;
    }
    setInterviewStatuses((items) => ({ ...items, ...Object.fromEntries(selectedCandidates.map((id) => [id, items[id] || '待作答'])) }));
    updateActiveTask({ stage: '在线面试', progress: 66, tone: 'amber' });
    pushEvent('候选名单已确认', `人工确认 ${selectedCandidates.length} 位候选人进入在线面试`);
    notify('候选名单已确认，面试邀请已创建');
    setView('interviews');
  }
  return (
    <>
      <PageHeader eyebrow={`${activeTask.code} / 人才搜索`} title="人才匹配" description={`基于“${activeTask.role}”岗位方案，从集团人才库筛选出可解释的候选人排序`}
        actions={<button className="btn primary" onClick={confirmSelection}><Send size={17} />确认名单并发起面试</button>} />
      <section className="match-summary">
        <div><span>评分卡</span><strong>{activeTask.role}评分卡</strong><button title="查看评分规则" onClick={() => openDialog('scorecard')}><Eye size={15} /></button></div>
        <div><span>检索范围</span><strong>集团人才库 · 近 5 年</strong></div>
        <div><span>自动排除</span><strong>硬性条件不符 167 人</strong></div>
        <button className="text-button" onClick={() => openDialog('strategy')}><SlidersHorizontal size={16} />调整匹配策略</button>
      </section>
      <div className="talent-layout">
        <section className="candidate-list-panel">
          <div className="list-toolbar"><label className="search-field"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索候选人、技能或公司" /></label><label className="mini-select" title="最低匹配分"><select value={minScore} onChange={(event) => setMinScore(event.target.value)}>{[...new Set([60, 70, 80, 85, Number(minScore)])].sort((a, b) => a - b).map((score) => <option value={score} key={score}>{score}分+</option>)}</select></label></div>
          <div className="selection-note"><span>推荐候选人 · {filteredCandidates.length} 人</span><button className={onlySelected ? 'selected-filter' : ''} onClick={() => setOnlySelected((value) => !value)}>{onlySelected ? '显示全部' : `已选择 ${selectedCandidates.length}`}</button></div>
          <div className="candidate-list">
            {filteredCandidates.map((person, index) => (
              <button className={classNames('candidate-row', selectedCandidate === person.id && 'selected')} key={person.id} onClick={() => setSelectedCandidate(person.id)}>
                <span className={classNames('checkbox', selectedCandidates.includes(person.id) && 'checked')} onClick={(event) => { event.stopPropagation(); toggleCandidate(person.id); }}>{selectedCandidates.includes(person.id) && <Check size={13} />}</span>
                <span className="rank">{String(index + 1).padStart(2, '0')}</span>
                <span className={`avatar avatar-${(index % 3) + 1}`}>{person.initials}</span>
                <span className="candidate-copy"><strong>{person.name}</strong><small>{person.title} · {person.years}</small></span>
                <span className={`score-orb ${person.tone}`}><strong>{person.score}</strong><small>分</small></span>
              </button>
            ))}
            {!filteredCandidates.length && <div className="empty-state compact"><Search size={20} /><strong>没有符合当前条件的候选人</strong><span>可降低匹配阈值或调整筛选条件</span></div>}
          </div>
        </section>
        <CandidateDetail person={selected} task={activeTask} selected={selectedCandidates.includes(selected.id)} onToggle={() => toggleCandidate(selected.id)} openDialog={openDialog} notify={notify} />
      </div>
    </>
  );
}

function CandidateDetail({ person, task, selected, onToggle, openDialog, notify }) {
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
              <blockquote>“{item.quote}” <button onClick={() => openDialog('resume', { person, highlight: item.label })}>查看简历原文</button></blockquote>
            </div>
          ))}
        </div>
      </div>
      <div className="fit-grid">
        <div><h3><CheckCircle2 size={16} />推荐依据</h3>{person.highlights.map((item) => <span key={item}>{item}</span>)}</div>
        <div><h3><CircleHelp size={16} />待核实项</h3>{person.risks.map((item) => <span key={item}>{item}</span>)}</div>
      </div>
      <div className="detail-section source-section">
        <div className="detail-title"><h3>画像对照来源</h3><button onClick={() => openDialog('scorecard')}>查看评分卡 <ArrowRight size={14} /></button></div>
        <button className="source-row" onClick={() => openDialog('knowledge', { item: task.role.includes('数据') ? knowledgeSeed[9] : knowledgeSeed[2] })}><BookOpenText size={17} /><span><strong>{task.role.includes('数据') ? '数据治理项目骨干成功特征分析' : '研发岗位高绩效人才特征分析'}</strong><small>人才画像 · 当前版本 · 引用 6 个特征</small></span><Eye size={15} /></button>
      </div>
      <div className="sticky-actions"><button className="btn secondary" onClick={() => { downloadText(`${person.name}-简历.txt`, buildResumeText(person)); notify('简历已下载'); }}><Download size={16} />下载简历</button><button className={classNames('btn', selected ? 'selected-button' : 'primary')} onClick={onToggle}>{selected ? <Check size={17} /> : <Plus size={17} />}{selected ? '已加入面试名单' : '加入面试名单'}</button></div>
    </section>
  );
}

function Interviews({ selectedCandidates, candidatePool, selectedCandidate, setSelectedCandidate, setView, notify, pushEvent, activeTask, interviewStatuses, setInterviewStatuses, updateActiveTask, openDialog }) {
  const interviewees = candidatePool.filter((item) => selectedCandidates.includes(item.id));
  function collect(person) {
    setInterviewStatuses((value) => ({ ...value, [person.id]: '已完成' }));
    pushEvent('收到在线面试结果', `${person.name} 已完成面试，评价报告生成完毕`, 'success');
    notify(`${person.name}的面试结果已回收`);
  }
  function remindAll() {
    const pending = interviewees.filter((person) => interviewStatuses[person.id] !== '已完成');
    if (!pending.length) return notify('当前批次已全部完成，无需提醒');
    pushEvent('发送面试提醒', `已向 ${pending.map((person) => person.name).join('、')} 发送在线面试提醒`, 'success');
    notify(`已提醒 ${pending.length} 位候选人`);
  }
  const completedCount = interviewees.filter((person) => interviewStatuses[person.id] === '已完成').length;
  const deadline = activeTask.interviewDeadline || '2026-07-25';
  const deadlineLabel = interviewees.length ? `${deadline.slice(5, 7)}月${deadline.slice(8, 10)}日` : '待创建';
  function openEvaluation(personId = selectedCandidate) {
    if (!completedCount) {
      notify('至少需要回收一份面试结果后才能生成综合评价');
      return;
    }
    const firstCompleted = interviewees.find((person) => interviewStatuses[person.id] === '已完成');
    const requested = interviewees.find((person) => person.id === personId && interviewStatuses[person.id] === '已完成');
    setSelectedCandidate((requested || firstCompleted).id);
    if (activeTask.stage !== '综合评价' && activeTask.stage !== '已完成') {
      updateActiveTask({ stage: '综合评价', progress: 86, tone: 'blue' });
      pushEvent('生成综合评价', `已汇总 ${completedCount} 位候选人的简历与面试证据`, 'success');
    }
    setView('evaluation');
  }
  return (
    <>
      <PageHeader eyebrow={`${activeTask.code} / 在线面试`} title="面试协同" description="智能体负责邀约、提醒、结果回收与结构化评价；招聘负责人掌握关键决策"
        actions={<button className="btn primary" disabled={!completedCount} title={completedCount ? '查看综合评价' : '至少完成一位候选人的面试后可用'} onClick={() => openEvaluation()}><BadgeCheck size={17} />查看综合评价</button>} />
      <div className="interview-overview">
        <div><span className="overview-icon blue"><Send size={19} /></span><span><small>已发送邀请</small><strong>{interviewees.length}</strong></span></div>
        <div><span className="overview-icon green"><CheckCircle2 size={19} /></span><span><small>已完成</small><strong>{completedCount}</strong></span></div>
        <div><span className="overview-icon amber"><Clock3 size={19} /></span><span><small>待作答</small><strong>{interviewees.length - completedCount}</strong></span></div>
        <div><span className="overview-icon gray"><CalendarDays size={19} /></span><span><small>最晚完成</small><strong className="date-strong">{deadlineLabel}</strong></span></div>
      </div>
      <section className="interview-list">
        <div className="panel-heading"><div><span className="section-kicker">在线面试批次</span><h2>{interviewees.length ? `${activeTask.role} · 第一批次` : '尚未创建面试批次'}</h2></div><button className="btn secondary" disabled={!interviewees.length || completedCount === interviewees.length} title={!interviewees.length ? '请先确认面试候选人' : completedCount === interviewees.length ? '当前批次已全部完成' : '提醒未完成候选人'} onClick={remindAll}><Send size={16} />批量提醒</button></div>
        {interviewees.map((person, index) => {
          const done = interviewStatuses[person.id] === '已完成';
          return (
            <article className="interview-card" key={person.id}>
              <div className="interview-person"><span className={`avatar avatar-${index + 1}`}>{person.initials}</span><span><strong>{person.name}</strong><small>{person.title}</small></span></div>
              <div className="interview-schedule"><span><CalendarDays size={15} />邀请时间</span><strong>2026-07-{22 - index} 14:30</strong></div>
              <div className="interview-schedule"><span><Clock3 size={15} />答题时长</span><strong>{done ? '26 分 18 秒' : '限时 40 分钟'}</strong></div>
              <div><StatusPill tone={done ? 'green' : 'amber'}>{done ? '评价已生成' : '等待候选人'}</StatusPill></div>
              <div className="interview-action">
                {done ? <button className="btn secondary" onClick={() => openEvaluation(person.id)}><Eye size={16} />查看评价</button> : <button className="btn secondary" onClick={() => collect(person)}><Play size={16} />模拟完成</button>}
                <button className="icon-button small" title="面试操作" onClick={() => openDialog('interview-actions', { person, done })}><MoreHorizontal size={17} /></button>
              </div>
            </article>
          );
        })}
        {!interviewees.length && <div className="empty-state interview-empty"><UsersRound size={24} /><strong>尚未选择面试候选人</strong><span>请先在人才匹配页面确认候选名单。</span><button className="btn primary" onClick={() => setView('talent')}>前往人才匹配</button></div>}
      </section>
      <section className="guardrail-band"><LockKeyhole size={20} /><div><strong>智能体执行边界</strong><p>邀请发送、状态提醒和结果回收可自动完成；面试评价仅作为决策参考，进入下一轮与最终录用必须由招聘负责人确认。</p></div><button className="text-button" onClick={() => openDialog('rules')}>查看规则</button></section>
    </>
  );
}

function Evaluation({ setView, notify, pushEvent, activeTask, candidatePool, selectedCandidate, setSelectedCandidate, selectedCandidates, interviewStatuses, evaluationDecisions, setEvaluationDecisions, updateActiveTask, openDialog }) {
  const eligible = candidatePool.filter((item) => selectedCandidates.includes(item.id) && interviewStatuses[item.id] === '已完成');
  const person = eligible.find((item) => item.id === selectedCandidate) || eligible[0];
  if (!person) {
    return <><PageHeader eyebrow={`${activeTask.code} / 综合评价`} title="候选人综合评价" description="面试结果回收后，智能体将在这里生成可解释的综合评价" /><section className="empty-state interview-empty evaluation-empty"><BadgeCheck size={24} /><strong>暂无可评价的候选人</strong><span>请先完成至少一位候选人的在线面试。</span><button className="btn primary" onClick={() => setView('interviews')}>返回面试协同</button></section></>;
  }
  const decisionKey = `${activeTask.code}-${person.id}`;
  const decision = evaluationDecisions[decisionKey];
  const resumeScore = person.score;
  const interviewScore = Math.max(75, person.score - 4);
  const assessmentScore = Math.max(75, person.score - 3);
  const finalScore = Math.round(resumeScore * 0.4 + interviewScore * 0.35 + assessmentScore * 0.25);
  const advice = finalScore >= 88 ? '建议进入下一轮' : finalScore >= 80 ? '建议复核后推进' : '建议重点复核';
  function decide(result) {
    setEvaluationDecisions((items) => ({ ...items, [decisionKey]: result }));
    const allResolved = selectedCandidates.every((id) => interviewStatuses[id] === '已完成' && (id === person.id || evaluationDecisions[`${activeTask.code}-${id}`]));
    updateActiveTask({ stage: allResolved ? '已完成' : '综合评价', progress: allResolved ? 100 : 86, tone: allResolved ? 'green' : 'blue' });
    pushEvent('综合评价已确认', `招聘经理确认 ${person.name}：${result}`, 'human');
    notify(allResolved ? '本批次评价已全部确认，任务已完成' : `${person.name}的评价结论已保存`);
  }
  function exportReport() {
    downloadText(`${activeTask.code}-${person.name}-综合评价报告.txt`, [`${activeTask.role} · 候选人综合评价报告`, `候选人：${person.name}`, `综合评分：${finalScore} / 100`, '', `简历匹配：${resumeScore} / 100`, `AI在线面试：${interviewScore} / 100`, `能力测评：${assessmentScore} / 100`, '', '综合结论', `${person.highlights.join('、')}等证据与岗位要求匹配；${person.risks.join('、')}需要在下一轮进一步核实。`, '', `决策状态：${decision || '待招聘负责人确认'}`].join('\n'));
    notify('综合评价报告已导出');
  }
  return (
    <>
      <PageHeader eyebrow={`${activeTask.code} / 综合评价`} title="候选人综合评价" description="汇总简历匹配、在线面试与人工评价，结论可解释、可复核"
        actions={<><button className="btn secondary" onClick={exportReport}><Download size={16} />导出报告</button><button className="btn primary" onClick={() => decide('进入下一轮')}>{decision === '进入下一轮' ? <Check size={17} /> : <UserCheck size={17} />}{decision === '进入下一轮' ? '已确认进入下一轮' : '确认进入下一轮'}</button></>} />
      {eligible.length > 1 && <div className="evaluation-candidates">{eligible.map((item) => <button className={item.id === person.id ? 'active' : ''} onClick={() => setSelectedCandidate(item.id)} key={item.id}><span className="avatar">{item.initials}</span><span><strong>{item.name}</strong><small>{item.score} 分</small></span></button>)}</div>}
      <section className="evaluation-hero">
        <div className="eval-person"><span className="avatar avatar-large">{person.initials}</span><div><h2>{person.name}</h2><p>{person.title} · {person.company}</p><span>候选人编号 C2026-{String(person.id).padStart(5, '0')}</span></div></div>
        <div className="final-score"><span>综合建议</span><strong>{finalScore}<small>/100</small></strong><StatusPill tone={finalScore >= 88 ? 'green' : 'amber'}>{advice}</StatusPill></div>
        <div className="eval-conclusion"><Sparkles size={19} /><p>{person.highlights.join('、')}等能力证据与岗位要求匹配。建议下一轮重点核实：{person.risks.join('、')}。</p></div>
      </section>
      <div className="evaluation-grid">
        <section className="panel score-breakdown">
          <div className="panel-heading"><div><span className="section-kicker">分项结果</span><h2>独立评分构成</h2></div><span className="evidence-badge"><ShieldCheck size={14} />证据覆盖率 96%</span></div>
          {[
            ['简历匹配', resumeScore, '岗位评分卡 v3.2', '40%', person.evidence[0]?.quote],
            ['AI在线面试', interviewScore, '结构化面试题库 v2.1', '35%', person.evidence[1]?.quote],
            ['能力测评', assessmentScore, '研发能力测评 v1.6', '25%', person.evidence[2]?.quote],
          ].map(([label, score, source, weight, quote]) => (
            <div className="breakdown-row" key={label}><span className="breakdown-score">{score}</span><div><strong>{label}</strong><small>{source} · 权重 {weight}</small><i><b style={{ width: `${score}%` }} /></i></div><button onClick={() => openDialog('evidence', { label, score, source, quote })}><Eye size={16} />查看证据</button></div>
          ))}
        </section>
        <aside className="panel review-panel">
          <div className="panel-heading"><div><span className="section-kicker">风险与复核</span><h2>需要人工关注</h2></div></div>
          <div className="review-item amber"><CircleHelp size={18} /><div><strong>管理意愿尚未充分验证</strong><p>候选人具有带教经历，但未表达明确的团队管理意愿。</p><span>建议业务面试追问</span></div></div>
          <div className="review-item green"><CheckCircle2 size={18} /><div><strong>未发现硬性风险</strong><p>资质、履历一致性与基础合规检查均通过。</p></div></div>
          <div className="decision-options"><span>招聘负责人结论</span><div><button className={decision === '进入下一轮' ? 'active' : ''} onClick={() => decide('进入下一轮')}><Check size={15} />进入下一轮</button><button className={decision === '保留复核' ? 'active amber' : ''} onClick={() => decide('保留复核')}><CircleHelp size={15} />保留复核</button><button className={decision === '不进入下一轮' ? 'active red' : ''} onClick={() => decide('不进入下一轮')}><X size={15} />不进入</button></div></div>
        </aside>
      </div>
      <section className="interview-evidence">
        <div className="panel-heading"><div><span className="section-kicker">在线面试</span><h2>关键回答与评价证据</h2></div><button className="btn secondary" onClick={() => openDialog('transcript', { person })}><Video size={16} />查看完整面试</button></div>
        <div className="qa-row"><span className="qa-index">01</span><div><strong>请介绍一次你主导的复杂项目，以及你的关键决策。</strong><blockquote>“{person.evidence[0]?.quote}”</blockquote></div><span className="qa-score">{interviewScore}<small>表达与证据</small></span></div>
        <div className="qa-row"><span className="qa-index">02</span><div><strong>项目推进遇到风险时，你如何识别并解决？</strong><blockquote>“{person.evidence[1]?.quote}”</blockquote></div><span className="qa-score">{Math.max(75, interviewScore - 3)}<small>判断与协作</small></span></div>
      </section>
    </>
  );
}

function Knowledge({ knowledge, setModalOpen, openDialog }) {
  const [type, setType] = useState('全部资料');
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('全部状态');
  const [sort, setSort] = useState('recent');
  const [showArchived, setShowArchived] = useState(false);
  const tabs = ['全部资料', '岗位知识', '人才画像', '制度流程'];
  const activeKnowledge = knowledge.filter((item) => !item.archived);
  const listSource = knowledge.filter((item) => showArchived ? item.archived : !item.archived);
  const filtered = useMemo(() => listSource.filter((item) => (type === '全部资料' || item.type === type) && (status === '全部状态' || item.status === status) && `${item.title}${item.owner}${item.type}${(item.tags || []).join('')}`.toLowerCase().includes(query.trim().toLowerCase())).sort((a, b) => sort === 'refs' ? b.refs - a.refs : b.updated.localeCompare(a.updated)), [listSource, type, query, status, sort]);
  const counts = {
    岗位知识: activeKnowledge.filter((i) => i.type === '岗位知识').length,
    人才画像: activeKnowledge.filter((i) => i.type === '人才画像').length,
    制度流程: activeKnowledge.filter((i) => i.type === '制度流程').length,
  };
  const listCounts = {
    岗位知识: listSource.filter((i) => i.type === '岗位知识').length,
    人才画像: listSource.filter((i) => i.type === '人才画像').length,
    制度流程: listSource.filter((i) => i.type === '制度流程').length,
  };
  const reviewCount = activeKnowledge.filter((item) => item.status !== '可用').length;
  const health = Math.max(0, 100 - reviewCount * 3);
  return (
    <>
      <PageHeader eyebrow="智能体管理" title="企业招聘知识库" description="沉淀企业岗位标准、人才成功特征与招聘制度，为智能体提供可信依据"
        actions={<button className="btn primary" onClick={() => setModalOpen(true)}><Plus size={17} />新增知识</button>} />
      <section className="knowledge-overview">
        <div className="knowledge-stat"><span className="knowledge-icon role"><BriefcaseBusiness size={20} /></span><span><small>岗位知识</small><strong>{counts.岗位知识}<em> 份资料</em></strong><i>覆盖 18 个岗位族</i></span></div>
        <div className="knowledge-stat"><span className="knowledge-icon portrait"><UsersRound size={20} /></span><span><small>人才画像</small><strong>{counts.人才画像}<em> 份资料</em></strong><i>关联 326 条任职结果</i></span></div>
        <div className="knowledge-stat"><span className="knowledge-icon policy"><ShieldCheck size={20} /></span><span><small>制度流程</small><strong>{counts.制度流程}<em> 份资料</em></strong><i>7 项规则正在生效</i></span></div>
        <div className="knowledge-health"><span><BadgeCheck size={19} />知识健康度</span><strong>{health}%</strong><i><b style={{ width: `${health}%` }} /></i><small>{reviewCount} 份资料建议复核</small></div>
      </section>
      <section className="knowledge-workspace">
        <div className="knowledge-tabs" role="tablist">
          {tabs.map((tab) => <button role="tab" aria-selected={type === tab} className={type === tab ? 'active' : ''} onClick={() => setType(tab)} key={tab}>{tab}<span>{tab === '全部资料' ? listSource.length : listCounts[tab]}</span></button>)}
        </div>
        <div className="knowledge-toolbar"><label className="search-field"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索知识标题、部门或标签" /></label><label className="select-button"><ListFilter size={16} /><select value={status} onChange={(event) => setStatus(event.target.value)}><option>全部状态</option><option>可用</option><option>待复核</option><option>停用</option></select><ChevronDown size={14} /></label><label className="select-button"><History size={16} /><select value={sort} onChange={(event) => setSort(event.target.value)}><option value="recent">最近更新</option><option value="refs">引用次数</option></select><ChevronDown size={14} /></label><button className={classNames('btn secondary', showArchived && 'is-active')} onClick={() => { setShowArchived((value) => !value); setType('全部资料'); }}><Archive size={16} />{showArchived ? '返回知识库' : '查看归档'}</button></div>
        <div className="data-table knowledge-table">
          <div className="table-row table-head"><span>资料名称</span><span>知识分类</span><span>版本与状态</span><span>维护部门</span><span>智能体引用</span><span>更新时间</span><span /></div>
          {filtered.map((item) => (
            <button className="table-row" key={item.id} onClick={() => openDialog('knowledge', { item })}>
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
      <section className="governance-note"><ShieldCheck size={20} /><div><strong>知识治理规则已启用</strong><p>上传资料将经过敏感信息识别、重复内容检测与人工复核；人才画像不会使用年龄、性别、婚育等敏感属性。</p></div><button className="text-button" onClick={() => openDialog('governance')}>查看治理规则 <ArrowRight size={15} /></button></section>
    </>
  );
}

function KnowledgeModal({ onClose, onSubmit }) {
  const [form, setForm] = useState({ type: '岗位知识', title: '', owner: '组织人事部', format: 'DOCX', description: '', tags: '', fileName: '' });
  function useFile(file) {
    if (!file) return;
    const extension = file.name.split('.').pop()?.toUpperCase() || 'DOCX';
    setForm((current) => ({ ...current, title: current.title || file.name.replace(/\.[^.]+$/, ''), format: extension, fileName: file.name }));
  }
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
        <label><span>资料说明</span><textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="说明资料的适用岗位、包含内容或使用边界" /></label>
        <label><span>业务标签</span><input value={form.tags} onChange={(e) => setForm({ ...form, tags: e.target.value })} placeholder="例如：研发岗位，任职标准，面试题" /></label>
        <label><span>知识文件</span><div className={classNames('upload-zone', form.fileName && 'has-file')} onDragOver={(event) => event.preventDefault()} onDrop={(event) => { event.preventDefault(); useFile(event.dataTransfer.files?.[0]); }}><UploadCloud size={25} /><strong>{form.fileName || '选择文件或拖放到此处'}</strong><small>{form.fileName ? `${form.format} · 已准备解析` : '支持 PDF、Word、Excel、PPT，单个文件不超过 50MB'}</small><input type="file" accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx" onChange={(event) => useFile(event.target.files?.[0])} /></div></label>
        <div className="modal-note"><ShieldCheck size={17} /><span>资料上传后不会立即用于智能体决策，需完成解析与人工复核。</span></div>
        <div className="modal-actions"><button type="button" className="btn secondary" onClick={onClose}>取消</button><button type="submit" className="btn primary"><UploadCloud size={16} />上传并解析</button></div>
      </form>
    </div>
  );
}

function Audit({ events, openDialog, notify, activeTask }) {
  const [query, setQuery] = useState('');
  const [type, setType] = useState('全部类型');
  const [date, setDate] = useState('全部时间');
  const [scope, setScope] = useState('当前任务');
  const today = localDateString();
  const scopedEvents = scope === '当前任务' ? events.filter((event) => event.taskId === activeTask.code) : events;
  const filteredEvents = useMemo(() => scopedEvents.filter((event) => (type === '全部类型' || (type === '人工操作' ? event.type === 'human' : event.type !== 'human')) && `${event.title}${event.detail}${event.actor || ''}${event.taskId || ''}`.toLowerCase().includes(query.trim().toLowerCase())).filter((event) => date === '全部时间' || event.date === today), [scopedEvents, query, type, date, today]);
  const automaticCount = scopedEvents.filter((event) => event.type !== 'human').length;
  const humanCount = scopedEvents.filter((event) => event.type === 'human').length;
  const knowledgeCount = scopedEvents.filter((event) => `${event.title}${event.detail}`.includes('知识')).length;
  function exportAudit() {
    const rows = [['日期', '时间', '任务', '类型', '执行主体', '动作', '结果'], ...filteredEvents.map((event) => [event.date || '2026-07-22', event.time, event.taskId || '-', event.type === 'human' ? '人工操作' : '自动执行', event.actor || '招聘执行智能体', event.title, event.detail])];
    downloadText(`招聘智能体审计日志-${today}.csv`, rows.map((row) => row.map((value) => `"${String(value).replaceAll('"', '""')}"`).join(',')).join('\n'), 'text/csv;charset=utf-8');
    notify('审计日志已导出');
  }
  return (
    <>
      <PageHeader eyebrow="智能体管理" title="运行审计" description="查看智能体的执行步骤、知识引用、人工决策和系统写入记录"
        actions={<button className="btn secondary" onClick={exportAudit}><Download size={16} />导出审计日志</button>} />
      <section className="audit-summary">
        <div><Bot size={20} /><span><small>智能体执行</small><strong>{automaticCount} 次</strong></span></div>
        <div><UserCheck size={20} /><span><small>人工确认</small><strong>{humanCount} 次</strong></span></div>
        <div><BookOpenText size={20} /><span><small>知识引用</small><strong>{knowledgeCount} 次</strong></span></div>
        <div><ShieldCheck size={20} /><span><small>规则拦截</small><strong>0 次</strong></span></div>
      </section>
      <section className="audit-workspace">
        <div className="knowledge-toolbar"><label className="search-field"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索动作、操作者或任务" /></label><label className="select-button"><BriefcaseBusiness size={16} /><select value={scope} onChange={(event) => setScope(event.target.value)}><option>当前任务</option><option>全部任务</option></select><ChevronDown size={14} /></label><label className="select-button"><CalendarDays size={16} /><select value={date} onChange={(event) => setDate(event.target.value)}><option>全部时间</option><option>今天</option></select><ChevronDown size={14} /></label><label className="select-button"><ListFilter size={16} /><select value={type} onChange={(event) => setType(event.target.value)}><option>全部类型</option><option>自动执行</option><option>人工操作</option></select><ChevronDown size={14} /></label></div>
        <div className="audit-context"><div><span className="agent-orbit small"><Bot size={19} /></span><span><strong>{activeTask.role}招聘</strong><small>任务 {activeTask.code} · 招聘执行智能体</small></span></div><StatusPill tone={activeTask.stage === '已完成' ? 'green' : 'blue'}>{activeTask.stage === '已完成' ? '已完成' : '执行中'}</StatusPill></div>
        <div className="audit-timeline">
          {filteredEvents.map((event, index) => (
            <div className="audit-event" key={`${event.time}-${index}`}>
              <time>{event.date === today ? '今天' : event.date?.slice(5)}<br /><strong>{event.time}</strong></time>
              <span className={classNames('audit-dot', event.type)}>{event.type === 'human' ? <UserCheck size={14} /> : <Check size={14} />}</span>
              <div><div className="audit-event-title"><strong>{event.title}</strong><StatusPill tone={event.type === 'human' ? 'blue' : 'green'}>{event.type === 'human' ? '人工操作' : '自动执行'}</StatusPill></div><p>{event.detail}</p><div className="audit-meta"><span><Bot size={13} />{event.actor || '招聘执行智能体'}</span><span><ShieldCheck size={13} />策略校验通过</span><button onClick={() => openDialog('audit', { event })}><Eye size={14} />查看输入输出</button></div></div>
            </div>
          ))}
          {!filteredEvents.length && <div className="empty-state compact"><Activity size={20} /><strong>没有符合条件的审计记录</strong><span>请调整检索词或筛选条件</span></div>}
        </div>
      </section>
    </>
  );
}

export default App;
