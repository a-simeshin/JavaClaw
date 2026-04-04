import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import {
  type ConversationDto,
  createConversation,
  deleteConversation,
  listConversations,
  type Page,
} from "@/api/conversations"

const CONVERSATIONS_KEY = ["conversations"] as const

export function useConversationHistory(page = 0, size = 50) {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: [...CONVERSATIONS_KEY, page, size],
    queryFn: () => listConversations(page, size),
    staleTime: 10_000,
  })

  const create = useMutation({
    mutationFn: () => createConversation(),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_KEY })
    },
  })

  const remove = useMutation({
    mutationFn: (id: string) => deleteConversation(id),
    onMutate: async (id) => {
      await queryClient.cancelQueries({ queryKey: CONVERSATIONS_KEY })
      const previous = queryClient.getQueryData<Page<ConversationDto>>([
        ...CONVERSATIONS_KEY,
        page,
        size,
      ])
      if (previous) {
        queryClient.setQueryData<Page<ConversationDto>>(
          [...CONVERSATIONS_KEY, page, size],
          {
            ...previous,
            content: previous.content.filter((item) => item.id !== id),
            total: Math.max(0, previous.total - 1),
          },
        )
      }
      return { previous }
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.previous) {
        queryClient.setQueryData(
          [...CONVERSATIONS_KEY, page, size],
          ctx.previous,
        )
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: CONVERSATIONS_KEY })
    },
  })

  return {
    conversations: query.data?.content ?? [],
    total: query.data?.total ?? 0,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
    createConversation: create.mutateAsync,
    deleteConversation: remove.mutate,
    isDeleting: remove.isPending,
  }
}
