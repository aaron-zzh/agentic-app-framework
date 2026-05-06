const fs = require('fs');
const path = require('path');

const MAX_SIZE = 1024 * 1024; // 1MB
const agentName = process.argv[2] || 'unknown';
const input = JSON.parse(fs.readFileSync(0, 'utf8'));
const logsDir = path.join(input.cwd, 'logs');
const logFile = path.join(logsDir, `${agentName}.log`);

fs.mkdirSync(logsDir, { recursive: true });

if (fs.existsSync(logFile) && fs.statSync(logFile).size > MAX_SIZE) {
  const ts = new Date().toISOString().replace(/[:.]/g, '-');
  fs.renameSync(logFile, path.join(logsDir, `${agentName}.${ts}.log`));
}

const date = new Date().toISOString();
fs.appendFileSync(logFile, `\n--- ${date} ---\n${input.assistant_response}\n`, 'utf8');
