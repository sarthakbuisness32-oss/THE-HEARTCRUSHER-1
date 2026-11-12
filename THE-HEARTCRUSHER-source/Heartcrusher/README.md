# THE HEARTCRUSHER

A Paper 26.1.2 plugin for a scripted Minecraft series.

## Features

- Custom Mace named **THE HEARTCRUSHER**
- Shift + Right Click: Divine Piercer
- Shift + Left Click: Heaven's Wrath
- 8-second cooldown for each ability
- Custom recipe
- Strength I, Fire Resistance II, and Speed I while held
- Wielder protected from explosion damage during ability use
- Explosions damage other entities and break blocks

## Crafting recipe

```text
[   ] [Nether Star    ] [   ]
[Netherite] [Heavy Core] [Netherite]
[   ] [Breeze Rod     ] [   ]
```

## Give command

`/heartcrusher give <player>`

Permission: `heartcrusher.admin` (default: op)

## Build without installing Java/Maven locally

1. Create a new GitHub repository.
2. Upload the contents of this project ZIP, keeping the folders in their exact locations.
3. Open the repository's **Actions** tab.
4. Select **Build THE HEARTCRUSHER** and run it, or push a commit to trigger it.
5. Open the completed workflow run and download the **THE-HEARTCRUSHER** artifact.
6. Put the JAR in your Paper server's `plugins` folder and restart the server.

Paper 26.1.2 servers require Java 25. The GitHub workflow uses Java 25 to build.

## Performance note

Divine Piercer uses a configurable vertical chain of explosions rather than creating hundreds of primed TNT entities at once. The default spacing is 8 blocks. Heaven's Wrath uses 8 rings with 8 TNT per ring (64 TNT total), with slightly staggered fuse times. Large block-damaging explosions can still cause server load and permanently alter terrain. Test in a backup world first.

Settings are in `plugins/Heartcrusher/config.yml`.
