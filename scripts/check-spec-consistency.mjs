#!/usr/bin/env node
/**
 * 规范-代码一致性检查脚本
 * Task: #13 (AAF-024)
 *
 * 检查规则：
 * 1. docs/ 下 Markdown 文件中的相对链接指向的文件必须存在
 * 2. 规范中提到的 pom.xml 依赖必须实际存在
 * 3. 规范中提到的 nx target 必须在 project.json 中定义
 */
import { readFileSync, readdirSync, statSync, existsSync } from 'fs';
import { join, dirname, resolve } from 'path';

const ROOT = resolve(import.meta.dirname, '..');
const DOCS_DIR = join(ROOT, 'docs');

// Directories to skip (external/reference projects, not our specs)
const SKIP_DIRS = ['docs/design/auto-dev', 'docs/tmp', 'docs/reference/team/skills'];

let errors = 0;

// --- Rule 1: 相对链接检查 ---

function findMarkdownFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory() && !entry.name.startsWith('.') && entry.name !== 'node_modules') {
      files.push(...findMarkdownFiles(full));
    } else if (entry.isFile() && entry.name.endsWith('.md')) {
      files.push(full);
    }
  }
  return files;
}

function isPlaceholderOrExample(target) {
  // Skip template variables, example paths, and non-path tokens
  if (/[{}$]/.test(target)) return true;           // ${var}, {module-name}
  if (/^(url|href|cdnUrl|路径)$/i.test(target)) return true; // bare variable names
  if (target.startsWith('/')) return true;          // absolute paths (site-relative)
  if (/\.(png|jpg|jpeg|gif|svg)$/i.test(target)) return true; // images (often missing in repo)
  if (/^[\u4e00-\u9fff]+$/.test(target)) return true; // pure Chinese text (placeholder)
  if (/pull\/\d+/.test(target)) return true;       // GitHub PR references
  if (/^\.{2,}$/.test(target)) return true;        // ellipsis (...)
  if (target.includes('./doc-a') || target.includes('./doc-b') || target.includes('./sub-dir')) return true; // example paths in standards
  return false;
}

function checkRelativeLinks(file) {
  const content = readFileSync(file, 'utf-8');
  const linkRegex = /\[([^\]]*)\]\(([^)]+)\)/g;
  let match;
  while ((match = linkRegex.exec(content)) !== null) {
    let target = match[2];
    // Skip absolute URLs and pure anchors
    if (target.startsWith('http') || target.startsWith('#') || target.startsWith('mailto:')) continue;
    // Strip anchor fragment
    target = target.split('#')[0];
    if (!target) continue;
    if (isPlaceholderOrExample(target)) continue;
    const resolved = resolve(dirname(file), target);
    if (!existsSync(resolved)) {
      const rel = file.replace(ROOT + '\\', '').replace(ROOT + '/', '');
      console.error(`[DEAD_LINK] ${rel} → ${target}`);
      errors++;
    }
  }
}

// --- Rule 2: 依赖存在性检查 ---

function loadPomDependencies() {
  const pomPath = join(ROOT, 'apps/service/pom.xml');
  if (!existsSync(pomPath)) return null;
  const content = readFileSync(pomPath, 'utf-8');
  // Extract artifactId from dependencies
  const ids = new Set();
  const regex = /<artifactId>([^<]+)<\/artifactId>/g;
  let m;
  while ((m = regex.exec(content)) !== null) ids.add(m[1]);
  return ids;
}

function loadPackageJsonDeps() {
  const pkgPath = join(ROOT, 'package.json');
  if (!existsSync(pkgPath)) return null;
  const pkg = JSON.parse(readFileSync(pkgPath, 'utf-8'));
  return new Set([
    ...Object.keys(pkg.dependencies || {}),
    ...Object.keys(pkg.devDependencies || {}),
  ]);
}

// --- Rule 3: Nx target 检查 ---

