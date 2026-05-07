import { defineConfig, defineDocs } from 'fumadocs-mdx/config';
import { remarkMdxMermaid } from 'fumadocs-core/mdx-plugins';
import { z } from 'zod';

// AAF 文档 frontmatter schema（所有字段可选，兼容现有文档）
const aafPageSchema = z.object({
  title: z.string().optional(),
  description: z.string().optional(),
  status: z.string().optional(),
  date: z.union([z.string(), z.date().transform((d) => d.toISOString())]).optional(),
  author: z.string().optional(),
  level: z.union([z.string(), z.number().transform(String)]).optional(),
  layer: z.string().optional(),
  purpose: z.string().optional(),
}).passthrough();

export const docs = defineDocs({
  dir: '../../docs',
  docs: {
    schema: aafPageSchema,
    files: ['**/*.{md,mdx}', '!tmp/**', '!task/**', '!prd/**', '!learn/**', '!reference/team/**'],
  },
  meta: {
    files: ['**/*.json', '!tmp/**', '!task/**', '!prd/**', '!learn/**', '!reference/team/**'],
  },
});

export default defineConfig({
  mdxOptions: {
    remarkPlugins: [remarkMdxMermaid],
  },
});
