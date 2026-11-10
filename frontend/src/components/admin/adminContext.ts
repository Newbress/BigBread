import { createContext, useContext } from 'react'

/** 처리하고 나면 상단 메뉴의 '처리할 일' 숫자를 다시 불러오게 한다. */
export const AdminContext = createContext<{ refreshSummary: () => void }>({ refreshSummary: () => {} })
export const useAdmin = () => useContext(AdminContext)
