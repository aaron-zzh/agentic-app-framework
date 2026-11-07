/**
 * PDF 预览组件出口：react-pdf 依赖浏览器 API，统一以 ssr:false 动态加载。
 * @author AaronZZH & Kiro
 */
import dynamic from "next/dynamic"

export type { PdfSource, PdfViewerProps } from "./PdfViewerImpl"

export const PdfViewer = dynamic(() => import("./PdfViewerImpl"), { ssr: false })
