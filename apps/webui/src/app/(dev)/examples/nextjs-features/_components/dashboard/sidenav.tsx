import { LogOut as PowerIcon } from "lucide-react"
import { Link } from "next-view-transitions"

import NavLinks from "./nav-links"

export default function SideNav() {
  return (
    <div className="flex h-full flex-col px-3 py-4 md:px-2">
      <Link
        className="mb-2 flex h-20 items-end justify-start rounded-md bg-blue-600 p-4 md:h-20"
        href="/examples/nextjs-features"
      >
        <span className="font-bold text-lg text-white">Acme</span>
      </Link>
      <div className="flex grow flex-row justify-between space-x-2 md:flex-col md:space-x-0 md:space-y-2">
        <NavLinks />
        <div className="hidden h-auto w-full grow rounded-md bg-gray-50 md:block" />
        <div className="flex h-10 w-full items-center justify-center gap-2 rounded-md bg-gray-50 p-3 font-medium text-gray-500 text-sm md:justify-start md:px-3">
          <PowerIcon className="w-4" />
          <span className="hidden md:block">Sign Out</span>
        </div>
      </div>
    </div>
  )
}
