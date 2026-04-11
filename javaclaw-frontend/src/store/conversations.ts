import { atom } from "jotai"

export const CONVERSATION_SEARCH_INITIAL = ""

export const conversationSearchAtom = atom<string>(CONVERSATION_SEARCH_INITIAL)
