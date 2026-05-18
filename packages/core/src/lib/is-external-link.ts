/**
 * 判断链接是否为外部链接
 *
 * @example
 * isExternalLink('https://example.com') // true
 * isExternalLink('/dashboard')          // false
 * isExternalLink('mailto:a@b.com')      // true
 */
export function isExternalLink(href: string): boolean {
  return /^(https?:|mailto:|tel:)/.test(href)
}
