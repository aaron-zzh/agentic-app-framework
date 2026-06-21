import type { MetadataRoute } from "next"
import { APP } from "@/lib/config"

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/dashboard/", "/settings/", "/admin/"]
    },
    sitemap: `${APP.siteUrl}/sitemap.xml`
  }
}
