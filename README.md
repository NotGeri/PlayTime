# PlayTime

A basic `/playtop` playtime leaderboard and personal online time.


## Configuration:
Here is the default config.
```yml
messages:
  permission: "&cYou do not have access to this command."
  console: "&cThis command may not be used from the console."
  player: "&cUnable to find a player by that name."
  reload: "&aReloaded values (stonks)"
  invalid: "&cInvalid arguments given!"
  loading: "&cThe leaderboard is still loading, please try again in a few seconds!"
  playtime: "&7Playtime for &3%player%&7: &7%playtime_time%&7! &8(&b#%playtime_position%&8)"
  playtop:
    head: "\n&8-=[ &3&lPlaytime Leaderboard &8]=-"
    lines: "&b#%playtime_position% &3%player% &7- &7&o%playtime_time%"
    foot: "&7"
  playtime-format: "%days%d %hours%hrs %minutes%mins"

playtime-top: 10
```

## Permissions & Commands
- `/playtime`, `/pt` - (playtime.use, playtime.use.others)
- `/playtop`, `/ptop` - (playtime.top, playtime.top.unlimited)
- `/playtopreload` - (playtime.reload)

## Placeholders:
- `%playtime_time%` - The player's play time (formatted with the playtime-format setting below)
- `%playtime_position%` - The player's position on /playtop
