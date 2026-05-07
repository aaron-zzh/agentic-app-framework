import { source } from '@/lib/source';
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle,
} from 'fumadocs-ui/layouts/docs/page';
import { notFound } from 'next/navigation';
import { getMDXComponents } from '@/components/mdx';
import type { Metadata } from 'next';
import { createRelativeLink } from 'fumadocs-ui/mdx';

export default async function Page(props: PageProps<'/docs/[[...slug]]'>) {
  const params = await props.params;
  const slug = params.slug ?? [];
  const page = source.getPage(slug) ?? source.getPage([...slug, 'Readme']);
  if (!page) notFound();

  if (page.data.status === 'draft') {
    return (
      <DocsPage>
        <div className="flex flex-col items-center justify-center py-16 text-center">
          <h1 className="text-2xl font-bold mb-2">文档编写中</h1>
          <p className="text-fd-muted-foreground">该文档尚未发布，敬请期待。</p>
        </div>
      </DocsPage>
    );
  }

  const MDX = page.data.body;

  return (
    <DocsPage toc={page.data.toc}>
      <DocsTitle>{page.data.title}</DocsTitle>
      <DocsDescription>{page.data.description}</DocsDescription>
      <DocsBody>
        <MDX
          components={getMDXComponents({
            a: createRelativeLink(source, page),
          })}
        />
      </DocsBody>
    </DocsPage>
  );
}

export async function generateStaticParams() {
  return source.generateParams();
}

export async function generateMetadata(props: PageProps<'/docs/[[...slug]]'>): Promise<Metadata> {
  const params = await props.params;
  const slug = params.slug ?? [];
  const page = source.getPage(slug) ?? source.getPage([...slug, 'Readme']);
  if (!page) notFound();

  return {
    title: page.data.title,
    description: page.data.description,
  };
}
