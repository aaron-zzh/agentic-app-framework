import type { MetadataRoute } from "next"
import { APP } from "@/lib/config"

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date()

  return [
    {
      url: APP.siteUrl,
      lastModified: now,
      changeFrequency: "weekly",
      priority: 1
    },
    {
      url: `${APP.siteUrl}/docs`,
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.8
    },
    {
      url: `${APP.siteUrl}/pricing`,
      lastModified: now,
      changeFrequency: "monthly",
      priority: 0.7
    }
  ]
}
