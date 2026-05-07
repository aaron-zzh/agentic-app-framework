import type { BaseLayoutProps } from 'fumadocs-ui/layouts/shared';
import { gitConfig } from './shared';

export function baseOptions(): BaseLayoutProps {
  return {
    nav: {
      title: 'Agentic App Framework',
    },
    githubUrl: `https://github.com/${gitConfig.user}/${gitConfig.repo}`,
    links: [
      { text: '指南', url: '/docs/guide/Readme', on: 'nav' },
      { text: '设计', url: '/docs/design/Readme', on: 'nav' },
      { text: '规范', url: '/docs/reference/Readme', on: 'nav' },
      { text: '原则', url: '/docs/explanation/Readme', on: 'nav' },
      { text: '教程', url: '/docs/tutorial/Readme', on: 'nav' },
    ],
  };
}
