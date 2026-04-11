import { atom } from "jotai"

export const ACTIVE_CONVERSATION_INITIAL = null as string | null
export const IS_STREAMING_INITIAL = false

export const activeConversationIdAtom = atom<string | null>(ACTIVE_CONVERSATION_INITIAL)
export const isStreamingAtom = atom<boolean>(IS_STREAMING_INITIAL)
