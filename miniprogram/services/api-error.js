class ApiError extends Error {
  constructor(code, message) {
    super(message || '服务暂时不可用')
    this.name = 'ApiError'
    this.code = code || 'UNKNOWN_ERROR'
    this.status = 0
    this.fields = null
    this.requestId = ''
  }
}

module.exports = { ApiError }
