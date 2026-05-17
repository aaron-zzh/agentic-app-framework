/**
 * Lexical 节点注册列表
 * @author AaronZZH & Kiro
 */

import { CodeNode } from "@lexical/code"
import { LinkNode } from "@lexical/link"
import { ListItemNode, ListNode } from "@lexical/list"
import { HeadingNode, QuoteNode } from "@lexical/rich-text"
import type { Klass, LexicalNode } from "lexical"
import { ImageNode } from "../nodes/ImageNode"

export const allNodes: Klass<LexicalNode>[] = [
  HeadingNode,
  QuoteNode,
  ListNode,
  ListItemNode,
  LinkNode,
  CodeNode,
  ImageNode
]
