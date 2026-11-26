import { Client, ReconnectionTimeMode } from '@stomp/stompjs';
import { isTokenExpired, notificationApi, userTokenStore } from '../api.js';

/**
 * Kết nối STOMP tới notification-service qua gateway: ws(s)://<host>/notification/ws.
 * Trình duyệt không gửi được header Authorization khi mở WebSocket, nên token đi trong frame CONNECT
 * (không đặt token trên URL để tránh lộ vào log).
 *
 * Kết nối lại tự động: chờ 3 giây rồi nhân đôi, tối đa 30 giây. Mỗi lần nối lại đọc token mới nhất;
 * nếu token đã hết hạn thì gọi một API nhẹ để interceptor của api.js tự làm mới token trước.
 *
 * @returns hàm ngắt kết nối
 */
export function connectNotificationSocket({ onMessage, onStatus }) {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  const client = new Client({
    brokerURL: `${proto}://${window.location.host}/notification/ws`,
    reconnectDelay: 3000,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    maxReconnectDelay: 30000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      let token = userTokenStore.get();
      if (!token || isTokenExpired(token)) {
        try {
          await notificationApi.counts();
        } catch {
          // interceptor đã xử lý (làm mới token hoặc đưa về trang đăng nhập)
        }
        token = userTokenStore.get();
      }
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
    },
    onConnect: () => {
      onStatus?.('connected');
      client.subscribe('/user/queue/notifications', (frame) => {
        try {
          onMessage?.(JSON.parse(frame.body));
        } catch {
          // bỏ qua thông điệp hỏng
        }
      });
    },
    onWebSocketClose: () => onStatus?.('disconnected'),
    onStompError: () => onStatus?.('error'),
  });
  client.activate();
  return () => {
    client.deactivate();
  };
}
