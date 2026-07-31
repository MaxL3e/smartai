import { access, readdir, readFile } from 'node:fs/promises';
import { dirname, extname, resolve } from 'node:path';

const root = resolve(import.meta.dirname, '..');

async function markdownFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const nested = await Promise.all(entries.map(async (entry) => {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) return markdownFiles(path);
    return extname(entry.name).toLowerCase() === '.md' ? [path] : [];
  }));
  return nested.flat();
}

const files = [resolve(root, 'README.md'), ...await markdownFiles(resolve(root, 'docs'))];
const failures = [];

for (const file of files) {
  const source = await readFile(file, 'utf8');
  const fenceCount = (source.match(/^```/gm) || []).length;
  if (fenceCount % 2 !== 0) failures.push(`${file}: unbalanced fenced code blocks`);

  const linkPattern = /\[[^\]]*\]\(([^)]+)\)/g;
  for (const match of source.matchAll(linkPattern)) {
    const rawTarget = match[1].trim().replace(/^<|>$/g, '');
    const target = rawTarget.split('#')[0];
    if (!target || /^(https?:|mailto:)/i.test(target)) continue;
    const resolvedTarget = resolve(dirname(file), decodeURIComponent(target));
    try {
      await access(resolvedTarget);
    } catch {
      failures.push(`${file}: missing local link target ${rawTarget}`);
    }
  }
}

if (failures.length) {
  console.error(failures.join('\n'));
  process.exitCode = 1;
} else {
  console.log(`Documentation validation passed for ${files.length} Markdown files.`);
}
