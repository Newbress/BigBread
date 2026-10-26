/** document.cookie 문자열에서 이름이 같은 쿠키 값을 꺼낸다. 없으면 null. */
export function readCookie(name: string, cookie: string = document.cookie): string | null {
  for (const part of cookie.split(';')) {
    const index = part.indexOf('=')
    if (index < 0) continue
    if (part.slice(0, index).trim() !== name) continue
    try {
      return decodeURIComponent(part.slice(index + 1).trim())
    } catch {
      return part.slice(index + 1).trim()
    }
  }
  return null
}
