export default function Details() {
  return (
    <>
      <nav className="absolute top-20 right-10 left-10 flex items-center justify-end gap-4">
        <a className="relative flex-none" href="https://github.com/aaron-zzh/agentic-app-framework">
          Github
        </a>
      </nav>
      <div>
        <a
          className="absolute right-10 bottom-10"
          href="https://github.com/aaron-zzh/agentic-app-framework/tree/main/"
        >
          {"<Source />"}
        </a>
      </div>
      <span className="absolute top-20 left-10 inline-block font-bold text-5xl text-white uppercase leading-none max-md:text-base">
        可视化
      </span>
    </>
  )
}
