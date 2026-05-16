<script lang="ts" setup>
const router = useRouter()

const route = useRoute()

const { activeTabbar, getTabbarItemValue, setTabbarItemActive, tabbarList } = useTabbar()

function handleTabbarChange({ value }: { value: string }) {
  setTabbarItemActive(value)
  router.pushTab({ name: value })
}

onMounted(() => {
  // #ifdef APP
  uni.hideTabBar()
  // #endif
  nextTick(() => {
    if (route.name && route.name !== activeTabbar.value.name) {
      setTabbarItemActive(route.name)
    }
  })
})
</script>

<script lang="ts">
export default {
  options: {
    addGlobalClass: true,
    virtualHost: true,
    styleIsolation: 'shared',
  },
}
</script>

<template>
  <slot />
  <wd-tabbar
    :model-value="activeTabbar.name" safe-area-inset-bottom fixed placeholder
    custom-style="border-top: 0.5px solid rgba(0,0,0,0.1);"
    @change="handleTabbarChange"
  >
    <template v-for="(item, index) in tabbarList" :key="index">
      <!-- 中间突起按钮 -->
      <wd-tabbar-item v-if="item.name === 'chat'" :name="item.name" :value="getTabbarItemValue(item.name)" title="">
        <view class="raised-btn" :class="{ active: activeTabbar.name === 'chat' }">
          <view class="raised-btn-inner">
            <image :src="item.activeIcon" style="width: 32px; height: 32px;" mode="aspectFit" />
          </view>
        </view>
      </wd-tabbar-item>
      <!-- 普通 item -->
      <wd-tabbar-item v-else :name="item.name" :value="getTabbarItemValue(item.name)" :title="item.title">
        <template #icon="{ active }">
          <image :src="active ? item.activeIcon : item.icon" style="width: 48rpx; height: 48rpx;" mode="aspectFit" />
        </template>
      </wd-tabbar-item>
    </template>
  </wd-tabbar>
</template>

<style scoped>
.raised-btn {
  position: absolute;
  bottom: 8px;
  left: 50%;
  transform: translateX(-50%);
  width: 60px;
  height: 60px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: conic-gradient(
    rgba(142, 68, 173, 0) 0deg,
    rgba(52, 152, 219, 0.4) 60deg,
    rgba(142, 68, 173, 1) 120deg,
    rgba(52, 152, 219, 1) 180deg,
    rgba(142, 68, 173, 0.4) 240deg,
    rgba(52, 152, 219, 0) 300deg,
    rgba(142, 68, 173, 0) 360deg
  );
  box-shadow:
    0 0 4px 1px rgba(142, 68, 173, 0.8),
    0 0 8px 3px rgba(52, 152, 219, 0.5),
    0 0 14px 6px rgba(142, 68, 173, 0.2);
  animation: spin 3s linear infinite;
}
.raised-btn.active {
  animation-duration: 1.5s;
  box-shadow:
    0 0 6px 2px rgba(142, 68, 173, 1),
    0 0 12px 5px rgba(52, 152, 219, 0.6),
    0 0 20px 8px rgba(142, 68, 173, 0.3);
}
.raised-btn-inner {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  animation: spin-reverse 3s linear infinite;
}
.raised-btn.active .raised-btn-inner {
  animation-duration: 1.5s;
}
@keyframes spin {
  from { transform: translateX(-50%) rotate(0deg); }
  to   { transform: translateX(-50%) rotate(360deg); }
}
@keyframes spin-reverse {
  from { transform: rotate(0deg); }
  to   { transform: rotate(-360deg); }
}
</style>
