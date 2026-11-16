self.addEventListener('push', (event) => {
  let payload = {}
  try { payload = event.data ? event.data.json() : {} } catch { payload = { notification: { body: event.data?.text() } } }
  const notification = payload.notification || {}
  const data = payload.data || {}
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
    windows.forEach((client) => client.postMessage({ type: 'scentstation:firebase-message', payload }))
    if (windows.some((client) => client.visibilityState === 'visible')) return undefined
    return self.registration.showNotification(notification.title || data.title || 'ScentStation', {
      body: notification.body || data.content || data.body || 'Bạn có thông báo vận hành mới.',
      icon: '/favicon.svg',
      badge: '/favicon.svg',
      data: { url: data.url || '/' },
    })
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const target = event.notification.data?.url || '/'
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
    const existing = windows.find((client) => new URL(client.url).origin === self.location.origin)
    return existing ? existing.focus() : clients.openWindow(target)
  }))
})
