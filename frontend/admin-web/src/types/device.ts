
export type DeviceStatus = 'Online' | 'Offline';

export interface Device {
  id: string;
  name: string;
  product: string;
  location: string;
  status: DeviceStatus;
  stock: number;
}
