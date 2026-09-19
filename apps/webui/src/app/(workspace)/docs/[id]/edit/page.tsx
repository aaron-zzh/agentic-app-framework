"use client"

import { useParams } from "next/navigation"
import { PageContainer } from "@/components/common/PageContainer"
import { Skeleton } from "@/components/ui/skeleton"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { DocumentVersionHistory } from "@/features/document/DocumentVersionHistory"
import { useDocument } from "@/lib/api/rest/system"
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
      <Tabs defaultValue="edit" className="flex h-full min-h-0 flex-col">
        <div className="border-b px-6 pt-3">
          <TabsList>
            <TabsTrigger value="edit">编辑</TabsTrigger>
            <TabsTrigger value="versions">版本历史</TabsTrigger>
          </TabsList>
        </div>
        <TabsContent value="edit" className="min-h-0 flex-1">
          <DocEditForm doc={doc} />
        </TabsContent>
        <TabsContent value="versions" className="min-h-0 flex-1">
          <DocumentVersionHistory
            documentId={doc.id}
            currentVersionId={doc.currentVersionId}
            publishedVersionId={doc.publishedVersionId}
          />
        </TabsContent>
      </Tabs>
    </PageContainer>
  )
}
