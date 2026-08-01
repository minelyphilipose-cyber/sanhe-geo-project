export class ExclusiveOperationTracker {
  constructor() {
    this.operations = new Map()
  }

  has(key) {
    return this.operations.has(key)
  }

  get(key) {
    return this.operations.get(key) || null
  }

  size() {
    return this.operations.size
  }

  start(key, operationFactory) {
    const existing = this.get(key)
    if (existing) return existing

    let tracked
    const operation = Promise.resolve().then(operationFactory)
    tracked = operation.finally(() => {
      if (this.operations.get(key) === tracked) {
        this.operations.delete(key)
      }
    })
    this.operations.set(key, tracked)
    return tracked
  }

  async wait(key, timeoutMs, label = 'exclusive operation') {
    const operation = this.get(key)
    if (!operation) return null
    if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) return operation

    let timer
    try {
      return await Promise.race([
        operation,
        new Promise((_, reject) => {
          timer = setTimeout(
            () => reject(new Error(`${label} timed out after ${timeoutMs}ms`)),
            timeoutMs,
          )
          timer.unref?.()
        }),
      ])
    } finally {
      clearTimeout(timer)
    }
  }
}
