type Props = {
  active: boolean
  /** 스크린 리더가 읽을 빵집 이름 */
  name: string
  onToggle: () => void
  /** true면 아이콘 옆에 글자도 보여준다 (상세 화면용) */
  withLabel?: boolean
}

/** 즐겨찾기 하트 버튼. 누를 때마다 담기/해제가 바뀐다. */
export default function FavoriteButton({ active, name, onToggle, withLabel = false }: Props) {
  return (
    <button
      type="button"
      className={`${withLabel ? 'btn btn--small ' : ''}icon-btn${active ? ' icon-btn--on' : ''}`}
      aria-pressed={active}
      aria-label={`${name} 즐겨찾기`}
      title={active ? '즐겨찾기 해제' : '즐겨찾기에 추가'}
      onClick={onToggle}
    >
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true" focusable="false">
        <path
          d="M12 21s-7.5-4.6-9.7-9.4C.6 8 2.7 4 6.4 4c2.1 0 3.7 1 5.6 3 1.9-2 3.5-3 5.6-3 3.7 0 5.8 4 4.1 7.6C19.5 16.4 12 21 12 21z"
          fill={active ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="1.8"
          strokeLinejoin="round"
        />
      </svg>
      {withLabel && <span>{active ? '즐겨찾기 해제' : '즐겨찾기'}</span>}
    </button>
  )
}
