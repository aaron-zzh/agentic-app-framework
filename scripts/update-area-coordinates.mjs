#!/usr/bin/env node
/**
 * 从高德地图行政区划接口拉取经纬度，扩展 area.csv 增加 longitude/latitude 两列。
 *
 * 用法：
 *   AMAP_KEY=xxx node scripts/update-area-coordinates.mjs           # 生成 area.csv.new
 *   AMAP_KEY=xxx node scripts/update-area-coordinates.mjs --apply   # 备份后直接覆盖 area.csv
 *
 * 设计：
 * - area.csv 的 id 对中国行政区即国标 adcode（110000=北京市），与高德 adcode 1:1 对应，无需名字模糊匹配
 * - 分省拉取（keywords={省名}&subdistrict=2）：避免一次性大响应被截断；调用 ~35 次，远低于免费额度
 * - 国外节点（type=1 且非中国）高德覆盖不到，longitude/latitude 留空
 * - 默认输出到 area.csv.new 供人工核对，加 --apply 才覆盖原文件（先备份到 .bak）
 *
 * 高德 API 文档：https://lbs.amap.com/api/webservice/guide/api/district
 */
import { readFileSync, writeFileSync, copyFileSync, existsSync } from "node:fs"
import { resolve } from "node:path"

const AMAP_KEY = process.env.AMAP_KEY
if (!AMAP_KEY) {
  console.error("✗ 缺少环境变量 AMAP_KEY（高德 Web 服务 key，platform.amap.com 申请）")
  process.exit(1)
}

const APPLY = process.argv.includes("--apply")
const ROOT = resolve(import.meta.dirname, "..")
const CSV_PATH = resolve(ROOT, "apps/service/aaf-common/src/main/resources/area.csv")
const OUT_PATH = APPLY ? CSV_PATH : `${CSV_PATH}.new`
const BAK_PATH = `${CSV_PATH}.bak`

// ─── 高德请求 ────────────────────────────────────────────────────────────────

const AMAP_BASE = "https://restapi.amap.com/v3/config/district"

/**
 * 拉指定 keyword 的行政区树。
 * @param {string} keywords 关键字（省名/"中国"等）
 * @param {number} subdistrict 子级深度（0-3）
 */
async function fetchDistrict(keywords, subdistrict) {
  const url = `${AMAP_BASE}?key=${AMAP_KEY}&keywords=${encodeURIComponent(keywords)}&subdistrict=${subdistrict}&extensions=base`
  const res = await fetch(url)
  if (!res.ok) throw new Error(`HTTP ${res.status} for ${keywords}`)
  const json = await res.json()
  if (json.status !== "1") {
    throw new Error(`高德返回失败: ${keywords} → ${json.info} (${json.infocode})`)
  }
  return json.districts
}

/**
 * 扁平化遍历 districts 树，收集所有节点的 adcode → [lng, lat]。
 */
function flatten(nodes, out) {
  for (const node of nodes ?? []) {
    if (node.adcode && node.center) {
      const [lng, lat] = node.center.split(",").map((s) => Number.parseFloat(s.trim()))
      if (Number.isFinite(lng) && Number.isFinite(lat)) {
        out.set(String(node.adcode), [lng, lat])
      }
    }
    if (node.districts?.length) flatten(node.districts, out)
  }
}

// ─── 主流程 ──────────────────────────────────────────────────────────────────

async function main() {
  console.log(`读取 ${CSV_PATH}`)
  const lines = readFileSync(CSV_PATH, "utf8").split(/\r?\n/)
  const header = lines[0]
  const dataLines = lines.slice(1).filter((l) => l.trim())

  // 收集中国 type=2/3/4 的 id，用于决定要拉哪些省
  /** @type {Map<string, {id:string, name:string, type:string, parentId:string}>} */
  const rows = new Map()
  for (const line of dataLines) {
    const [id, name, type, parentId] = line.split(",")
    rows.set(id, { id, name, type, parentId })
  }

  // 找 type=2 且 parentId=1（中国直辖一级）的省份/直辖市/特别行政区
  const provinces = [...rows.values()].filter((r) => r.type === "2" && r.parentId === "1")
  console.log(`待拉取省级行政区：${provinces.length} 个`)

  // ─── 1. 分省拉数据 ────────────────────────────────────────────────────
  /** @type {Map<string, [number, number]>} adcode → [lng, lat] */
  const coordMap = new Map()

  // 先单独拉中国根节点（拿到中国和各省经纬度）
  console.log("→ 拉取根节点（中国 + 省级）")
  flatten(await fetchDistrict("中国", 1), coordMap)

  // 再分省拉市/区（subdistrict=2 一次返回该省下的市与区/县）
  for (const p of provinces) {
    process.stdout.write(`→ 拉取 ${p.name} ... `)
    try {
      flatten(await fetchDistrict(p.name, 2), coordMap)
      console.log("✓")
    } catch (e) {
      console.log(`✗ ${e.message}`)
    }
    // 节流：避免高德 QPS 限制（个人 key 默认 30 次/秒，留余量）
    await new Promise((r) => setTimeout(r, 100))
  }

  console.log(`\n累计采集 ${coordMap.size} 个 adcode 的经纬度`)

  // ─── 2. 写新 csv ───────────────────────────────────────────────────────
  const newHeader = `${header},longitude,latitude`
  const newLines = [newHeader]
  let matched = 0
  let skipped = 0

  for (const line of dataLines) {
    const [id, name, type, parentId] = line.split(",")
    const coord = coordMap.get(id)
    if (coord) {
      newLines.push(`${id},${name},${type},${parentId},${coord[0]},${coord[1]}`)
      matched++
    } else {
      newLines.push(`${id},${name},${type},${parentId},,`)
      skipped++
    }
  }

  console.log(`匹配 ${matched} 条 / 跳过 ${skipped} 条（国外或高德未覆盖）`)

  // ─── 3. 写出 ──────────────────────────────────────────────────────────
  if (APPLY) {
    if (!existsSync(BAK_PATH)) {
      copyFileSync(CSV_PATH, BAK_PATH)
      console.log(`备份原文件 → ${BAK_PATH}`)
    }
    writeFileSync(CSV_PATH, `${newLines.join("\n")}\n`, "utf8")
    console.log(`✓ 已覆盖 ${CSV_PATH}`)
  } else {
    writeFileSync(OUT_PATH, `${newLines.join("\n")}\n`, "utf8")
    console.log(`✓ 已生成 ${OUT_PATH}`)
    console.log(`  对比无误后运行 --apply 覆盖原文件`)
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
