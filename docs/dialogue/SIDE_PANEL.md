# Side panel UI framework

Covers `indevo/dialogue/sidepanel/`: the shared custom panel shown in an interaction dialog's visual area, the button polling it uses, the press callback that replaces it, the frame renderers, and the button-object pattern for custom dialogs. Read it before building or changing an IE dialog panel. It does not cover the features that draw with it; see [Split fleet](../abilities/SPLIT_FLEET.md), [Research](RESEARCH.md), [Courier port](../industries/COURIER_PORT.md), [Pets](../industries/PETS.md), [Museum](../industries/MUSEUM.md), [Warehouses](../industries/WAREHOUSES.md).

Checked against Starsector 0.98a-RC8 and IE `cbdc2e1`. Recheck engine facts after a game update.

## Overview

Three patterns live here. `VisualCustomPanel` (adapted from Histidine's Nexerelin code) replaces the dialog's visual panel with a 600 x 600 scrollable tooltip and checks button state every frame through `InteractionDialogCustomPanelPlugin`. Newer side panels build the visual panel themselves on `RunnableButtonPanelPlugin` and pass a `Runnable` as the button id, run on press; no static state, no polling. `ButtonReportingCustomPanel` + `ButtonReportingDialogueDelegate` + `ButtonAction` are for `showCustomDialog` popups: the button id is a `ButtonAction` object that runs itself when pressed.

## Files

| File | Owner / role |
|---|---|
| `indevo/dialogue/sidepanel/VisualCustomPanel.java` | Static holder for the current `panel`, `tooltip`, `plugin`. `createPanel(dialog, replace)` calls `VisualPanelAPI.showCustomPanel` and makes a scrolling tooltip (`:46`); `addTooltipToPanel` adds it last (`:75`); `clearPanel` drops the refs (`:36`); `readdTooltip` re-shows the panel to reset the scroll extent. |
| `InteractionDialogCustomPanelPlugin.java` | Panel plugin for the visual panel. Keeps a list of `ButtonEntry`; `advance` calls `checkButton` on each (`:34`). `ButtonEntry.onToggle` fires when `button.isChecked()` differs from the cached state. `RadioButtonEntry` unchecks siblings. `render` is empty (no frame). |
| `FramedCustomPanelPlugin.java` | Draws corner brackets (`sideRatio` of each side, optional square corners) in a colour at 0.3 alpha. Used for ship lists and progress bars. |
| `NoFrameCustomPanelPlugin.java` | No-op plugin for layout-only sub panels (button rows). |
| `RunnableButtonPanelPlugin.java` | `BaseCustomUIPanelPlugin` whose `buttonPressed` runs a `Runnable` button id. Use it for the visual panel and for every sub panel that holds buttons. |
| `ButtonReportingCustomPanel.java` | `BaseCustomUIPanelPlugin` that forwards `buttonPressed` to a `ButtonReportingDialogueDelegate`; optional bracket frame with height override. |
| `ButtonReportingDialogueDelegate.java` | `BaseCustomDialogDelegate`; `reportButtonPressed` runs the id if it is a `ButtonAction` (`:7`). |
| `HelpIcon.java` | `?` icon for rules and rates that stay out of the panel text: vanilla `fleetScreen` `icon_more_info` sprite tinted with the base player colour, 20 px, hover tooltip. `HelpIcon.create(parent, tooltip -> ...)` returns the element; the caller positions it. Place it in the top right corner on the title line: title heading `w - HelpIcon.SIZE - PAD` wide, icon at `inTR(0, 0)` (Relic Lattice Work is the reference). Users: Senate, Academy, Ancient Laboratory relic services, Relic Lattice Work panels. |
| `ButtonAction.java`, `ButtonActionInterface.java` | Abstract button id object holding the `CustomDialogDelegate`; `execute()` is the click handler. |
| `EscapeBlockingDialoguePluginAPI.java` | `close()` hook. A dialog plugin stores itself in memory `$IndEvo_BlockESC`; `indevo/industries/petshop/listener/EscapeInterceptListener.java:34` consumes ESC and calls `close()`. |

## Data and ids

| Kind | Id / key | Where |
|---|---|---|
| Constants | `PANEL_WIDTH = 600`, `PANEL_HEIGHT = 600` | `VisualCustomPanel.java:14` |
| Memory | `$IndEvo_BlockESC` | `EscapeInterceptListener.BLOCK_ESC` |
| Memory (per user) | panel plugin instance keys such as `$IndEvo_tempDialogueInstance` (expire 0) | user dialogs |

## Registration and lifecycle

Nothing is registered. Everything is created by the dialog that uses it and lives only while that dialog is open. `VisualCustomPanel` holds static refs, so they survive into the next dialog unless `clearPanel()` is called on close (javadoc at `:33` calls this a memory leak).

Users:

| Pattern | Users |
|---|---|
| `VisualCustomPanel` + `InteractionDialogCustomPanelPlugin.ButtonEntry` | `ResearchProjectSidePanelCreator`, `ContractListSidePanelCreator`, `ContractSidePanelCreator` (+ their dialog plugins) |
| own visual panel + `RunnableButtonPanelPlugin` | `SplinterFleetSidePanelCreator` (+ `AbilityPanelDialoguePlugin`) |
| `ButtonReportingCustomPanel` + `ButtonAction` | `PetManagerDialogueDelegate`, `ParadeManagementDialogueDelegate`, `WarehouseAddSubmarketOptionProvider`, `warehouses/plugin/wi/WarehouseManagementDialogueDelegate` (folder excluded by `IndEvo.iml`) |
| own copy of `ButtonReportingCustomPanel` | `ChangelingIndustryDialogueDelegate.ButtonReportingCustomPanel` (inner class, `:229`) |
| `EscapeBlockingDialoguePluginAPI` | `ManageParadeDialoguePlugin`, `ParadeManagementDialogueDelegate`, `PetShopDialogPlugin` |

## Flow

### Visual side panel (dialog plugins)

`dialog plugin init -> VisualCustomPanel.createPanel(dialog, true) -> creator.showPanel -> build rows into VisualCustomPanel.getTooltip() -> addTooltipToPanel()`.

Layout conventions of the `VisualCustomPanel` creators (`ResearchProjectSidePanelCreator.java:25`); the split fleet slot rows follow them too:

- Constants: `ROW_WIDTH = PANEL_WIDTH - 20` so a right aligned toggle fits, `BUTTON_HEIGHT = 30`, `SELECT_BUTTON_WIDTH = 95`, `ARROW_BUTTON_WIDTH = 20`, `TEXT_FIELD_WIDTH = 80`, `SHIP_ICON_WIDTH = 48`, pads `opad = 10`, `spad = 3`.
- Each block: `addSectionHeading(name, MID, opad)`, then a row panel (`NoFrameCustomPanelPlugin`; split fleet uses `RunnableButtonPanelPlugin`), 50 high, holding one `createUIElement` per button, first `inTL(spad, opad)`, rest `rightOfMid(previous, opad)`, then `panelTooltip.addCustom(row, opad)`.
- Below it a `FramedCustomPanelPlugin(0.25f, Misc.getBasePlayerColor(), false)` panel `PANEL_WIDTH - 20` wide for ship icons or a progress bar (a disabled, checked `addAreaCheckbox` sized to the percentage).
- Buttons: `addButton(text, id, textColor, bgColor, Alignment.MID, CutStyle.C2_MENU, w, h, 0)`; enabled colours `Misc.getButtonTextColor()` / `Misc.getDarkPlayerColor()`, disabled `Misc.getTextColor()` / `Misc.getGrayColor()`, plus `setEnabled`. Action buttons use fixed fills (green `50,130,0`, red `160,30,20`, amber `200,160,10`).
- Toggles are small `addAreaCheckbox` buttons with player trio colours (`Misc.getBasePlayerColor()`, `getDarkPlayerColor()`, `getBrightPlayerColor()`), placed `inTR` on the row and given a vanilla style hover tooltip through `addTooltipTo` (research Stock toggle, `ResearchProjectSidePanelCreator.java:207`).
- Each button is wrapped in an anonymous `ButtonEntry` with `onToggle()` and added via `VisualCustomPanel.getPlugin().addButton(entry)`. Ids are strings `"button_<action>_<slot or project>"`, used only for logging.
- Status lines are `panelTooltip.addPara` with highlight or negative highlight colours under the block.
- Any click handler that changes state rebuilds the whole panel (`refreshCustomPanel()` -> `createPanel(dialog, true)`), so entries never need updating in place. Handlers find the dialog plugin through a static getter on a sector memory key with expire 0.

### Visual side panel with press callbacks

`dialog plugin -> new Creator(plugin).showPanel() -> dialog.getVisualPanel().showCustomPanel(w, h, new RunnableButtonPanelPlugin()) -> panel.createUIElement(w, h, true) -> rows -> panel.addUIElement(tooltip)`. Row panels are `panel.createCustomPanel(w, h, new RunnableButtonPanelPlugin())`; buttons are `addButton(text, (Runnable) () -> handler(), ...)`. The creator keeps the dialog plugin in a field, so handlers need no memory key, and each handler ends by rebuilding the panel. Slot rows follow the conventions above (`SplinterFleetSidePanelCreator.java:64`). The Command row on top is a custom panel with one nested custom panel per slot (`row.createCustomPanel`, `row.addComponent(column).inTL(x, 0)`); inside a column every button gets its own `createUIElement` placed with `inTL(x, y)` below a heading element, `y` from its `getHeightSoFar()`. Mode buttons, the Send/Take pair and the Auto-recall and Join fights toggles are `addAreaCheckbox` buttons: the press runs the `Runnable`, the rebuilt panel sets `setChecked` from the stored state. Hover text uses `addTooltipToPrevious` with `setShowTooltipWhileInactive(true)`.

### Custom dialog popups

`dialog.showCustomDialog(w, h, delegate extends ButtonReportingDialogueDelegate) -> panel.createCustomPanel(w, h, new ButtonReportingCustomPanel(this[, colour, -1])) -> addButton(text, new ButtonAction(this){ execute() }) -> click -> ButtonReportingCustomPanel.buttonPressed -> delegate.reportButtonPressed -> ButtonAction.execute()`.

## Contracts

- `VisualPanelAPI.showCustomPanel(float, float, CustomUIPanelPlugin)` returns the panel (`api/campaign/VisualPanelAPI.java:37`). Add the tooltip to the panel after filling it, or the scroll area does not cover the content (comment at `VisualCustomPanel.java:57`, `:73`).
- Buttons in a `createUIElement` element report to the owning panel's `CustomUIPanelPlugin.buttonPressed` (skill `docs/ui/widgets.md`, "Event routing"), so a button in a row sub panel reports to that row's plugin, not to the visual panel's. `RunnableButtonPanelPlugin` relies on that. The `VisualCustomPanel` rows use `NoFrameCustomPanelPlugin`, which ignores that callback, and detect clicks by polling `isChecked()` each frame instead (inferred: relies on a click flipping the checked state of these buttons; not traced into the engine).
- A `Runnable` button id must never reach a saved object (skill `docs/ui/widgets.md`, "Event routing"). Side panels are rebuilt on every change and never saved.
- `InteractionDialogCustomPanelPlugin` takes the player faction UI colour in its constructor, so it needs a loaded sector.
- `ButtonReportingCustomPanel.render` uses `pos` set by `positionChanged`; a panel rendered before positioning would NPE (inferred).
- UI guidance for new panels: skill `docs/ui/index.md`, `layout.md`, `widgets.md`.

## Known issues and dormant code

| Item | State |
|---|---|
| `VisualCustomPanel.readdTooltip` | unused |
| `InteractionDialogCustomPanelPlugin.RadioButtonEntry` | unused in IE |
| `ChangelingIndustryDialogueDelegate.ButtonReportingCustomPanel` | duplicate of the shared class |
| `FramedCustomPanelPlugin` / `ButtonReportingCustomPanel.renderBox` | duplicated bracket drawing code |

## Maintenance

Update this doc when a constant, the polling model or the button-object pattern changes, or a new user is added. Feature specific panel content belongs in that feature's doc. Follow [CLAUDE.md](../../CLAUDE.md#documentation-upkeep).
