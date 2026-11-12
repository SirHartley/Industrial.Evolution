# UI kit

Shared builder for IE custom panels in `jars/src/indevo/utils/ui/`. Read it before building or changing any IE panel (custom dialog or side panel). It owns the layout contract, the helpers and the rebuild and scroll pattern. Feature behaviour stays in the feature docs; the older side panel classes in `dialogue/sidepanel` are in [SIDE_PANEL](../dialogue/SIDE_PANEL.md).

Checked against Starsector 0.98a-RC8. Paths below are relative to `jars/src/indevo/utils/ui/`; `obf/` and `api/` paths point into the `starsector-knowledge` skill.

Reference implementation: the Academy panel (`industries/academy/plugins/AcademyPanelDelegate.java`, `AcademyDetail.java`, `AcademyView.java`; [Academy](../industries/ACADEMY.md#panel)). The detachment panel, Salvage Yards, Courier Port, Senate Edict, Ancient Laboratory, Engineering Hub and Research panels are built the same way; their feature docs own the content.

The helpers mirror the mockup board kit (`kit.py`): a board line `text(58,7,140,name,'ins15',GRAY)` becomes `Ui.text(row, 58, 7, 140, name, Ui.gray())`.

## Layout contract

Every IE panel follows these rules.

1. Absolute layout. Compute every rectangle from the panel size and place children with `inTL(x, y)`. Never chain positions off text of unknown height. A region of unknown length is a scroll column (`UiScroll`) with a fixed outer height.
2. No overlap and no cut off text for any data. Think of the worst case of each field: 40 character names, "Nova Maxios Prime Station", 9 digit credits, 300 day counters, 14 skills, 30 colonies, 200 entries, an empty list.
   - One line text in a fixed width goes through `Ui.text`/`Ui.runs`, which shorten with `...` and add the full text as tooltip.
   - Wrapped text gets a fixed width and its measured height (`Ui.wrap`, `Ui.wrapHeight`); what follows is placed from that height, or the block scrolls.
   - Right aligned numbers get their own measured column (`UiData.value`, `grid`, `costrow`).
   - Icon rows have fixed cells, at most two rows, then a `+N` cell with the rest as tooltip (`UiImages.iconrow`).
   - Lists scroll and keep their offset across a rebuild (`UiScroll.Offsets`).
   - At scroll offset 0 nothing is cut in half by a scroll edge: fixed pitch lists end on a whole row (`UiScroll.wholeRows`), every `UiScroll.Column` ends its viewport on the last block that fits whole and leaves the rest empty, never on a heading (`Column.heading`), and a block that holds several rows is split into one block per row.
   - A detail pane that ends in its actions pins the action block (cost line, buttons with their reason lines, an inline confirm) at the bottom; the information above scrolls in the height left. Every action button is visible without scrolling at 1600 x 900 and 1280 x 768.
   - A blocked button is grey with one red reason line below in the game's current words (`UiButtons.guarded`); the line height is reserved either way.
   - An empty list keeps its heading and one grey line.
   - Interiors stay black: boxes, cards, rows and cells get a 1 px border and no fill. Only a selected row, a button, heading bars and table header strips are filled; vanilla never tints a tile. A panel on the dialog picture or starscape gets the black `Ui.backdrop()` behind it.
3. Panel size is `min(board, screen - margin)` per axis (`UiFrame.width/height`, margin 100). List regions take up the difference, fixed blocks keep their size. Everything fits 1600 x 900 and stays usable at 1280 x 768.
4. Native look. Fonts and colours only through `Ui`; raw RGB only for the three state colours `Ui.GREEN`, `Ui.RED`, `Ui.AMBER` (button fills, bars, strips, marks). The fill of a button says what it does: green confirms or starts, red undoes or removes, amber cancels a running order, the story colours cost a story point, everything else is the vanilla dark fill; disabled buttons get the engine's own wash. Cuts follow the vanilla roles: `ALL` for a single button and for a Confirm and Cancel pair, `TL_BR` for every button of a bank that sits side by side, `TOP` for tabs. Use vanilla widgets where they fit (`addShipList`, `showPlanetInfo`, `showCargo`, `showCost`, `addSkillPanel`) through `Ui.element` for their stock tooltips and codex links. Hand drawn cells get a tooltip that names the thing. Sprites come by spec, never by path guess.
5. Wording. `?` help icon on the title line holds rules and rates, not the body. A sentence that explains a feature, an option or a rule is the tooltip of the control it explains (`Ui.tooltip`), never a label; lore goes into the tooltip of the action button. The body keeps state, data, red reasons and consequences the player must see before a click. Short labels, standard game wording, no rounded edges. Player text follows the house-style `player-text.md`.
6. Every view is reachable from code: the panel takes one small view state object (tab, selected id, scroll offsets, armed confirm, open sub view) and has one static entry point that opens it for a market or fleet.

## Classes

| Class | Role |
|---|---|
| `Ui` | Fonts, colours, number formats, text, headings, rules, boxes, tooltips, plain panels |
| `UiButtons` | Buttons and kinds, blocked buttons, inline confirm, tabs, area toggles, selectable rows, checkbox, stepper, text field |
| `UiImages` | Sprites, portraits, ships, hulls and wings, skill and spec icons, cargo style cells, planets and entities, icon rows with overflow |
| `UiData` | Progress bars, pips, marks and check rows, costs, value grids |
| `UiFrame` | Dialog sizing, title strip with `?`, header slots and counters |
| `UiScroll` | Scroll columns and the per panel scroll offsets |
| `UiRoot` | Rebuild host |
| `UiCanvas` | Plugin of every kit panel: runs a `Runnable` button id, paints below and above children; `shown` holds the fraction a kit progress bar shows, for checks |
| `UiHost` | Interaction dialog that only hosts one custom dialog, for panels opened from code |
| `UiDraw` | GL primitives for painters (quad, vertical gradient, line with an optional texture, outline, dashed box, fades, mark, contain fit sprite) |

Every helper takes the parent `CustomPanelAPI`, the rectangle in parent coordinates and the content, and returns the created element. Panels the kit creates use `UiCanvas`, so a button anywhere below them reports to the nearest kit panel and runs its `Runnable`. No helper keeps global state; view state lives in the panel's state object.

## Fonts and colours

| Board | `Ui` | Value |
|---|---|---|
| `ins15` | `INS15` | `Fonts.DEFAULT_SMALL` (insignia15LTaa) |
| `ins21` | `INS21` | `Fonts.INSIGNIA_LARGE` |
| `orb12` | `ORB12` | `Fonts.ORBITRON_12` (orbitron12condensed, headings, buttons, tabs) |
| `orb20` | `ORB20` | `Fonts.ORBITRON_20AA` (dialog buttons) |
| `vic10` | `VIC10` | `Fonts.VICTOR_10` |
| BASE, BRIGHT, DARK | `base()`, `bright()`, `dark()` | player faction UI colours |
| HL, TEXT, GRAY | `hl()`, `text()`, `gray()` | `Misc.getHighlightColor/getTextColor/getGrayColor` |
| POS, NEG | `pos()`, `neg()` | `Misc.getPositive/NegativeHighlightColor` (colourblind aware) |
| STORY | `story()`, `storyDark()` | `Misc.getStoryOptionColor/getStoryDarkColor` |
| LINE | `line()` | base at 0.3 alpha |
| backdrop | `backdrop()` | black at 200 alpha, behind panels on the dialog picture or starscape |
| bar blue | `barBlue()` | settings colour `progressBarStandardColor` (0,121,216); other bar colours through `barColor(key)` |
| GREEN, RED, AMBER | `GREEN`, `RED`, `AMBER` | (50,130,0), (160,30,20), (200,160,10), the only raw colours; at full strength for state display (strips, bars, marks), button fills use `UiButtons.fill` |

`Ui.credits(n)` is `Misc.getDGSCredits` (`30,000¢`), `Ui.num(n)` is `Misc.getWithDGS`.

## Helpers

### Ui

| Signature | Does |
|---|---|
| `panel(p, x, y, w, h)` | Child panel with its own coordinates |
| `painted(p, x, y, w, h, below, above)` | Child panel with `UiCanvas.Painter`s |
| `element(p, x, y, w, h, content)` | Non scrolling `TooltipMakerAPI` for vanilla widgets: made `w + INSET` wide, filled by `content`, then placed at `x - INSET`, so items start at `x`. The first item's pad is its offset below `y`; later items stack `belowLeft` with their own pad. The element grows to its content past `h` and is not clipped: keep the content inside `h` (check `getHeightSoFar()`) or use a scroll column. Fill it inside `content`, before it is attached |
| `width(s, font)` / `fit(s, font, w)` | Real font width (`SettingsAPI.computeStringWidth`); shorten to `w - 2` with `...` |
| `nameLines(p, x, y, pitch, w, name, color, second, secondW)` | Name and a grey second line `pitch` below; a name too long for one line wraps to two (ellipsis and tooltip past that) and the second line drops |
| `lines(s, font, w, n)` | The leading words that wrap into `n` lines, `...` when words are left out |
| `sentences(s, font, w)` | The leading whole sentences that fit `w - 2`; when the first one does not fit, cut at a word with `...` |
| `text(p, x, y, w, s[, color[, align]])`, `text(p, x, y, w, s, font, color, align)` | One line, fitted, tooltip with the full text when shortened. Align `LEFT`, `RIGHT`, `CENTER` |
| `runs(p, x, y, w, font, color, align, s, parts, colors)` | One line with coloured runs (first occurrence of each part) |
| `textIn(p, x, y, w, h, s, font, color, align, parts, colors)` | `runs` centred vertically in a box of height `h`, the odd pixel below like the engine (left and right headings, `thead`, notes on heading bars) |
| `wrap(p, x, y, w, s, color)`, `wrap(..., font, color, align, parts, colors)` | Wrapped at `w`; `height(label)` is the measured height |
| `wrapHeight(s, font, w)` | Height of wrapped text before placing it |
| `heading(p, x, y, w, title[, align[, color, bg]])` | Section heading, 18 high, exactly `w` wide. Centred: the vanilla heading label. Left or right: a flat bar of the same colour with the text 8 px in |
| `heading(p, x, y, w, title, note)` | Left heading with a grey orb12 note at the right end |
| `rule(p, x, y, w[, color])`, `vrule(p, x, y, h)` | 1 px line, `line()` colour |
| `box(p, x, y, w, h, border, fill)`, `card`, `well`, `dashbox` | Framed box; card (base 0.3 frame, no fill); recessed cell behind pictures; dashed empty slot |
| `thead(p, x, y, w, labels, xs, ws, aligns)` | Table header strip with orb12 column labels |
| `tooltip(p, target, title, lines...)` | Text tooltip below any panel, label or button (title in highlight, lines 10 px apart); buttons show it while disabled |
| `tooltip(p, target, width, loc, content)` | Same with free content |

### UiButtons

| Signature | Does |
|---|---|
| `button(p, x, y, w, h, label, kind[, cut], action)` | Kinds `STD`, `GREEN` (confirms or starts), `RED` (undoes or removes), `AMBER` (cancels a running order), `STORY` (costs a story point), `HOT` (bright on opaque dark), `OFF` (disabled). GREEN and AMBER fill with their state colour at `FILL` (60 %, `fill(state)`), RED at full; light text on all three. Cut `ALL` by default, `TL_BR` for the buttons of a bank; never `BL_TR` next to `TL_BR`, never `C2_MENU` or `NONE`. `action` null also disables |
| `button(..., cut, icon, sub, font, action)` | Icon sprite and a highlighted sub text (cost) drawn over the face |
| `guarded(p, x, y, w, h, label, kind, reason, action)` | `reason` null: normal button. Else grey button and the red reason wrapped below. Takes `guardHeight(w, h, reason)` (one line reserved when not blocked) |
| `confirm(p, x, y, w, h, label, kind, state, id, action, refresh[, okLabel, cancelLabel])` | Inline confirm: the button arms `id` in a `Confirm` held by the view state; armed, Confirm and Cancel (or the given labels, so the pair never reads like a neighbouring Cancel) stand in its place |
| `tab(p, x, y, w, h, label, on, enabled, action)` | `CutStyle.TOP` tab, selected bright on opaque dark |
| `tabs(p, x, y, w, tabW, h, labels, selected, enabled, onSelect)` | Tab bar (`tabW` 0 splits evenly, gap 4) and a rule below; returns the y under the rule |
| `area(p, x, y, w, h, label, on, enabled, strip, align, action)` | Area checkbox look: checked is the bright frame and fill; optional 4 px colour strip at the left edge |
| `row(p, x, y, w, h, selected, enabled, strip, action)` | Selectable list row: the area is the click target, content added to the returned panel sits on top |
| `check(p, x, y, w, label, on, enabled, action)` | Vanilla checkbox with label |
| `stepper(p, x, y, value, enabled, minus, plus)` | `-` value `+`, 72 wide |
| `field(p, x, y, w, h, text, maxChars)` | Vanilla text field |

### UiImages

| Signature | Does |
|---|---|
| `img(p, x, y, w, h, sprite[, tint, alpha])`, `tint(...)` | Sprite in a fixed box, contain fit, centred, integer pixels; dimmed is `DIM` 0.4 |
| `icon(p, x, y, size, sprite, name)` | Square sprite with a name tooltip |
| `portrait(p, x, y, size, person)` | Portrait with the name as tooltip |
| `ship(p, x, y, size, member, framed, dim)` | Hand drawn hull sprite in a well, ship name and class as tooltip |
| `hull(p, x, y, size, spec, framed, dim)` | Same for a `ShipHullSpecAPI` that is no fleet member, hull name and class as tooltip |
| `wing(p, x, y, size, wingId, framed, dim)` | One fighter hull sprite of the wing (not the vanilla formation picture), wing name and role as tooltip |
| `shipIcon(p, x, y, size, member)` | Vanilla `addShipList` icon with the stock ship tooltip |
| `skill(p, x, y, size, spec, elite)` | Skill icon, elite framed in highlight |
| `commodity`, `hullmod`, `item` (ids), `submarket` (`SubmarketAPI`) | Spec icons with name tooltip |
| `stack(p, x, y, size, sprite, count, extra, name)` | Cargo style cell in a well: sprite inset 6, `count` bottom right in highlight (hidden below 0), `extra` as `+N` top left in positive colour with a lit frame while above 0, `name` tooltip |
| `planet(p, x, y, size, market)` | Vanilla `showPlanetInfo` for a planet, faction crest for a station; market name tooltip |
| `planet(p, x, y, size, entity)` | `SectorEntityToken`: vanilla render for a planet or star, else the custom entity sprite, else its icon, else the faction crest; entity name tooltip |
| `iconrow(p, x, y, w, items, cw, ch, gap, rows, cell, name)` | Fixed cells, wraps to `rows`, last cell `+N` with the rest; returns the used height |
| `morecell(p, x, y, w, h, rest)` | The `+N` cell |
| `creditsSprite()`, `relicSprite()`, `partsSprite()`, `commoditySprite(id)` | Sprite names by key or spec |

### UiData

| Signature | Does |
|---|---|
| `bar(p, x, y, w, h, fraction, fill, label)` | Progress bar drawn like the vanilla bar at rest (see Engine facts), centred label; pass a vanilla bar colour (`Ui.barColor`) |
| `thinbar(p, x, y, w, fraction, fill)` | 3 px bar for list rows: the vanilla fill colour flat on black |
| `pips(p, x, y, total, filled, size, gap, color)` | Square pips, the first `filled` lit (quality level, D-mod forecast) |
| `pips(p, x, y, total, from, to, w, h, gap, color)` | Pips `[from, to)` lit, the rest in base at `PIP_OFF` 0.18 (one lit pip: Academy personality ladder) |
| `mark(p, x, y, ok)` / `checkrow(p, x, y, w, ok, text)` | 12 px tick or cross; mark and text, red text while unmet |
| `cost(p, x, y, w, sprite, amount, stock, ok, size)` | Icon, amount in highlight (negative when short), stock in grey brackets while both fit (`withStock`) |
| `costrow(p, x, y, w, label, sprite, amount, stock, ok)` | Label left, cost right in a measured column, 22 high; the amount is never cut, the stock drops when both do not fit |
| `withStock(amount, stock, w)` | `amount (stock)` while it fits `w`, else the amount alone; for hand built cost cells |
| `creditsRow(p, x, y, w, label, amount)`, `relicRow(p, x, y, w, label, amount, market)` | `costrow` with the player's credits or `RelicHelper.getAvailable(market)` |
| `value(p, x, y, w, h, label, value, color, parts, colors)` | Label left, value right in a measured column |
| `grid(p, x, y, w, rowH, rows)` | `GridRow.of(label, value[, color])` rows, value in highlight by default |

### UiFrame

| Signature | Does |
|---|---|
| `width(boardW)`, `height(boardH)` | Content size for `showCustomDialog`: `min(board, screen - 100) - 20` and `- 55` |
| `title(p, w, title, sub, help)` | Title heading at y 0 with the sub text in grey; `HelpIcon` 20 x 20 at the right end when `help` is set |
| `slot(p, right, y, sprite, text, title, lines...)` | Header item right to left (24 px icon, grey text, tooltip); returns the next right edge |
| `counter(p, right, y, label, sprite, value)`, `credits(p, right, y)` | Grey label, icon, highlighted value; the player's credits |

Header layout of the boards: title at y 0, tabs at `HEADER_Y` 30 with `HEADER_H` 24, rule at 54, slots right to left from the panel width at y 30.

### UiScroll

| Signature | Does |
|---|---|
| `list(p, x, y, w, h, offsets, id)` | Scroll column of fixed outer size; returns a `Column` |
| `Column.block(h, gapBefore)` | Next block, `width()` wide (`w - GUTTER`), its own coordinates |
| `Column.heading(gapBefore)` | A `Ui.HEADING_H` block for `Ui.heading`; `end()` never ends the viewport on it, so a heading is never shown at rest without the block below it |
| `Column.end()` | Creates the scroll element (its viewport height is fixed at creation, `obf/ui/newui/int.java` `addUIElement`) at the height of the last block that fits whole, adds the blocks, attaches it and restores its offset |
| `Offsets` | Offsets by list id: `get`, `set`, `reset(id)` (for example on a new selection), `capture()`, `restore()` |
| `wholeRows(h, lead, rowH, gap)` | Tallest height up to `h` that ends on a whole row, so a fixed pitch list never shows half a row at rest; the rest becomes the gap below |

Blocks need their height before they are built, so detail columns compute each block's height first (`wrapHeight`, `guardHeight`, row counts).

## Rebuild and scroll pattern

A click changes the view state and rebuilds the body; scroll offsets survive. Buttons carry a `Runnable` as their data, so there are no id strings and no per frame polling.

```java
public class FooPanel extends BaseCustomDialogDelegate {
    private final FooView view;
    private UiRoot root;

    public static void show(InteractionDialogAPI dialog, MarketAPI market, FooView view) {
        dialog.showCustomDialog(UiFrame.width(BOARD_W), UiFrame.height(BOARD_H), new FooPanel(market, view));
    }

    @Override
    public void createCustomDialog(CustomPanelAPI panel, CustomDialogCallback callback) {
        root = new UiRoot(panel, view.scroll, this::build);
        root.refresh();
    }

    private void build(CustomPanelAPI p, float w, float h) {
        UiFrame.title(p, w, "Foo", market.getName(), this::help);
        UiScroll.Column list = UiScroll.list(p, 0f, 64f, 400f, h - 64f, view.scroll, "list");
        for (Item item : items) {
            CustomPanelAPI block = list.block(50f, 4f);
            CustomPanelAPI row = UiButtons.row(block, 0f, 0f, list.width(), 50f, item.id.equals(view.selected), true, null, () -> select(item));
            Ui.text(row, 58f, 7f, 140f, item.name);
        }
        list.end();
    }

    private void select(Item item) {
        view.selected = item.id;
        view.scroll.reset("detail");
        root.refresh();
    }
}
```

`UiRoot.refresh()` captures the live offsets, removes the old body, builds a new one on a `UiCanvas` panel, attaches it and restores the offsets clamped to the new content height. `getCustomPanelPlugin()` can stay null: kit panels handle their own buttons. Text typed into a `field` is read in the action that uses it and kept in the view state before the refresh.

## Confirm over a panel

One custom dialog can be open per interaction dialog. Two ways to confirm:

- Inline: `UiButtons.confirm` turns the action button into Confirm and Cancel in place. Use it when the button area can hold two buttons.
- Real dialog (Edict enact, Research redeem): `industries/senate/dialogue/EdictConfirmDialog.show(onClose)` opens a second interaction dialog with `CampaignUIAPI.showInteractionDialogFromCargo(plugin, null, onClose)`; its plugin's `init` calls `dialog.showCustomDialog(w, h, this)` and Confirm or Cancel call `dialog.dismiss()`; `onClose` rebuilds the panel below. `showCustomDialog` can not stack a second custom dialog in one dialog.

## Opening a panel

Every panel has one static `show` per way in, each taking the view state:

- From an industry option: `DialogCreatorUI.showDialog(w, h, delegate)`.
- From a rule command or another dialog plugin: `InteractionDialogAPI.showCustomDialog(w, h, delegate)`.
- From code with no dialog open (tests, abilities): `UiHost.show(entity, dialog -> dialog.showCustomDialog(w, h, new FooPanel(..., dialog::dismiss)))`. The host is an empty interaction dialog; the delegate runs the passed `Runnable` in `customDialogConfirm` and `customDialogCancel`, or the empty dialog stays open.

## Vanilla pickers over a panel

A vanilla picker (`showFleetMemberPickerDialog`, `showCargoPickerDialog`, `showCustomProductionPicker`, a second `showCustomDialog`) does nothing while the panel's custom dialog is up. A panel that needs one is hosted by its own dialog plugin, which closes the panel, opens the picker and shows the panel again from `advance` once the picker is gone; the view state object carries selection and scroll offsets across. Owners: `abilities/splitfleet/dialogue/DetachmentDialogPlugin.openPicker`, `industries/salvageyards/plugins/SalvageYardsDialogPlugin.openPicker`, `industries/courierport/dialogue/CourierPortDialoguePlugin.openPicker`. Pickers call their listener before they dismiss themselves, so the listener only changes state and never reopens the panel itself.

## Side panel

A `VisualPanelAPI.showCustomPanel(600, h, plugin)` side panel (board 15) uses the same pieces without the dialog frame: pass `new UiCanvas()` as the plugin, build into the returned panel with `UiRoot`, take the height from the visual panel area instead of `UiFrame.height`, and draw the title with `UiFrame.title`. There is no Return button; the dialog options close it. Owner: `dialogue/research/dialogue/ResearchProjectPanel.show` ([Research](../dialogue/RESEARCH.md)).

## Engine facts

Verified in the decompiled 0.98a-RC8 source unless noted.

- `showCustomDialog(w, h, delegate)` makes a `(w + 20) x (h + 55)` dialog: content panel `w x h` at `inTL(10, 10)` with `delegate.getCustomPanelPlugin()`, confirm (and cancel) buttons 150 x 25, `CutStyle.TL_BR`, orbitron20aa, `inBR(10, 10)` (`obf/ui/newui/super.java:26-67`). A board's W x H is the whole dialog, so its inner coordinates map 1:1 to the content panel. Boards draw Return 160 x 28; the game's is 150 x 25.
- Tooltip elements place the first item at `inTL(0, pad).setXAlignOffset(5)` and the rest `belowLeft(prev, pad)` (`obf/ui/impl/StandardTooltipV2Expandable.java:384-394`), so every item of an element sits 5 px right of it. Kit elements are made `Ui.INSET` wider and placed 5 px left. The same rule indents tooltip text 5 px; keep it.
- `addSectionHeading(str, color, bg, align, width, pad)`: an orbitron12condensed label with background, `lineHeight + 3` = 18 high and `width + 10` wide; as the first item of an element at `inTL(0, 0)` (`StandardTooltipV2Expandable.java:937-956`). Left aligned text starts at the label's left edge (`obf/ui/_c177.java:516`), so left headings are a flat bar plus one inset label.
- `addButton(text, data, base, bg, align, cut, w, h, pad)` uses the element's button font, orbitron12condensed by default (`StandardTooltipV2Expandable.java:1012-1052`). Area checkboxes use `areaCheckboxFont`, the default font unless set.
- Labels: `SettingsAPI.createLabel(text, font)` is the obf label `com.fs.starfarer.ui.d`, which implements `UIComponentAPI`, so the kit adds it with `addComponent((UIComponentAPI) label)` (knowledge skill `docs/ui/widgets.md`). Labels only react to mouse moves, never consume clicks (`obf/ui/_c177.java:652-666`). Panels hand input to children top first and never consume it themselves (`obf/ui/interfacenew.java:144-157`); a custom panel runs its plugin's `processInput` after its children (`obf/ui/newui/int.java:127-131`). So labels and painted panels on top of an area checkbox let the click through to it.
- `addTooltipTo(tc, component, loc)` works on any component that is an obf `w` (labels, buttons, custom panels) and is static in effect: any element can attach it (`StandardTooltipV2Expandable.java:2210-2232`).
- Scroll element: `createUIElement(w, h, true)` lays content out `w - 5` wide; the scrollbar is 3 px at the right edge and widens by `BAR_PAD` 6 on hover (`obf/ui/_c179.java:51,718-790`); content fades near the top and bottom edges. `getExternalScroller()` is non null after `addUIElement`; `setYOffset` restores the offset (`api/ui/ScrollPanelAPI.java`).
- Non scrolling elements: `addUIElement` sizes the element to `max(getHeightSoFar(), h)` and adds it as is (`obf/ui/newui/int.java:97-100`); panels do not clip children unless `setClipping(true)` (`obf/ui/interfacenew.java:18,174`). So `Ui.element` content past `h` draws over what lies below.
- `SettingsAPI.getSprite(path)` needs a loaded texture (`api/SettingsAPI.java:115`); for a path never loaded it returns a sprite without texture and 0 x 0 (`obf/_common/graphics/_c7.java:23`, `obf/_common/graphics/Sprite.java:460-470`). `UiImages.planet(entity)` checks the size before it uses a custom entity sprite or icon.
- Weapon icons have no single sprite: the vanilla cell draws the hardpoint sprite, the hardpoint gun sprite of projectile weapons and, for missile weapons, the missile sprite from the projectile spec, which only obf code reaches (`obf/ui/B/_c172.java:59-105`). The kit has no weapon helper; show weapons through `Ui.element` with `showCargo`, or use `stack` with `getHardpointSpriteName()` and accept missing barrels and missiles.
- `Misc.renderQuad` does not disable textures; `UiDraw` sets `GL_TEXTURE_2D` off, blend on, and turns textures back on after each primitive (knowledge skill `docs/ui/custom-elements.md` section 2).
- Credits icon: sprite `fleetScreen/icon_logistics_credits` (`graphics/ui/icons/fleettab/credits_24x24.png`, `data/config/settings.json`). Relic and Ship Components: `CommoditySpecAPI.getIconName()` of `IndEvo_rare_parts` and `IndEvo_parts`.
- Progress bars: the vanilla bar is `com.fs.starfarer.campaign.ui.N` (cargo screen bars, `obf/campaign/ui/trade/o0OO.java:43`), no API. `UiData.bar` is a translation of its `renderImpl` at rest (`obf/campaign/ui/N.java:481-700`): black over the rect; usable width `w - 8` from `x + 3` (`w - 6` from `x + 2` at 5 px or less); fill `fraction * usable + 1` wide, `h - 2` high, `y + 1`; the empty part `y + 3`, `h - 6` high, from 5 px after the fill end. Fill routine `obf/renderers/O.java:339-381`: the colour 15 % toward white at alpha 0.75, then an additive vertical gradient of the colour, 0 at the edges and 0.75 in the middle; the empty part is the same at 0.65 alpha with a black (alpha 127) gradient on top instead. End lines (`obf/renderers/E.java:386-430`; every bar line is textured with `graphics/hud/line4x4.png`, which vanilla loads at startup, through `UiDraw.line` with a texture): 2 px at x and x + w at 0.5, a 2 px additive line at x + 1 and x + w - 1, 15 px corner stubs along top and bottom fading 1, 0.5, 0; along the empty part 1 px edge lines at y + 1 and y + h - 2 that fade in over 10 px at the right end, and a divider plus an additive white one where it starts; a 1 px line over the full height at `x + fraction * usable + 5.5`, alpha 0.5, 1, 0.5 (`obf/campaign/ui/N.java:716`). Not translated: easing toward the target value, the overflow part and the hover brightness. The frame and line colour is the settings colour `widgetBorderColorBright` (the `N` widget colour default, `obf/O0OO.java:29`). Bars under 2 px get no fill in vanilla, so `thinbar` is a kit bar in the same fill colour.
- Bar colours are the vanilla settings colours where a meaning exists (`Ui.barColor(key)`): `progressBarStandardColor` (progress, `barBlue`), `progressBarCargoColor`, `progressBarFuelColor`, `progressBarCrewColor` (Detachments), `progressBarOverflowColor` (over capacity, over budget), `progressBarWarningColor` (paused training, an Edict before its minimum term), `progressBarDangerColor` (Detachments short on supplies). Ready or done has no vanilla colour and uses `Ui.GREEN` (Research ready, Hub hull at 100 %, Edict after its term).
- `showPlanetInfo(planet, w, h, withName, pad)` draws the vanilla planet render at that size; there is no market level planet widget, so `planet(market)` shows the faction crest for a station and `planet(entity)` the entity sprite.
- `CampaignClockAPI.getShortDate()` is `cycle.month.day` and `getDateString()` is `Mon d, cN` (`obf/campaign/CampaignClock.java:30-36`).

## Known limits

- Overlay text on a button face (icon and sub text variant) does not take the hover glow of the button text.
- `Ui.fit` keeps 2 px slack against the label's own wrapping; not measured in game.