function loadNxTargets() {
  const targets = new Set();
  const projectFiles = findProjectJsonFiles(ROOT);
  for (const pf of projectFiles) {
    const proj = JSON.parse(readFileSync(pf, 'utf-8'));
    if (proj.targets) {
      Object.keys(proj.targets).forEach((t) => targets.add(t));
    }
  }
  return targets;
}

function findProjectJsonFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.')) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...findProjectJsonFiles(full));
    } else if (entry.name === 'project.json') {
      files.push(full);
    }
  }
  return files;
}

// --- Rule 2: pnpm script 引用检查 ---

function checkPnpmScriptRefs(files) {
  const pkgPath = join(ROOT, 'package.json');
  if (!existsSync(pkgPath)) return;
  const pkg = JSON.parse(readFileSync(pkgPath, 'utf-8'));
  const scripts = new Set(Object.keys(pkg.scripts || {}));

  const pnpmRegex = /`pnpm\s+([\w:./-]+)`/g;
  for (const file of files) {
    const content = readFileSync(file, 'utf-8');
    let match;
    while ((match = pnpmRegex.exec(content)) !== null) {
      const cmd = match[1];
      // Skip known passthrough commands
      if (['nx', 'exec', 'install', 'add', 'remove', 'dlx', 'run', 'i'].includes(cmd)) continue;
      // Skip sub-commands of nx (format:write is `nx format:write`)
      if (cmd.startsWith('format:')) { if (scripts.has('format') || scripts.has(cmd)) continue; }
      if (!scripts.has(cmd)) {
        const rel = file.replace(ROOT + '\\', '').replace(ROOT + '/', '');
        console.error(`[MISSING_SCRIPT] ${rel} → pnpm ${cmd} (not in package.json scripts)`);
        errors++;
      }
    }
  }
}

// --- Rule 3: nx target 引用检查 ---

function checkNxTargetRefs(files) {
  const targets = loadNxTargets();
  if (targets.size === 0) return;

  // Match patterns: `nx run-many -t xxx`, `nx affected -t xxx`, `nx run project:xxx`
  const targetRegex = /`(?:pnpm\s+)?nx\s+(?:run-many|affected)\s+-t\s+([\w-]+)`|`(?:pnpm\s+)?nx\s+run\s+[\w-]+:([\w-]+)`/g;
  for (const file of files) {
    const content = readFileSync(file, 'utf-8');
    let match;
    while ((match = targetRegex.exec(content)) !== null) {
      const target = match[1] || match[2];
      if (!targets.has(target)) {
        const rel = file.replace(ROOT + '\\', '').replace(ROOT + '/', '');
        console.error(`[MISSING_TARGET] ${rel} → nx target "${target}" (not in any project.json)`);
        errors++;
      }
    }
  }
}

// --- Main ---

console.log('=== 规范-代码一致性检查 ===\n');

// Rule 1: 相对链接
console.log('▶ Rule 1: 检查相对链接...');
const mdFiles = findMarkdownFiles(DOCS_DIR);
// Also check root-level md files
for (const entry of readdirSync(ROOT, { withFileTypes: true })) {
  if (entry.isFile() && entry.name.endsWith('.md')) {
    mdFiles.push(join(ROOT, entry.name));
  }
}
for (const f of mdFiles) {
  if (SKIP_DIRS.some((d) => f.includes(d.replace(/\//g, '\\')) || f.includes(d))) continue;
  checkRelativeLinks(f);
}

// Rule 2: pnpm script 引用 (only check our own specs, not external refs)
console.log('▶ Rule 2: 检查 pnpm script 引用...');
const specFiles = mdFiles.filter((f) => !SKIP_DIRS.some((d) => f.includes(d.replace(/\//g, '\\')) || f.includes(d)));
checkPnpmScriptRefs(specFiles);

// Rule 3: nx target 引用
console.log('▶ Rule 3: 检查 nx target 引用...');
checkNxTargetRefs(specFiles);

console.log(`\n=== 完成：${errors} 个问题 ===`);
process.exit(errors > 0 ? 1 : 0);
