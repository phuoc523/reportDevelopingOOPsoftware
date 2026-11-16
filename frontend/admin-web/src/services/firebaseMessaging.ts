import { initializeApp, getApps } from 'firebase/app'
import { deleteToken, getMessaging, getToken, isSupported, onMessage } from 'firebase/messaging'
import type { Messaging, Unsubscribe } from 'firebase/messaging'

const DEVICE_ID_KEY = 'scentstation.admin.notification-device-id'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

export const firebaseMessagingConfigured = Boolean(
  firebaseConfig.apiKey
  && firebaseConfig.projectId
  && firebaseConfig.messagingSenderId
  && firebaseConfig.appId
  && import.meta.env.VITE_FIREBASE_VAPID_KEY,
)

export function notificationDeviceId(): string {
  const existing = localStorage.getItem(DEVICE_ID_KEY)
  if (existing) return existing
  const generated = globalThis.crypto?.randomUUID?.() ?? `web-${Date.now()}-${Math.random().toString(16).slice(2)}`
  localStorage.setItem(DEVICE_ID_KEY, generated)
  return generated
}

async function configuredMessaging(): Promise<Messaging> {
  if (!firebaseMessagingConfigured) throw new Error('Firebase Web chưa được cấu hình cho môi trường này.')
  if (!('Notification' in window) || !(await isSupported())) throw new Error('Trình duyệt này không hỗ trợ thông báo đẩy.')
  const app = getApps()[0] ?? initializeApp(firebaseConfig)
  return getMessaging(app)
}

export async function requestFirebaseToken(): Promise<{ deviceId: string; token: string }> {
  const messaging = await configuredMessaging()
  const permission = await Notification.requestPermission()
  if (permission !== 'granted') throw new Error('Bạn chưa cho phép trình duyệt nhận thông báo.')

  const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js')
  const token = await getToken(messaging, {
    vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY,
    serviceWorkerRegistration: registration,
  })
  if (!token) throw new Error('Firebase không cấp được mã thiết bị. Vui lòng thử lại.')
  return { deviceId: notificationDeviceId(), token }
}

export async function deleteFirebaseToken(): Promise<void> {
  if (!firebaseMessagingConfigured || !(await isSupported())) return
  await deleteToken(await configuredMessaging())
}

export async function subscribeForegroundMessages(
  listener: () => void,
): Promise<Unsubscribe> {
  if (!firebaseMessagingConfigured || !(await isSupported())) return () => undefined
  const firebaseUnsubscribe = onMessage(await configuredMessaging(), () => listener())
  const serviceWorkerListener = (event: MessageEvent) => {
    if (event.data?.type === 'scentstation:firebase-message') listener()
  }
  navigator.serviceWorker.addEventListener('message', serviceWorkerListener)
  return () => {
    firebaseUnsubscribe()
    navigator.serviceWorker.removeEventListener('message', serviceWorkerListener)
  }
}
