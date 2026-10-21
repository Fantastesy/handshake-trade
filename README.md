<p align="center"><img src="src/main/resources/assets/handshake-trade/icon.png" width="128" alt="Handshake Trade icon"></p>

# Handshake Trade

Safe player-to-player trading for Minecraft (Fabric). **Server-side only** – players join with a completely vanilla client.

`/trade <player>` sends a request that shows up in the middle of the other player's screen with clickable **[Accept]** / **[Deny]** buttons in chat. After accepting, both players get the same trade window (a 6-row chest): your offer on the left, your partner's offer on the right (live, read-only), Confirm and Cancel at the bottom. Items are only swapped when both players have confirmed.

## Features

- **Scam protection:** any change to an offer withdraws both confirmations, and confirming is blocked for a moment right after a change.
- **Nothing gets lost:** cancelling, closing the window, disconnecting, dying or a server stop hands every item back.
- **Crash-safe:** items inside a running trade are stored in the player's own save data (Fabric data attachment), together with the inventory. After a crash they are given back on the next join.
- **English and German**, picked from each player's game language – no resource pack needed.
- No permissions needed, every player can trade.

## Commands

| Command | |
|---|---|
| `/trade <player>` | Send a trade request (if both ask each other, the trade starts right away) |
| `/trade accept <player>` | Accept a request |
| `/trade deny <player>` | Deny a request |
| `/trade cancel` | Cancel your running trade or withdraw your requests |

## Config

`config/handshake-trade.json` is created on the first start:

```json
{
  "request_timeout_seconds": 60,
  "confirm_cooldown_seconds": 1.5,
  "max_distance": 0.0,
  "allow_cross_dimension": true
}
```

`max_distance` is in blocks, `0` means no limit.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5+
- [Fabric API](https://modrinth.com/mod/fabric-api)

## Building

Java 25 is required.

```
./gradlew build
```

The jar ends up in `build/libs/`.

## Download

[Modrinth](https://modrinth.com/mod/handshake-trade)

## License

[MIT](LICENSE)
