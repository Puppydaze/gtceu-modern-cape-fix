# GTCEu: Modern Cape Fix

EVERYTHING HERE IS GENERATED WITH CLAUDE AI. I, PUPPYDAZE, TAKE NO CREDIT FOR ANY OF THE CODE OR TEXT WRITTEN IN THIS REPO.

A small client-side Forge mod for Minecraft 1.20.1 that stops Mojang capes from disappearing when
[GregTech CEu Modern](https://github.com/GregTechCEu/GregTech-Modern) is installed
([GregTech-Modern#3866](https://github.com/GregTechCEu/GregTech-Modern/issues/3866)).

## The bug

GTCEu's `RenderPlayerEvent.Pre` handler saves each player's Mojang cape the first time the player is
drawn, and writes that saved value back every frame unless a GTCEu cape is selected. It never
re-reads it. If the first draw happens before the cape has finished downloading, it saves "no cape"
and wipes the real cape every frame for the rest of the session.

That early draw happens when you join a world in third person, or when a shaderpack draws your
player into its shadow map. Joining in first person with shader shadows off usually avoids it.

## The fix

The mod listens to the same event on both sides of GTCEu's handler:

- before it, to remember the player's real cape whenever Minecraft has supplied one;
- after it, to put that cape back when GTCEu left none and the player has no GTCEu cape selected.

GTCEu itself isn't modified and GTCEu capes still take priority. The mod has no mixins, does
nothing on dedicated servers, and only needs to be installed on the client. GTCEu is reached by
reflection; if a GTCEu version doesn't match, the mod logs a warning and switches itself off.

## Install

Put `gtceu-modern-cape-fix-1.20.1-<version>.jar` in your `mods` folder. Requires Minecraft 1.20.1,
Forge 47+ and GTCEu Modern (tested with 7.5.3). The log shows `GTCEu: Modern Cape Fix active` the
first time a player is drawn.

## Build

`build.ps1` compiles without Gradle, using JDK 17 and the SRG-named Minecraft and Forge 47.4.13
jars from a [Prism Launcher](https://prismlauncher.org/) install that has launched Forge 1.20.1
at least once:

```powershell
# JDK 17 in .jdk\ or JAVA_HOME
.\build.ps1
```

The jar is written to `build\`.
