import type { MetadataRoute } from "next"
import { APP } from "@/lib/config"

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: APP.name,
    short_name: APP.name,
    description: APP.description,
    id: "/dashboard",
    start_url: "/dashboard",
    scope: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#0f172a",
    orientation: "portrait-primary",
    dir: "ltr",
    lang: "zh-CN",
    icons: [
      {
        src: "/logo/logo.png",
        sizes: "any",
        type: "image/png",
        purpose: "maskable"
      }
    ],
    categories: ["productivity", "business"]
  }
}
