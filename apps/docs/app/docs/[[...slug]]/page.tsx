import { source } from '@/lib/source';
import {
  DocsBody,
  DocsDescription,
  DocsPage,
  DocsTitle, EditOnGitHub,
  PageLastUpdate
} from 'fumadocs-ui/layouts/docs/page';
import { notFound } from 'next/navigation';
import { getMDXComponents } from '@/components/mdx';
import type { Metadata } from 'next';
import { createRelativeLink } from 'fumadocs-ui/mdx';

import { getGithubLastEdit } from 'fumadocs-core/content/github';
import {GITHUB_DOCS_BASEURL, GITHUB_OWNER, GITHUB_REPO} from "@/lib/github";

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
  const lastModifiedTime = await getGithubLastEdit({
    owner: GITHUB_OWNER,
    repo: GITHUB_REPO,
    path: `docs/${page.path}`,
    token: process.env.GITHUB_TOKEN,
  }).catch(() => null);
  const githubUrl = GITHUB_DOCS_BASEURL + page.path;

  return (
    <DocsPage toc={page.data.toc} tableOfContent={{style: 'clerk'}}>
      <DocsTitle>{page.data.title}</DocsTitle>
      <DocsDescription>{page.data.purpose}</DocsDescription>
      <div className="flex flex-row flex-wrap items-center border-b pb-2 mb-6 justify-between gap-4 empty:hidden">
        {lastModifiedTime && <PageLastUpdate date={lastModifiedTime} />}
        <EditOnGitHub href={githubUrl}/>
      </div>
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
