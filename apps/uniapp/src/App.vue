<script setup lang="ts">
onLaunch(async () => {
  await useAppStore().init()
})
</script>

<style lang="scss">
@use '@wot-ui/ui/styles/theme/index.scss' as *;
@use './themes/styles/campus.scss' as campus;
.page-wraper {
  min-height: calc(100vh - var(--window-top));
  box-sizing: border-box;
  background: var(--wot-filled-content);
}
</style>
