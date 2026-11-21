export function parsePaymentQrData(raw: string): string {
  let value: unknown
  try {
    value = JSON.parse(raw)
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

  return reference
}
