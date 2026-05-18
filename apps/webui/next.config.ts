import { composePlugins, withNx } from "@nx/next";
import withSerwistInit from "@serwist/next";
import type { NextConfig } from "next";

const withSerwist = withSerwistInit({
  swSrc: "src/app/sw.ts",
  swDest: "public/sw.js",
  disable: process.env.NODE_ENV !== "production"
});

const nextConfig: NextConfig = {};

const plugins = [withNx, withSerwist];

module.exports = composePlugins(...plugins)(nextConfig);
