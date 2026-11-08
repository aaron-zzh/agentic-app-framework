/**
 * PCM16 小端转换与流式分片拼接测试（AAF-123 #12302）。
 * @author AaronZZH & Kiro
 */

import { describe, expect, it } from "vitest"
import { createPcm16StreamDecoder, float32ToPcm16, pcm16ToFloat32 } from "./audio"

/** 按小端序把 int16 样本编码为字节 */
function encodeLe(samples: number[]): Uint8Array {
  const bytes = new Uint8Array(samples.length * 2)
  const view = new DataView(bytes.buffer)
  samples.forEach((s, i) => {
    view.setInt16(i * 2, s, true)
  })
  return bytes
}

describe("pcm16ToFloat32", () => {
  it("should_convert_little_endian_samples_when_bytes_even", () => {
    const result = pcm16ToFloat32(encodeLe([0, 0x4000, -0x8000, 0x7fff]))
    expect(Array.from(result)).toEqual([0, 0.5, -1, 0x7fff / 0x8000])
  })

  it("should_respect_byte_offset_when_view_not_aligned", () => {
    // 准备参数：样本从奇数偏移开始，Int16Array 无法直接构造
    const backing = new Uint8Array(5)
    backing.set(encodeLe([0x4000, -0x4000]), 1)
    const view = backing.subarray(1, 5)

    // 调用 + 断言
    expect(Array.from(pcm16ToFloat32(view))).toEqual([0.5, -0.5])
  })

  it("should_return_empty_when_no_bytes", () => {
    expect(pcm16ToFloat32(new Uint8Array(0))).toHaveLength(0)
  })

  it("should_round_trip_with_float32ToPcm16", () => {
    const pcm = float32ToPcm16(new Float32Array([0, -1, 0.5]))
    const bytes = new Uint8Array(pcm.buffer)
    // 0.5 * 0x7fff = 16383.5，写入 Int16Array 时截断为 16383
    expect(Array.from(pcm16ToFloat32(bytes))).toEqual([0, -1, 16383 / 0x8000])
  })
})

describe("createPcm16StreamDecoder", () => {
  it("should_carry_odd_byte_when_sample_split_across_chunks", () => {
    // 准备参数：3 个样本共 6 字节，切成 3 + 1 + 2
    const bytes = encodeLe([0x4000, -0x4000, 0x2000])
    const decoder = createPcm16StreamDecoder()

    // 调用
    const first = decoder.push(bytes.subarray(0, 3))
    const second = decoder.push(bytes.subarray(3, 4))
    const third = decoder.push(bytes.subarray(4, 6))

    // 断言
    expect(Array.from(first)).toEqual([0.5])
    expect(Array.from(second)).toEqual([-0.5])
    expect(Array.from(third)).toEqual([0.25])
  })

  it("should_keep_pending_byte_when_chunk_is_single_byte", () => {
    const bytes = encodeLe([-0x8000])
    const decoder = createPcm16StreamDecoder()

    expect(decoder.push(bytes.subarray(0, 1))).toHaveLength(0)
    expect(decoder.push(new Uint8Array(0))).toHaveLength(0)
    expect(Array.from(decoder.push(bytes.subarray(1, 2)))).toEqual([-1])
  })

  it("should_decode_all_samples_when_chunks_split_at_every_byte", () => {
    const samples = [1, -1, 0x1234, -0x1234, 0x7fff]
    const bytes = encodeLe(samples)
    const decoder = createPcm16StreamDecoder()

    const decoded: number[] = []
    for (let i = 0; i < bytes.byteLength; i++) {
      decoded.push(...decoder.push(bytes.subarray(i, i + 1)))
    }

    expect(decoded).toEqual(samples.map((s) => s / 0x8000))
  })
})
