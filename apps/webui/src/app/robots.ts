import type { MetadataRoute } from "next"

const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? "https://aaf.xuejiai.com"

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/api/", "/dashboard/", "/settings/", "/admin/"]
    },
    sitemap: `${SITE_URL}/sitemap.xml`
  }
}
