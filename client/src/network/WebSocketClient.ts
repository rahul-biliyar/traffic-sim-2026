import { TickDelta, GameStateSnapshot, PlayerCommand } from "../types";

type MessageHandler = {
  onSnapshot: (snapshot: GameStateSnapshot) => void;
  onTick: (delta: TickDelta) => void;
  onError: (error: string) => void;
};

/**
 * WebSocket client with auto-reconnect and JSON message handling.
 */
export class WebSocketClient {
  private ws: WebSocket | null = null;
  private sessionId: string;
  private handlers: MessageHandler;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000;

  constructor(sessionId: string, handlers: MessageHandler) {
    this.sessionId = sessionId;
    this.handlers = handlers;
  }

  connect(): void {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${protocol}//${window.location.host}/ws/game/${this.sessionId}`;

    this.ws = new WebSocket(url);

    this.ws.onopen = () => {
      console.log("WebSocket connected");
      this.reconnectAttempts = 0;
    };

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data);
        if (msg.type === "snapshot") {
          this.handlers.onSnapshot(msg.data as GameStateSnapshot);
        } else if (msg.type === "tick") {
          this.handlers.onTick(msg.data as TickDelta);
        }
      } catch (e) {
        console.error("Failed to parse message:", e);
      }
    };

    this.ws.onclose = () => {
      console.log("WebSocket disconnected");
      this.attemptReconnect();
    };

    this.ws.onerror = (error) => {
      console.error("WebSocket error:", error);
      this.handlers.onError("Connection error");
    };
  }

  sendCommand(command: PlayerCommand): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(command));
    }
  }

  private attemptReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.handlers.onError("Connection lost. Please refresh.");
      return;
    }
    this.reconnectAttempts++;
    const delay = this.reconnectDelay * Math.pow(1.5, this.reconnectAttempts);
    setTimeout(() => this.connect(), Math.min(delay, 30000));
  }

  disconnect(): void {
    this.maxReconnectAttempts = 0; // prevent reconnect
    this.ws?.close();
  }
}
