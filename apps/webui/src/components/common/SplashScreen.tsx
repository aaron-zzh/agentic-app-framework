/**
 * SplashScreen——应用初始加载全屏动画
 * @author AaronZZH & Kiro
 *
 * Logo 脉冲缩放 + 旋转边框环，纯 CSS 动画
 */

export function SplashScreen() {
  return (
    <div className="fixed inset-0 z-[9998] flex items-center justify-center bg-background">
      <div className="relative flex size-[120px] items-center justify-center">
        {/* Logo 脉冲 */}
        <div className="animate-[pulse_2s_ease-in-out_infinite]">
          {/* biome-ignore lint/performance/noImgElement: splash logo */}
          <img src="/logo/logo.png" alt="" className="size-16" />
        </div>

        {/* 外圈旋转 */}
        <span className="absolute inset-0 animate-[spin_3s_linear_infinite] rounded-[25%] border-[3px] border-primary/20" />
        <span className="absolute inset-2 animate-[spin_3s_linear_infinite_reverse] rounded-[25%] border-[6px] border-primary/10" />
      </div>
    </div>
  )
}
