export class ApiError extends Error {
  constructor(message, { status = 0, code = 'NETWORK_ERROR', fields = null } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fields = fields
  }
}
