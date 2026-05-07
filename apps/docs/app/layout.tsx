import { RootProvider } from 'fumadocs-ui/provider/next';
import './global.css';
import 'katex/dist/katex.css';
import type { ReactNode } from 'react';
import type { Translations } from 'fumadocs-ui/i18n';

const zhCN: Partial<Translations> = {
  search: '搜索文档',
  searchNoResult: '无搜索结果',
  toc: '本页目录',
  tocNoHeadings: '无目录',
  lastUpdate: '最后更新于',
  nextPage: '下一页',
  previousPage: '上一页',
  chooseTheme: '切换主题',
  editOnGithub: '在 GitHub 上编辑',
};

export default function Layout({ children }: { children: ReactNode }) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <body className="flex flex-col min-h-screen">
        <RootProvider i18n={{ translations: zhCN }}>{children}</RootProvider>
        <footer className="border-t border-fd-border py-4 text-center text-xs text-fd-muted-foreground">
          © {new Date().getFullYear()} AaronZZH · Agentic App Framework · Apache License 2.0
        </footer>
      </body>
    </html>
  );
}
