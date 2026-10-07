/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 카카오맵 JavaScript 키 (레포 루트 .env의 KAKAO_JS_KEY) */
  readonly KAKAO_JS_KEY?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
