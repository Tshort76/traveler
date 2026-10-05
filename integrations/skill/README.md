# The traveler-trip skill (ChatGPT and Claude)

Two skills, in the open Agent Skills format (a folder with a `SKILL.md`) that ChatGPT and Claude both load:

- `traveler-trip` writes Traveler trip files. `preamble.md` holds its front matter and working rules; the rest of its `SKILL.md` is `prompts/itinerary-instructions.md`.
- `trip-workbook` builds a planning spreadsheet and a Google My Maps file. `workbook-preamble.md` holds its front matter; the rest is `prompts/workbook-instructions.md`.

OpenAI is retiring custom GPTs on 11 December 2026 in favour of plugins built from skills, so this replaces a custom GPT.

## Build

```bash
make skill
```

This writes `build/skill/`:

| Output | Use it for |
| --- | --- |
| `<skill>.zip` | Uploading a skill to Claude, or to ChatGPT where it accepts skills (one top-level skill folder) |
| `chatgpt-project/` | ChatGPT on a personal plan: the same instructions as flat files for a ChatGPT Project |
| `traveler-plugin.zip`, `traveler-plugin/` | A skills-only plugin wrapping both skills, for OpenAI's plugin submission or Codex |
| `traveler-trip/`, `trip-workbook/` | The unzipped skills, for Claude Code or Codex |

Rebuild and re-upload whenever the prompt or the format changes.

## ChatGPT

On a personal plan (Plus or Pro), ChatGPT on the web and phone refuses uploaded skills and fails with "Couldn't add plugin". Reports say the desktop app and Business, Enterprise and Edu plans accept them. A plugin reaches a personal account only through OpenAI's reviewed directory, which is a developer submission and not worth it for one user.

**Use a Project instead**, which works on any plan, on the web and on the phone:

1. Create a Project (say "Travel") and paste `chatgpt-project/INSTRUCTIONS.txt` into its instructions.
2. Add the other files in `chatgpt-project/` as project files, plus your travel profile if you want your preferences followed.
3. Plan trips in chats inside that Project. It writes and checks files with ChatGPT's code tool, so the chat must be able to run code.

If you use the desktop app or a business plan, you can instead upload `traveler-trip.zip` and `trip-workbook.zip` under **Skills → Create → Upload from your computer**.

## Claude

- **claude.ai**: Settings → Capabilities → Skills → upload `traveler-trip.zip` and `trip-workbook.zip`. This can replace the Concierge project's itinerary files: the project's own instructions and profile stay, and the skill brings the format, the validator and the example.
- **Claude Code**: copy `build/skill/traveler-trip/` and `build/skill/trip-workbook/` into `~/.claude/skills/`.

## From the phone

1. Ask for a trip. The reply is a short summary and a `<trip-id>.r1.trip.json` download.
2. Download it and open it with **Traveler**. Or say "paste", copy the JSON block, and use **Paste trip JSON** on Traveler's trips screen.
3. To revise: in Traveler, tap the share icon on the trip's overview, send the file to the chat, and say what to change. Import the file that comes back; the app merges it with your edits.
