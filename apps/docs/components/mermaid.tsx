import { CodeBlock, Pre } from 'fumadocs-ui/components/codeblock';
import { renderMermaidSVG } from 'beautiful-mermaid';

export async function Mermaid({ chart }: { chart: string }) {
  try {
    let svg = renderMermaidSVG(chart, {
      bg: 'var(--color-fd-background)',
      fg: 'var(--color-fd-foreground)',
      interactive: true,
      transparent: true,
    });

    // 覆盖原始 width/height：宽度自适应容器，高度固定 400px，通过 viewBox 等比缩放
    svg = svg.replace(/<svg([^>]*)>/, (_match, attrs: string) => {
      const cleaned = attrs
        .replace(/\s*width="[^"]*"/, '')
        .replace(/\s*height="[^"]*"/, '');
      return `<svg${cleaned} width="100%" height="400" style="display:block">`;
    });

    return (
      <div style={{ maxHeight: '600px', overflow: 'auto' }} className="my-4 rounded border border-fd-border">
        <div dangerouslySetInnerHTML={{ __html: svg }} />
      </div>
    );
  } catch {
    return (
      <CodeBlock title="Mermaid">
        <Pre>{chart}</Pre>
      </CodeBlock>
    );
  }
}
