
import type { Device } from '../types/device';

export const mockDevices: Device[] = [
  {
    id: 'SP-001',
    name: 'Đầu phun SP-001',
    product: 'Citrus No. 08',
    location: 'Tầng 1 · Sảnh chính',
    status: 'Online',
    stock: 82,
  },
  {
    id: 'SP-002',
    name: 'Đầu phun SP-002',
    product: 'Ocean Breeze',
    location: 'Tầng 2 · Khu mua sắm',
    status: 'Online',
    stock: 45,
  },
  {
    id: 'SP-003',
    name: 'Đầu phun SP-003',
    product: 'Rose Velvet',
    location: 'Tầng 3 · Khu dịch vụ',
    status: 'Offline',
    stock: 12,
  },
  {
    id: 'SP-004',
    name: 'Đầu phun SP-004',
    product: 'Woody Night',
    location: 'Tầng 3 · Khu dịch vụ',
    status: 'Online',
    stock: 0,
  },
];
