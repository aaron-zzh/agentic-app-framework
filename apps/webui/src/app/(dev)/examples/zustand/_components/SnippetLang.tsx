import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"

interface SnippetLangProps {
  lang: string
  setLang: (lang: string) => void
}

export default function SnippetLang({ lang, setLang }: SnippetLangProps) {
  return (
    <Select
      value={lang}
      onValueChange={(v) => {
        if (v) setLang(v)
      }}
    >
      <SelectTrigger className="h-7 w-[110px] border-none bg-gray-800 text-white text-xs">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="javascript">JavaScript</SelectItem>
        <SelectItem value="typescript">TypeScript</SelectItem>
      </SelectContent>
    </Select>
  )
}
