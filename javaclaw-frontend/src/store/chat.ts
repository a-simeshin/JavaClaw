import { atom } from "jotai"

export const activeConversationIdAtom = atom<string | null>(null)
export const isStreamingAtom = atom<boolean>(false)
