"use client"

import clsx from "clsx"
import { ArrowLeft as ArrowLeftIcon, ArrowRight as ArrowRightIcon } from "lucide-react"
import Link from "next/link"
import { usePathname, useSearchParams } from "next/navigation"

import { generatePagination } from "../../_data/mock"

export default function Pagination({ totalPages }: { totalPages: number }) {
  const pathname = usePathname()
  const searchParams = useSearchParams()
  const currentPage = Number(searchParams.get("page")) || 1

  const createPageURL = (pageNumber: number | string) => {
    const params = new URLSearchParams(searchParams)
    params.set("page", pageNumber.toString())
    return `${pathname}?${params.toString()}`
  }

  const allPages = generatePagination(currentPage, totalPages)

  return (
    <div className="inline-flex">
      <PaginationArrow
        direction="left"
        href={createPageURL(currentPage - 1)}
        isDisabled={currentPage <= 1}
      />
      <div className="flex -space-x-px">
        {allPages.map((page, index) => (
          <PaginationNumber
            key={`${page}-${index}`}
            href={createPageURL(page)}
            page={page}
            isActive={currentPage === page}
            position={
              index === 0
                ? "first"
                : index === allPages.length - 1
                  ? "last"
                  : page === "..."
                    ? "middle"
                    : undefined
            }
          />
        ))}
      </div>
      <PaginationArrow
        direction="right"
        href={createPageURL(currentPage + 1)}
        isDisabled={currentPage >= totalPages}
      />
    </div>
  )
}

function PaginationNumber({
  page,
  href,
  isActive,
  position
}: {
  page: number | string
  href: string
  isActive: boolean
  position?: "first" | "last" | "middle"
}) {
  const className = clsx("flex h-10 w-10 items-center justify-center border text-sm", {
    "rounded-l-md": position === "first",
    "rounded-r-md": position === "last",
    "z-10 border-blue-600 bg-blue-600 text-white": isActive,
    "hover:bg-gray-100": !isActive && position !== "middle",
    "text-gray-300": position === "middle"
  })

  return isActive || position === "middle" ? (
    <div className={className}>{page}</div>
  ) : (
    <Link href={href} className={className}>
      {page}
    </Link>
  )
}

function PaginationArrow({
  href,
  direction,
  isDisabled
}: {
  href: string
  direction: "left" | "right"
  isDisabled?: boolean
}) {
  const className = clsx("flex h-10 w-10 items-center justify-center rounded-md border", {
    "pointer-events-none text-gray-300": isDisabled,
    "hover:bg-gray-100": !isDisabled,
    "mr-2": direction === "left",
    "ml-2": direction === "right"
  })

  return isDisabled ? (
    <div className={className}>
      {direction === "left" ? (
        <ArrowLeftIcon className="w-4" />
      ) : (
        <ArrowRightIcon className="w-4" />
      )}
    </div>
  ) : (
    <Link href={href} className={className}>
      {direction === "left" ? (
        <ArrowLeftIcon className="w-4" />
      ) : (
        <ArrowRightIcon className="w-4" />
      )}
    </Link>
  )
}
