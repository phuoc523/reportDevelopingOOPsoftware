export function parsePaymentQrData(raw: string): string {
  const trimmed = raw.trim()
  if (!trimmed) {
    throw new Error('Mã QR không đúng định dạng thanh toán ScentStation.')
  }

  if (/^PAY-[A-Z0-9-]{1,124}$/i.test(trimmed)) {
    return trimmed.toUpperCase()
  }

  let value: unknown
  try {
    value = JSON.parse(trimmed)
  } catch {
    throw new Error('Mã QR không đúng định dạng thanh toán ScentStation.')
  }

  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error('Mã QR không đúng định dạng thanh toán ScentStation.')
  }

  const payload = value as Record<string, unknown>
  const reference = payload.paymentReference
  if (
    Object.keys(payload).length !== 2
    || payload.type !== 'SCENTSTATION_PAYMENT'
    || typeof reference !== 'string'
    || !/^PAY-[A-Z0-9-]{1,124}$/i.test(reference)
  ) {
    throw new Error('Mã QR không đúng định dạng thanh toán ScentStation.')
  }

  return reference.toUpperCase()
}
