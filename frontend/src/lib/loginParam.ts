/** 메일 링크(가입 완료 등)에서 돌아올 때 로그인 창을 바로 열기 위한 주소 파라미터(?login=1). */
export function wantsLoginOnLoad(): boolean {
  return new URLSearchParams(window.location.search).get('login') === '1'
}
