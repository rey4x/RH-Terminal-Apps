# RH Terminal Lite

RH Terminal Lite is a lightweight, customizable Android terminal. It features a built-in package manager for BeanShell addons, multi-tab sessions, integrated AI helper (Gemini/OpenAI/DeepSeek), a local live server, and a file manager. Built to help developers code, automate, and manage files efficiently directly from their mobile devices.

Developed entirely on Android using AIDE (Java).

## 🌟 Key Features

* **Multi-Tab Sessions:** Run multiple tasks simultaneously. Tabs can be configured to run in the background using Android services.
* **Package Manager (`pkg`):** Install, list, and remove custom BeanShell-based addons dynamically.
* **AI Assistant Integration:** Built-in AI helper. Configure your API keys to ask questions and generate code directly from the terminal. Supports Gemini, ChatGPT, and DeepSeek.
* **Local Live Server:** Host local HTML/JS/CSS files with a built-in HTTP server (`live` command). Includes an optional `--debug` mode that auto-injects Eruda for easy mobile browser debugging.
* **Advanced File Manager:** Batch rename, sort, and move files easily using `file rename` and `file destination`.
* **Network & Remote Tools:** Built-in `curl` for HTTP requests and `ssh` for remote server execution.
* **Customizable UI:** Change text size and terminal prompt strings dynamically via the settings menu.
* **Quick Shortcuts:** Extra on-screen keys for ESC, TAB, UP, DOWN, and LS to speed up typing.
* **Storage Access Framework (SAF):** Full read/write support for external SD cards.

## 🚀 Installation

1. Download the latest `app-release.apk` from the [Releases] tab.
2. Install the APK on your Android device.
3. Open the app and grant the required Storage permissions (type `saf` to open the directory picker for SD Card access).

## 💻 Built-in Commands

Here is a list of native commands supported out of the box:

### Navigation & Core
* `ls` - List files and directories in the current path with color-coded extensions.
* `cd <path>` - Change the current directory.
* `clear` - Clear the terminal output for the current session.
* `help` - Display the help manual.

### Package & Addon Management
* `pkg install <addon_name_or_url>` - Install a new BeanShell addon.
* `pkg list` - List all installed addons.
* `pkg remove <addon_name>` - Delete an installed addon.

### AI Integration
* `ai <provider> -m <model> -k <apikey>` - Configure the AI provider (e.g., `ai gemini -m gemini-1.5-flash -k YOUR_KEY`).
* `<provider> <prompt>` - Ask the AI a question (e.g., `gemini write a java hello world`).

### Web Development
* `live --dir <file.html> --port <port> [--debug]` - Start a local web server. Use `--debug` to inject Eruda console.
* `stop live` - Stop the running live server.

### File Management
* `file rename <dir> -n <pattern> -f <type> [-r]` - Batch rename files. Supports `{name}`, `{date}`, and `{nb}` counters.
* `file destination <src_dir> -t <type> -d <dest_dir>` - Bulk move files by type (image, video, audio, archive).

### Networking
* `curl <url>` - Send GET requests. Supports `-X`, `-d`, `-H`, and `-o` arguments.
* `defcurl <name> <url>` - Save a URL macro.
* `ssh <user> <pass> <host> <cmd>` - Execute a remote command via SSH.

## 🛠 Tech Stack

* **Language:** Java 7
* **IDE:** AIDE (Android IDE)
* **Libraries:** BeanShell (for addon scripting), JSch (for SSH), org.json (for JSON parsing).

## 📝 License

This project is open-source and available under the MIT License.
