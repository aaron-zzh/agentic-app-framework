"use client"

import { useParams } from "next/navigation"
import { PageContainer } from "@/components/common/PageContainer"
import { Skeleton } from "@/components/ui/skeleton"
import { useDocument } from "@/lib/queries/use-documents"
import { DocEditForm } from "../../DocEditForm"

export default function DocEditPage() {
  const params = useParams<{ id: string }>()
  const id = Number(params.id)
  const { data: doc, isLoading } = useDocument(Number.isNaN(id) ? null : id)

  if (isLoading) {
    return (
      <PageContainer>
        <div className="mx-auto max-w-4xl space-y-4 p-6">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-96 w-full" />
        </div>
      </PageContainer>
    )
  }

  if (!doc) return null

  return (
    <PageContainer disablePadding>
      <DocEditForm doc={doc} />
    </PageContainer>
  )
}
