"use client"

import { useCallback, useState } from "react"
import type { Slide, SlideImage } from "yet-another-react-lightbox"

export type UseLightboxReturn = {
  open: boolean
  index: number
  onClose: () => void
  onOpen: (slideUrl: string) => void
}

export function useLightbox(slides: Slide[]): UseLightboxReturn {
  const [index, setIndex] = useState(-1)

  const onOpen = useCallback(
    (slideUrl: string) => {
      const found = slides.findIndex((slide) => {
        if ((slide as { type?: string }).type === "video") {
          const sources = (slide as { sources?: { src: string }[] }).sources
          return sources?.[0]?.src === slideUrl
        }
        return (slide as SlideImage).src === slideUrl
      })
      setIndex(found)
    },
    [slides]
  )

  const onClose = useCallback(() => setIndex(-1), [])

  return { index, open: index >= 0, onOpen, onClose }
}
