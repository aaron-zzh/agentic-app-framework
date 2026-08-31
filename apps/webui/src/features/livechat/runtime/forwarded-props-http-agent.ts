/**
 * 携带 forwardedProps 的 AG-UI HttpAgent。
 *
 * 按 AG-UI 协议语义，本次 run 的一次性调用参数（mode / assistantId / taskModelSelection / request /
 * teamId 等）属于 `forwardedProps`——单向 client→server、不回写；`state` 是线程级共享状态，会被
 * `STATE_SNAPSHOT` / `STATE_DELTA` 双向同步，只应放页面感知上下文这类真正的状态。
 *
 * `@assistant-ui/react-ag-ui` 的 useAgUiRuntime 只接受 `agent`，内部自行调用 `runAgent()`，
 * 拿不到 `RunAgentParameters` 注入点，因此在 agent 层覆写 `run` 注入 forwardedProps。
 * @author AaronZZH & Kiro
 */

import { HttpAgent } from "@ag-ui/client"

/** 从 HttpAgent.run 推导协议入参与返回流类型，避免直接依赖非直接依赖包（@ag-ui/core、rxjs）。 */
type RunAgentInput = Parameters<HttpAgent["run"]>[0]
type RunAgentStream = ReturnType<HttpAgent["run"]>

type HttpAgentConfig = ConstructorParameters<typeof HttpAgent>[0]

export class ForwardedPropsHttpAgent extends HttpAgent {
  private readonly forwardedProps: Record<string, unknown>

  constructor(config: HttpAgentConfig, forwardedProps: Record<string, unknown>) {
    super(config)
    this.forwardedProps = forwardedProps
  }

  override run(input: RunAgentInput): RunAgentStream {
    return super.run({
      ...input,
      forwardedProps: { ...(input.forwardedProps ?? {}), ...this.forwardedProps }
    })
  }
}
