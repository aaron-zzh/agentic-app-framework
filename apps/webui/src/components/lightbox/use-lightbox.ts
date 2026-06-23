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
      const found = slides.findIndex((slide) =>
        (slide as { type?: string; poster?: string }).type === "video"
          ? (slide as { poster?: string }).poster === slideUrl
          : (slide as SlideImage).src === slideUrl
      )
      setIndex(found)
    },
    [slides]
  )

  const onClose = useCallback(() => setIndex(-1), [])

  return { index, open: index >= 0, onOpen, onClose }
}
