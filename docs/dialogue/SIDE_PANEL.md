# Dialog popup helpers

Covers `indevo/dialogue/sidepanel/`: the button object pattern of the older `showCustomDialog` popups, the `?` help icon and the ESC hook for dialog plugins. Read it before changing one of the popups listed under Users. New panels and reworks use the kit instead: [UI](../core/UI.md). It does not cover the features that draw with these classes; see [Pets](../industries/PETS.md), [Museum](../industries/MUSEUM.md), [Warehouses](../industries/WAREHOUSES.md), [Orbital Works slot](../industries/ORBITAL_WORKS_SLOT.md).

Checked against Starsector 0.98a-RC8. Recheck engine facts after a game update.

## Overview

`ButtonReportingCustomPanel` + `ButtonReportingDialogueDelegate` + `ButtonAction` are for `showCustomDialog` popups: the button id is a `ButtonAction` object that runs itself when pressed. `HelpIcon` is the `?` icon every IE panel uses for rules and rates. `EscapeBlockingDialoguePluginAPI` lets a dialog plugin without options close on ESC.

## Files

| File | Owner / role |
|---|---|
| `ButtonReportingCustomPanel.java` | `BaseCustomUIPanelPlugin` that forwards `buttonPressed` to a `ButtonReportingDialogueDelegate`; optional bracket frame with height override. |
| `ButtonReportingDialogueDelegate.java` | `BaseCustomDialogDelegate`; `reportButtonPressed` runs the id if it is a `ButtonAction` (`:7`). |
| `ButtonAction.java`, `ButtonActionInterface.java` | Abstract button id object holding the `CustomDialogDelegate`; `execute()` is the click handler. |
| `HelpIcon.java` | `?` icon for rules and rates that stay out of the panel text: vanilla `fleetScreen` `icon_more_info` sprite tinted with the base player colour, 20 px, hover tooltip. `HelpIcon.create(parent, tooltip -> ...)` returns the element; the caller positions it. Kit panels get it through `UiFrame.title` ([UI](../core/UI.md#uiframe)); the Orbital Works slot panel places it by hand at `inTR(0, 0)` on the title line. |
| `EscapeBlockingDialoguePluginAPI.java` | `close()` hook. A dialog plugin stores itself in memory `$IndEvo_BlockESC`; `indevo/industries/petshop/listener/EscapeInterceptListener.java:34` consumes ESC and calls `close()`. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Memory | `$IndEvo_BlockESC` | `EscapeInterceptListener.BLOCK_ESC` |

## Registration and lifecycle

Nothing is registered. Everything is created by the dialog that uses it and lives only while that dialog is open.

Users:

| Pattern | Users |
|---|---|
| `ButtonReportingCustomPanel` + `ButtonAction` | `PetManagerDialogueDelegate`, `ParadeManagementDialogueDelegate`, `OrbitalWorksSlotPanelDelegate`, `WarehouseAddSubmarketOptionProvider`, `warehouses/plugin/wi/WarehouseManagementDialogueDelegate` (folder excluded by `IndEvo.iml`) |
| `ButtonReportingDialogueDelegate` alone | `EdictConfirmDialog` |
| own copy of `ButtonReportingCustomPanel` | `ChangelingIndustryDialogueDelegate.ButtonReportingCustomPanel` (inner class, `:230`) |
| `EscapeBlockingDialoguePluginAPI` | `AcademyDialogPlugin`, `SalvageYardsDialogPlugin`, `CourierPortDialoguePlugin`, `ManageParadeDialoguePlugin`, `ParadeManagementDialogueDelegate`, `PetShopDialogPlugin` |

## Flow

### Custom dialog popups

`dialog.showCustomDialog(w, h, delegate extends ButtonReportingDialogueDelegate) -> panel.createCustomPanel(w, h, new ButtonReportingCustomPanel(this[, colour, -1])) -> addButton(text, new ButtonAction(this){ execute() }) -> click -> ButtonReportingCustomPanel.buttonPressed -> delegate.reportButtonPressed -> ButtonAction.execute()`.

### ESC on a dialog plugin without options

`plugin.advance -> memory.set($IndEvo_BlockESC, plugin, 1f)` while no sub dialog is up `-> EscapeInterceptListener` consumes ESC `-> plugin.close()`. `EscBlockerMemoryIterator` (transient script, added by the plugin's `init` when missing) counts the key down, so it is gone two frames after the plugin stops setting it. While a vanilla picker is open the plugin does not set the key (Courier Port also unsets it before the picker opens), so ESC reaches the picker.

## Contracts

- Buttons in a `createUIElement` element report to the owning panel's `CustomUIPanelPlugin.buttonPressed` (skill `docs/ui/widgets.md`, "Event routing"), so a button in a sub panel reports to that sub panel's plugin.
- A `ButtonAction` or `Runnable` button id must never reach a saved object (skill `docs/ui/widgets.md`, "Event routing"). Popups are rebuilt on every change and never saved.
- `ButtonReportingCustomPanel.render` uses `pos` set by `positionChanged`; a panel rendered before positioning would NPE (inferred).
- UI guidance for new panels: [UI](../core/UI.md), skill `docs/ui/index.md`, `layout.md`, `widgets.md`.

## Known issues and dormant code

| Item | State |
|---|---|
| `ChangelingIndustryDialogueDelegate.ButtonReportingCustomPanel` | duplicate of the shared class |

## Maintenance

Update this doc when the button object pattern, the ESC hook or `HelpIcon` changes, or a user is added or moved to the kit. Feature specific panel content belongs in that feature's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
