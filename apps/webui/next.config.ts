import { composePlugins, withNx } from "@nx/next";
import type { NextConfig } from "next";

const nextConfig: NextConfig = {};

const plugins = [withNx];

module.exports = composePlugins(...plugins)(nextConfig);
