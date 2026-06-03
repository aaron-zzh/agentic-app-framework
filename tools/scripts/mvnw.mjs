#!/usr/bin/env node

import { spawnSync } from "node:child_process"
import { existsSync } from "node:fs"
import { join } from "node:path"

const cwd = process.cwd()
const wrapper = process.platform === "win32" ? "mvnw.cmd" : "mvnw"
const wrapperPath = join(cwd, wrapper)

if (!existsSync(wrapperPath)) {
  console.error(`Maven wrapper not found: ${wrapperPath}`)
  process.exit(1)
}

const result = spawnSync(wrapperPath, process.argv.slice(2), {
  cwd,
  stdio: "inherit",
  shell: process.platform === "win32"
})

if (result.error) {
  console.error(result.error.message)
  process.exit(1)
}

process.exit(result.status ?? 0)
