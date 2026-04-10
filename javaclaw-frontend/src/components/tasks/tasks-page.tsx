import { useCallback, useState } from "react"

import { TaskDetailDialog } from "@/components/tasks/task-detail-dialog"
import { TaskListPanel } from "@/components/tasks/task-list-panel"

export function TasksPage() {
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null)
  const [dialogOpen, setDialogOpen] = useState(false)

  const handleSelectTask = useCallback((taskId: string) => {
    setSelectedTaskId(taskId)
    setDialogOpen(true)
  }, [])

  const handleDialogChange = useCallback((open: boolean) => {
    setDialogOpen(open)
    if (!open) {
      setSelectedTaskId(null)
    }
  }, [])

  return (
    <div className="flex h-full min-h-0 flex-col bg-background">
      <TaskListPanel
        onSelectTask={handleSelectTask}
        className="flex-1"
      />
      <TaskDetailDialog
        taskId={selectedTaskId}
        open={dialogOpen}
        onOpenChange={handleDialogChange}
      />
    </div>
  )
}
