/**
 * (workspace) 默认 children 槽 fallback。
 *
 * <p>当客户端导航只更新 modal slot（拦截路由）时，children 槽保留上一次的内容；但如果 URL 段在
 * children 树里找不到对应 page（理论上不会发生），就会渲染本文件。返回 null 即可。
 *
 * @author AaronZZH & Kiro
 */
export default function Default() {
  return null
}
