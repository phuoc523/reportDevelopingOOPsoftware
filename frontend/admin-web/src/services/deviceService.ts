
import type { Device } from '../types/device';
import { mockDevices } from '../data/mockDevices';

export const deviceService = {
  async getDevices(): Promise<Device[]> {
    // Hiện tại sử dụng dữ liệu mô phỏng.
    return mockDevices;
  },
};
