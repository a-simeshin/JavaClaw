#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#     "python-dotenv",
#     "urllib3",
# ]
# ///

"""
Telegram notification utility for Claude Code hooks.

Usage as module:
    from utils.telegram_notify import send_telegram

    send_telegram("Build complete!")
    send_telegram("Task failed", level="error")

Usage as script:
    uv run utils/telegram_notify.py "Build complete!"
    uv run utils/telegram_notify.py --level error "Task failed"

Env vars:
    TELEGRAM_BOT_TOKEN  — Bot token from @BotFather
    TELEGRAM_CHAT_ID    — Your chat ID
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Optional
from urllib.request import urlopen, Request
from urllib.error import URLError

try:
    from dotenv import load_dotenv
    # Try project root .env first (works when called from any directory)
    _script_dir = Path(__file__).resolve().parent
    _project_root = _script_dir.parent.parent.parent  # utils -> hooks -> .claude -> project root
    _env_path = _project_root / ".env"
    if _env_path.exists():
        load_dotenv(_env_path)
    else:
        load_dotenv()  # fallback to cwd
except ImportError:
    pass


LEVEL_ICONS = {
    "info": "\u2139\ufe0f",
    "success": "\u2705",
    "warning": "\u26a0\ufe0f",
    "error": "\u274c",
    "progress": "\u23f3",
    "plan": "\ud83d\udccb",
    "build": "\ud83d\udd27",
    "test": "\ud83e\uddea",
    "validate": "\ud83d\udd0d",
    "deploy": "\ud83d\ude80",
}


def send_telegram(
    message: str,
    level: str = "info",
    bot_token: Optional[str] = None,
    chat_id: Optional[str] = None,
    parse_mode: str = "HTML",
) -> bool:
    """
    Send a message to Telegram.

    Args:
        message: Text to send
        level: Message level (info/success/warning/error/progress/plan/build/test/validate/deploy)
        bot_token: Override bot token (default: from env)
        chat_id: Override chat ID (default: from env)
        parse_mode: Telegram parse mode (HTML or Markdown)

    Returns:
        True if sent successfully, False otherwise
    """
    token = bot_token or os.getenv("TELEGRAM_BOT_TOKEN", "")
    chat = chat_id or os.getenv("TELEGRAM_CHAT_ID", "")

    if not token or not chat:
        return False

    icon = LEVEL_ICONS.get(level, LEVEL_ICONS["info"])
    full_message = f"{icon} {message}"

    url = f"https://api.telegram.org/bot{token}/sendMessage"
    payload = json.dumps({
        "chat_id": chat,
        "text": full_message,
        "parse_mode": parse_mode,
        "disable_web_page_preview": True,
    }).encode("utf-8")

    req = Request(url, data=payload, headers={"Content-Type": "application/json"})

    try:
        with urlopen(req, timeout=10) as resp:
            return resp.status == 200
    except (URLError, OSError, ValueError):
        return False


def main():
    parser = argparse.ArgumentParser(description="Send Telegram notification")
    parser.add_argument("message", help="Message to send")
    parser.add_argument("--level", default="info", choices=list(LEVEL_ICONS.keys()),
                        help="Message level/icon")
    parser.add_argument("--token", default=None, help="Bot token override")
    parser.add_argument("--chat-id", default=None, help="Chat ID override")
    args = parser.parse_args()

    ok = send_telegram(args.message, level=args.level, bot_token=args.token, chat_id=args.chat_id)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
