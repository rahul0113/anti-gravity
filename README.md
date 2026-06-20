# Anti-Gravity Vibe Coder

> A native Android vibe coding app powered by OpenCode CLI + Termux.

## Features
- 🤖 Full OpenCode CLI integration via Termux IPC
- 🎤 Voice-to-prompt via Android Speech Recognition
- 🖥️ Landscape code editor with file explorer
- 👁️ Built-in WebView preview for localhost servers
- ⚡ Auto-Chrome redirect when `npm run dev` is detected
- 🔌 MCP plugin support (via `~/.config/opencode/mcp.json`)
- ⌨️ Custom coding keyboard: COPY, PASTE, CTRL, ENTER, ESC, MIC

## Architecture
```
[Android Compose UI] ──intent──► [Termux IPC] ──► [opencode binary] ──► [LLM API]
         │                                                  │
    [TerminalView]                                   [Tool Execution]
    [EditorView]                                   (file, run, web)
    [PreviewView]
    [SettingsView]
```

## Building the APK

### Option A: GitHub Actions (Recommended – No PC needed)
1. Push this repo to GitHub
2. GitHub Actions will automatically build Debug + Release APKs
3. Download from the **Actions** tab → latest run → **Artifacts**

### Option B: Android Studio (PC)
```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Setup on Device
1. Install **Termux** from F-Droid
2. In Termux: `pkg install golang && go install github.com/opencode-ai/opencode@latest`
3. Configure API: `opencode auth login`
4. Install Anti-Gravity APK
5. In Settings, set Execution Mode to **TERMUX_SERVICE**
6. Grant `com.termux.permission.RUN_COMMAND` permission

## MCP Configuration
Create `~/.config/opencode/mcp.json` inside Termux:
```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"]
    }
  }
}
```
