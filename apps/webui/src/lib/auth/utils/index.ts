import axios from "axios"

export function setAxiosAuth(token: string): void {
  axios.defaults.headers.common.Authorization = `Bearer ${token}`
}

export function clearAxiosAuth(): void {
  delete axios.defaults.headers.common.Authorization
}
