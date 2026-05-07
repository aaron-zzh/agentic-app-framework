import defaultMdxComponents from 'fumadocs-ui/mdx';
import type { MDXComponents } from 'mdx/types';
import { Mermaid } from '@/components/mermaid';
import { Callout } from 'fumadocs-ui/components/callout';
import { Tab, Tabs } from 'fumadocs-ui/components/tabs';
import { Steps, Step } from 'fumadocs-ui/components/steps';

export function getMDXComponents(components?: MDXComponents) {
  return {
    ...defaultMdxComponents,
    // MDX body 里的 h1 由 <DocsTitle> 统一渲染，避免标题重复
    h1: () => null,
    Mermaid,
    Callout,
    Tab,
    Tabs,
    Steps,
    Step,
    ...components,
  } satisfies MDXComponents;
}

export const useMDXComponents = getMDXComponents;

declare global {
  type MDXProvidedComponents = ReturnType<typeof getMDXComponents>;
}
