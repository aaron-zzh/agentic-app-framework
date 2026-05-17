/**
 * Mock /api/chat——模拟 AI 流式生成（SSE）
 * 生产环境替换为真实 LLM 接口
 */

import type { NextRequest } from "next/server"

export async function POST(req: NextRequest) {
  const { messages } = await req.json()
  const prompt = messages?.[messages.length - 1]?.content ?? ""

  // 根据提示词生成 mock 内容
  const mockContent = generateMockContent(prompt)
  const tokens = mockContent.split("")

  const stream = new ReadableStream({
    async start(controller) {
      for (const token of tokens) {
        const data = JSON.stringify({ choices: [{ delta: { content: token } }] })
        controller.enqueue(new TextEncoder().encode(`data: ${data}\n\n`))
        // 模拟流式延迟
        await new Promise((r) => setTimeout(r, 30))
      }
      controller.enqueue(new TextEncoder().encode("data: [DONE]\n\n"))
      controller.close()
    }
  })

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache",
      Connection: "keep-alive"
    }
  })
}

function generateMockContent(prompt: string): string {
  const templates = [
    `根据您的需求"${prompt.slice(0, 20)}..."，以下是生成的内容：\n\n人工智能正在深刻改变我们的工作方式。通过自动化重复性任务，AI 让人类能够专注于更具创造性和战略性的工作。在内容创作领域，AI 工具可以快速生成初稿，帮助作者克服写作障碍，提高生产效率。`,
    `这是一段关于"${prompt.slice(0, 15)}..."的示例文本。\n\n技术的进步带来了前所未有的机遇。云计算、大数据和人工智能的融合，正在推动各行各业的数字化转型。企业需要拥抱这些变化，才能在竞争激烈的市场中保持领先地位。`,
    `针对您的提示"${prompt.slice(0, 20)}..."，生成如下：\n\n在这个信息爆炸的时代，高质量的内容比以往任何时候都更加重要。读者需要清晰、准确、有价值的信息来做出明智的决策。好的写作不仅传递信息，更能激发思考，引发共鸣。`
  ]
  return templates[Math.floor(Math.random() * templates.length)]
}
