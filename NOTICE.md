# DAP Ur Homie! - Forge 1.20.1 port (unofficial)

* Original mod: **DAP ur Homies** by **Anteryo** - https://www.curseforge.com/minecraft/mc-mods/dap-ur-homies
  (Fabric, Minecraft 1.21.1). All gameplay code, animations, sounds and textures are Anteryo's work.
  The original jar ships a CC0 LICENSE file and the CurseForge page lists MIT; its fabric.mod.json says
  "All Rights Reserved". This port is for private use; ask Anteryo before redistributing it.
* Animation playback in `com.cooptest.client.anim` re-implements the behaviour of
  **Player Animation Library** by ZigyTheBird (MIT, https://github.com/ZigyTheBird/PlayerAnimationLibrary),
  itself derived from GeckoLib (MIT) and KosmX's playerAnimator.
* `com.cooptest.fabricshim` is a small compatibility layer that maps the Fabric API calls the mod uses onto Forge.
