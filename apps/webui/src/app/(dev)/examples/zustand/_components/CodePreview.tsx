import { Highlight } from "prism-react-renderer"
import { create } from "zustand"

import javascriptCode from "../resources/javascript-code.js"
import typescriptCode from "../resources/typescript-code.js"
import CopyButton from "./CopyButton"
import SnippetLang from "./SnippetLang"

type Store = {
  lang: string
  setLang: (lang: string) => void
  getCode: () => string
}

const useStore = create<Store>((set, get) => ({
  lang: "javascript",
  setLang: (lang) => set(() => ({ lang })),
  getCode: () => (get().lang === "javascript" ? javascriptCode : typescriptCode)
}))

export default function CodePreview() {
  const { lang, setLang, getCode } = useStore()
  const code = getCode()

  return (
    <Highlight code={code} language="tsx" theme={undefined}>
      {({ className, style, tokens, getLineProps, getTokenProps }) => (
        // define how each line is to be rendered in the code block,
        // position is set to relative so the copy button can align to bottom right
        <pre
          className={`${className} relative mt-[-50px] inline-block w-auto whitespace-pre-wrap rounded-[10px] p-10 text-sm shadow-[0_16px_40px_-5px_rgba(0,0,0,1)] max-md:rounded-t-[10px] max-md:rounded-b-none max-md:text-xs`}
          style={style}
        >
          {tokens.map((line, i) => (
            <div {...getLineProps({ line })} key={i}>
              {line.map((token, key) => (
                <span {...getTokenProps({ token })} key={key} />
              ))}
            </div>
          ))}
          <div className="absolute right-0 bottom-0 flex items-center gap-1 p-1.5">
            <SnippetLang lang={lang} setLang={setLang} />
            <CopyButton code={code} />
          </div>
        </pre>
      )}
    </Highlight>
  )
}
