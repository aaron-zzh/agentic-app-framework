export default function Details() {
  return (
    <>
      <nav className="fixed top-10 right-10 left-10 flex items-center justify-end gap-4">
        <a className="relative flex-none" href="https://github.com/pmndrs/zustand">
          Github
        </a>
      </nav>
      <div>
        <a
          className="absolute right-10 bottom-10"
          href="https://github.com/pmndrs/zustand/tree/main/examples/demo"
        >
          {"<Source />"}
        </a>
        <a className="absolute bottom-10 left-10" href="https://www.instagram.com/tina.henschel/">
          Illustrations @ Tina Henschel
        </a>
      </div>
      <span className="absolute top-10 left-10 inline-block font-bold text-5xl text-white uppercase leading-none max-md:text-base">
        Zustand
      </span>
    </>
  )
}
