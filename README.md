<img align="right" height="250" width="250" src="https://raw.githubusercontent.com/CompeyDev/pronounspls/refs/heads/main/src/main/resources/assets/pronounspls/icon.png" />

# Pronouns, Please!

<div>
  <a href="https://modrinth.com/mod/pronounspls">
    <img alt="modrinth" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/available/modrinth_vector.svg" />
  </a>
  <a href="https://fabricmc.net">
    <img alt="fabric" height="56" src="https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg" />
  </a>
</div>

<br />

A lightweight, dependency-free and fully server-side Fabric mod that lets players set their preferred pronouns to be displayed
the player list, nameplates, and chat. Integrates with [PronounDB](https://pronoundb.org) as well for extra features for players
that have it linked to their Minecraft account.

## Features

- Pronouns are displayed in the player list, above player heads, and in chat messages
- Pronouns are fully localized and a player sees them in their own preferred client language
- Cute pride flag decorations from PronounDB if equipped by the player
- Fully server-side, no mods to be installed on the client

## Commands

| Command                              | Description                                   |
| ------------------------------------ | --------------------------------------------- |
| `/pronounspls get [player]`          | Get your or another player's current pronouns |
| `/pronounspls set pronoun <pronoun>` | Set your pronouns                             |
| `/pronounspls refresh`               | Refresh your pronouns from PronounDB          |
| `/pronounspls help`                  | Show all available commands                   |

## Technical Summary

I am pretty proud of some of the creative solutions I had to come up while making this mod, so I thought it might be worth documenting
them below! :D

### Virtual Team Packets

Nameplates and player list prefixes make use of Minecraft's scoreboard team system under the hood, which does not support per-player
name prefixes and suffixes. Registering real teams on the server scoreboard could conflict with actual objectives and also prevent
localization (see below), we construct `ClientboundSetPlayerTeamPacket` instances backed by throwaway `Scoreboard` and `PlayerTeam` 
objects that are never registered persistently. These packets are sent directly to each client's network handler and immediately 
discarded server-side, leaving no trace on the actual scoreboard.

### Per-Client Translation

Typically, in Minecraft's translation model (and in general, too), translatable text is sent as special keys to the client, and the
actual translation is performed by the client using their local locale files. However, in order to not require special resources on
each client, we intercept the packets per-recipient and resolve the player's pronoun translation key into their preferred language
before the packet is built and dispatched.

We use the same approach for chat messages, where we hook into `PlayerList.broadcastChatMessage`, and embed a carrier containing the 
pronoun as a translation key. In `ServerPlayer.sendChatMessage` where the packets are to be dispatched, we translate this to the recipient's
language before it is actually sent.

### PronounDB Integration & Caching

PronounDB lookups are cached in our own API client and held via weak references in a keyed store that tags each entry by source
identifying it as "custom" (set via `/pronounspls set`) or fetched from PronounDB. This means cached PronounDB results do not need to be
refetched upon every join or reload. When a refresh is requested, the PronounDB cache entry is invalidated, which both releases the weak
reference in the pronouns store and triggers a refetch. Once the refetch resolves, the new value is stored and the virtual team packets are
automatically as the reference to it held in the store had been invalidated.
