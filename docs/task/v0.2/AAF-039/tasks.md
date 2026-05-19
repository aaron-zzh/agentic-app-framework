---
level: Practice
layer: Product
purpose: AAF-039 存储服务的技术任务清单
status: pending
version: 1.0.0
date: 2026-05-19
author: AaronZZH
---

# 存储服务（AAF-039）

> 负责人：architect + developer-service | 创建：05-19

## 任务列表

1. [ ] #3901 存储抽象层设计
   - 定义 StorageService 接口（upload/download/delete/getUrl）
   - 存储策略枚举（LOCAL/OSS/MINIO）
   - 配置驱动切换存储后端
   - verify: 接口定义完整，编译通过

2. [ ] #3902 本地存储实现
   - 本地文件系统存储实现
   - 文件路径规则（按日期/类型分目录）
   - 静态资源访问配置
   - verify: 文件上传→下载→删除流程通过

3. [ ] #3903 阿里云 OSS 实现
   - OSS SDK 集成、STS 临时凭证
   - 直传签名（前端直传 OSS）、回调通知
   - verify: 文件上传到 OSS 并可访问

4. [ ] #3904 MinIO 实现
   - MinIO SDK 集成、Bucket 自动创建
   - 预签名 URL 生成
   - verify: MinIO 上传下载流程通过

5. [ ] #3905 图片处理
   - 缩略图生成（Thumbnailator）、水印添加
   - 图片压缩、格式转换
   - verify: 上传图片自动生成缩略图
