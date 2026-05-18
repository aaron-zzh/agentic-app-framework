/**
 * 比较两个 URL 路径是否相同
 *
 * @param path1 - 第一个路径
 * @param path2 - 第二个路径
 * @param options.deep - true: 比较完整 URL（含 query/hash）；false: 仅比较 pathname
 *
 * @example
 * isEqualPath('/a?x=1', '/a?x=2', { deep: false }) // true（仅比较 pathname）
 * isEqualPath('/a?x=1', '/a?x=2', { deep: true })  // false（完整比较）
 */
export function isEqualPath(
  path1: string,
  path2: string,
  options: { deep: boolean } = { deep: true }
): boolean {
  try {
    // 补全相对路径，使 URL 构造函数能解析
    const base = "http://n"
    const url1 = new URL(path1, base)
    const url2 = new URL(path2, base)

    if (options.deep) {
      return url1.pathname + url1.search + url1.hash === url2.pathname + url2.search + url2.hash
    }
    return url1.pathname === url2.pathname
  } catch {
    return path1 === path2
  }
}
