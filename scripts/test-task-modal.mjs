import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

const appSource = await readFile(new URL('../src/App.jsx', import.meta.url), 'utf8');

function assertSource(pattern, message) {
  assert.match(appSource, pattern, message);
}

function openingButtonTags(source) {
  const tags = [];
  let start = source.indexOf('<button');
  while (start >= 0) {
    let quote = '';
    let braceDepth = 0;
    let end = start + '<button'.length;
    for (; end < source.length; end += 1) {
      const character = source[end];
      if (quote) {
        if (character === quote && source[end - 1] !== '\\') quote = '';
        continue;
      }
      if (character === '"' || character === "'") {
        quote = character;
        continue;
      }
      if (character === '{') braceDepth += 1;
      else if (character === '}') braceDepth -= 1;
      else if (character === '>' && braceDepth === 0) break;
    }
    tags.push({
      line: source.slice(0, start).split('\n').length,
      tag: source.slice(start, end + 1).replace(/\s+/g, ' '),
    });
    start = source.indexOf('<button', end + 1);
  }
  return tags;
}

const buttonTags = openingButtonTags(appSource);
const buttonsWithoutAction = buttonTags.filter(({ tag }) => (
  !/\bonClick\s*=/.test(tag) && !/\btype\s*=\s*['"]submit['"]/.test(tag)
));

assert.deepEqual(
  buttonsWithoutAction,
  [],
  `Every button must have an onClick handler or submit semantics:\n${buttonsWithoutAction
    .map(({ line, tag }) => `line ${line}: ${tag}`)
    .join('\n')}`,
);

assertSource(
  /<Sidebar\s+view=\{view\}\s+setView=\{requestView\}/,
  'Sidebar navigation must pass through the guarded view dispatcher.',
);
assertSource(
  /<Topbar\s+setView=\{requestView\}/,
  'Topbar navigation must pass through the guarded view dispatcher.',
);
assertSource(
  /function requestView\(target\)[\s\S]*?notify\([^)]*请先[^)]*\);[\s\S]*?return;[\s\S]*?setView\(target\);/,
  'Guarded navigation must explain blocked workflow transitions.',
);
assertSource(
  /<EmbedGate\s+status=\{embedStatus\}\s+onOpenStandalone=\{openStandalone\}/,
  'A directly opened embed must offer a working standalone entry.',
);

const criticalEntrypoints = [
  ['new task', /新建招聘任务<\/button>/, /onClick=\{\(\) => setTaskModalOpen\(true\)\}/],
  ['global search', /className="command-search"\s+onClick=\{onSearch\}/, /placeholder="搜索任务、候选人或知识资料"/],
  ['role plan save', /disabled=\{planSaving\}\s+onClick=\{savePlan\}/, /岗位方案尚未确认/],
  ['G3 match', /onClick=\{\(\) => parsedResumeFiles\.length \? runServiceMatch\(false\) : setResumeLibraryOpen\(true\)\}/, /G3 匹配未完成/],
  ['resume library', /onClick=\{\(\) => setResumeLibraryOpen\(true\)\}/, /title="独立简历库" eyebrow="真实候选输入"/],
  ['candidate confirmation', /onClick=\{confirmSelection\}/, /名单确认与推荐报告尚未后端化/],
  ['interview reservation', /view === 'interviews' && <ReservedCapability type="interview"/, /不发送邀请，不调用消息或在线面试平台/],
  ['evaluation reservation', /view === 'evaluation' && <ReservedCapability type="evaluation"/, /不生成虚构面试分或测评分/],
  ['knowledge creation', /onClick=\{\(\) => setModalOpen\(true\)\}[\s\S]{0,120}新增知识/, /文件已上传，等待服务端解析|服务不可用，已保存本地草稿/],
  ['audit export', /onClick=\{exportAudit\}[\s\S]{0,100}导出审计日志/, /审计日志已导出/],
];

for (const [name, handlerPattern, feedbackPattern] of criticalEntrypoints) {
  assertSource(handlerPattern, `${name} must bind its primary action.`);
  assertSource(feedbackPattern, `${name} must expose visible feedback or an explanatory state.`);
}

assert.doesNotMatch(appSource, /模拟完成|发送面试提醒|interviewScore|assessmentScore/, 'Reserved interview and evaluation capabilities must not retain simulated execution logic.');

assertSource(
  /const serviceDraftReady[\s\S]*?const canCreate[\s\S]*?serviceDraftReady/,
  'Task creation must stay disabled until the service draft is ready.',
);
assertSource(
  /服务端草案尚未满足创建条件[\s\S]*?创建按钮会自动解锁/,
  'A disabled service draft must explain how the user can continue.',
);
assertSource(
  /getCandidates\(\{ \.\.\.activeTask, serviceMatchResults: null \}\)/,
  'G3 must generate demo inputs before service results exist instead of treating an empty array as a completed empty run.',
);
assertSource(
  /serviceMatchEmpty[\s\S]*?上次运行没有产生推荐结果[\s\S]*?runServiceMatch\(false\)/,
  'An empty G3 run must offer a visible retry path.',
);
assertSource(
  /if \(activeTask\.stage !== '人才搜索' \|\| serviceTask\) return undefined;/,
  'Service tasks must never use the local timer to fake a completed talent search.',
);
assert.doesNotMatch(
  appSource,
  /fixtures\[index\]|\|\| candidatesSeed\[0\]/,
  'G3 results must never fall back to a positional or seeded candidate fixture.',
);
assertSource(
  /fixtures\.find\(\(person\) => person\.candidateId === result\.candidate\.id\)[\s\S]{0,100}\|\| unmatchedServiceCandidate\(result\)/,
  'G3 results must use a resume-free placeholder when candidate identity cannot be resolved.',
);
assertSource(
  /evidenceKind: hasSourceEvidence \? 'SOURCE' : 'SYSTEM'/,
  'G3 evidence mapping must distinguish source evidence from system assessment.',
);
assertSource(
  /sourceEvidenceCount[\s\S]{0,180}项有原文证据/,
  'Candidate details must report the actual source-evidence count.',
);
assert.doesNotMatch(
  appSource,
  /全部结论均有原文证据/,
  'Candidate details must not claim that every conclusion has source evidence.',
);

const server = await createServer({
  appType: 'custom',
  logLevel: 'silent',
  server: { middlewareMode: true },
});

try {
  const { TaskModal } = await server.ssrLoadModule('/src/App.jsx');
  const markup = renderToStaticMarkup(React.createElement(TaskModal, {
    getAccessToken: () => null,
    onClose: () => {},
    onSubmit: () => {},
  }));

  assert.match(markup, /对话创建招聘任务/);
  assert.match(markup, /智能体服务/);
  assert.match(markup, /发送需求后连接/);
  assert.match(markup, /确认并生成岗位方案/);
  assert.match(markup, /disabled=""/);
  console.log(`Task modal and ${buttonTags.length} clickable UI entrypoints passed.`);
} finally {
  await server.close();
}
