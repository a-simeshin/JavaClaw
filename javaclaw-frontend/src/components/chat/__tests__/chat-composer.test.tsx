import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { ChatComposer } from "@/components/chat/chat-composer"
import "@/test/i18n-test"

describe("ChatComposer", () => {
  it("disables send when input is empty", () => {
    const onSend = vi.fn()
    render(
      <ChatComposer value="" onChange={() => {}} onSend={onSend} />,
    )
    const sendButton = screen.getByRole("button", { name: /send/i })
    expect(sendButton).toBeDisabled()
  })

  it("enables send when value is non-empty", () => {
    render(
      <ChatComposer value="hello" onChange={() => {}} onSend={() => {}} />,
    )
    const sendButton = screen.getByRole("button", { name: /send/i })
    expect(sendButton).not.toBeDisabled()
  })

  it("calls onSend on Enter without shift", () => {
    const onSend = vi.fn()
    render(
      <ChatComposer value="hi" onChange={() => {}} onSend={onSend} />,
    )
    const textarea = screen.getByRole("textbox")
    fireEvent.keyDown(textarea, { key: "Enter" })
    expect(onSend).toHaveBeenCalledTimes(1)
  })

  it("does not send on Shift+Enter", () => {
    const onSend = vi.fn()
    render(
      <ChatComposer value="hi" onChange={() => {}} onSend={onSend} />,
    )
    const textarea = screen.getByRole("textbox")
    fireEvent.keyDown(textarea, { key: "Enter", shiftKey: true })
    expect(onSend).not.toHaveBeenCalled()
  })

  it("shows stop button while streaming and calls onStop on click", () => {
    const onStop = vi.fn()
    render(
      <ChatComposer
        value="streaming"
        onChange={() => {}}
        onSend={() => {}}
        onStop={onStop}
        isStreaming
      />,
    )
    const stopButton = screen.getByRole("button", { name: /stop streaming/i })
    fireEvent.click(stopButton)
    expect(onStop).toHaveBeenCalledTimes(1)
  })

  it("calls onChange when typing", () => {
    const onChange = vi.fn()
    render(
      <ChatComposer value="" onChange={onChange} onSend={() => {}} />,
    )
    const textarea = screen.getByRole("textbox")
    fireEvent.change(textarea, { target: { value: "abc" } })
    expect(onChange).toHaveBeenCalledWith("abc")
  })
})
