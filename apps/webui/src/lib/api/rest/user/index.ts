export type { LoginResult, UserInfo } from "./auth"
export { authApi } from "./auth"

export type { MenuCreateDTO, MenuUpdateDTO, MenuVO } from "./menu"
export {
  menuApi,
  useAllMenus,
  useCreateMenu,
  useDeleteMenu,
  useUpdateMenu,
  useUserMenus
} from "./menu"

export type { NotificationItem, NotificationListParams, NotificationType } from "./notification"
export {
  notificationApi,
  useMarkRead,
  useNotifications,
  useRemoveNotifications,
  useUnreadCount
} from "./notification"

export type {
  ChannelConfig,
  NotificationChannel,
  NotificationPreference
} from "./notification-preference"
export {
  notificationPreferenceApi,
  useNotificationPreference,
  useUpdateNotificationPreference
} from "./notification-preference"

export type { OrgAddMemberReq, OrgMemberVO, OrganizationVO, OrgUpdateReq } from "./organization"
export {
  organizationApi,
  useAddOrgMember,
  useOrgMembers,
  useOrganizations,
  useRemoveOrgMember,
  useUpdateOrganization
} from "./organization"

export type { EntityAccess, FieldAccess } from "./permission"
export { fetchEntityAccess, useEntityAccess } from "./permission"

export type { ChangePasswordReq, ProfileUpdateReq, ProfileVO } from "./profile"
export {
  profileApi,
  profileQueries,
  useChangePassword,
  useProfile,
  useUpdateProfile
} from "./profile"

export type { AddFavoriteDTO, FavoritesParams, UserFavoriteVO } from "./favorite"
export {
  favoriteApi,
  useAddFavorite,
  useRemoveFavorite,
  useToggleFavorite,
  useUserFavorites
} from "./favorite"

export type { GrowthTaskVO } from "./growth"
export { growthApi, useClaimGrowthTask, useGrowthTasks } from "./growth"
