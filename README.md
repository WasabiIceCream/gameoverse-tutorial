# Gameoverse Tutorial

Vanilla-style tutorial hints for new players on the Gameoverse server: a chain of steps from moving around to the
first iron pickaxe and the first Satiated Shield meal, teaching the server's changed mechanics one at a time, plus
one-off tips (tree felling, ore veins, Cozy, death and hearts, ...). Design: `docs/tutorial-design.md` in the server
project.

Both sides. The server decides: it reads `src/main/resources/tutorial/steps.json`, checks each online player twice a
second and tells the client which hint to show, update or take down; progress is saved on the player (Fabric
attachment, kept on death). The client draws hints in vanilla's tutorial-toast look with an item icon, in their own top-left corner
(under Controlify's left button-guide column when it shows; the top right already holds status effects, the Atlas
minimap, the compass and clock read-outs and every toast), fills in the player's own keybinds, uses a step's `.text.controller` wording when Controlify is in controller mode, switches vanilla's own
tutorial off, and reports when a screen the current step waits for opens.

Players with more than `veteran_play_hours` (2) of play time when they first meet the mod start with it off.
Commands, for anyone about themselves: `/tutorial` (status), `/tutorial off`, `/tutorial on`, `/tutorial restart`.

## Steps

Text: `assets/gameoverse_tutorial/lang/en_us.json`, keys `gameoverse_tutorial.<id>.title` and `.text` (and optional
`.text.controller`); `keys` in the step fill the text's `%s` slots with keybinds. Chain steps show in order until
`until` holds; one that already holds when reached is skipped silently. Tips show once, for `seconds`, when `when`
first holds (at most one every 20 seconds).

Conditions:

| Condition | Holds when |
|---|---|
| `{"item": "id" or "#tag", "count": n, "model": "ns:model"}` | the player carries at least n (default 1), optionally with that item model |
| `{"stat": "minecraft:crafted", "key": "id" or "#tag", "min": n, "since_shown": true}` | a statistic (summed over a tag) reaches n; `since_shown` counts from when the step showed |
| `{"advancement": "ns:id"}` | the advancement is done |
| `{"effect": "ns:id"}` | the effect is active |
| `{"permission": ["group.verified", ...]}` | any node passes (Fabric Permissions API; passes if no mod ships it) |
| `{"screen": ["class.Name", ...]}` | the client opened a screen of (a subclass of) one of these classes while the step showed |
| `{"chain_done": "step"}` | that chain step is done |
| `{"shown_seconds": n}` | the step has been on screen n seconds |
| `{"play_minutes": n}` | total play time reaches n minutes |
| `{"any": [...]}`, `{"all": [...]}` | any / all of the listed conditions |

A counting `item` or `stat` condition gives the toast a progress bar.

## License

MIT.
