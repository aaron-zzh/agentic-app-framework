<script setup lang="ts">
const { themeVars, theme } = useManualTheme()
</script>

<template>
  <wd-config-provider :theme-vars="themeVars" :theme="theme" :custom-class="`page-wraper ${theme}`">
    <ku-root-view />
    <wd-notify />
    <wd-dialog />
    <wd-toast />
    <global-loading />
    <global-toast />
    <global-dialog />
    <!-- #ifdef MP-WEIXIN -->
    <privacy-popup />
    <!-- #endif -->
  </wd-config-provider>
</template>
