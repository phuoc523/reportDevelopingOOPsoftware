import { describe, expect, it } from 'vitest'
import { parsePaymentQrData } from './paymentQr'

describe('parsePaymentQrData', () => {
  it('returns a payment reference from the transaction QR payload', () => {
    expect(parsePaymentQrData(JSON.stringify({
      type: 'SCENTSTATION_PAYMENT',
      paymentReference: 'PAY-ORDER-A',
    }))).toBe('PAY-ORDER-A')
  })

  it('accepts a plain payment reference for compatibility', () => {
    expect(parsePaymentQrData('  PAY-ORDER-B  ')).toBe('PAY-ORDER-B')
  })

  it('keeps different payment references distinct', () => {
    expect(parsePaymentQrData('PAY-ORDER-A')).not.toBe(parsePaymentQrData('PAY-ORDER-B'))
  })

  it('rejects malformed, unrelated, or URL-bearing QR data', () => {
    const invalidPayloads = [
      '{invalid',
      JSON.stringify({ type: 'OTHER', paymentReference: 'PAY-ORDER-A' }),
      JSON.stringify({ type: 'SCENTSTATION_PAYMENT' }),
      JSON.stringify({ type: 'SCENTSTATION_PAYMENT', paymentReference: 'not-a-payment' }),
      JSON.stringify({ type: 'SCENTSTATION_PAYMENT', paymentReference: 'PAY-ORDER-A', url: 'http://example.test/' }),
    ]

    for (const payload of invalidPayloads) {
      expect(() => parsePaymentQrData(payload)).toThrow('Mã QR không đúng định dạng')
    }
  })
})
