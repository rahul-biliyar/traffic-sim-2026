import { TickDelta } from "../types";

/**
 * Buffers tick state for client-side interpolation between server updates.
 */
export class StateBuffer {
  private buffer: TickDelta[] = [];
  private maxSize = 5;

  push(delta: TickDelta): void {
    this.buffer.push(delta);
    if (this.buffer.length > this.maxSize) {
      this.buffer.shift();
    }
  }

  getCurrent(): TickDelta | null {
    return this.buffer.length > 0 ? this.buffer[this.buffer.length - 1] : null;
  }

  getPrevious(): TickDelta | null {
    return this.buffer.length > 1 ? this.buffer[this.buffer.length - 2] : null;
  }

  getInterpolationPair(): [TickDelta, TickDelta] | null {
    if (this.buffer.length < 2) return null;
    return [
      this.buffer[this.buffer.length - 2],
      this.buffer[this.buffer.length - 1],
    ];
  }

  clear(): void {
    this.buffer = [];
  }
}
