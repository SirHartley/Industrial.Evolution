package indevo.industries.courierport.dialogue;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoStackAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import indevo.config.Setting;
import indevo.industries.courierport.*;
import indevo.industries.courierport.listeners.ShippingPlanetPicker;
import indevo.industries.courierport.listeners.SubmarketCargoPicker;
import indevo.industries.courierport.listeners.SubmarketShipPicker;
import indevo.utils.helper.MiscIE;
import indevo.utils.ui.*;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

// editor column of the Courier Port panel; a new contract shows each section once the one before it is done
class CourierEditor {

    static final float NAME_H = 26f;
    static final float NAME_X = 50f;
    static final float NAME_W = 340f;
    static final float SCROLL_Y = 36f;
    static final float ACTION_H = 28f;
    static final float ACTION_GAP = 8f;
    static final float CARD_W = 316f;
    static final float CARD_MIN_GAP = 60f;
    static final float CARD_Y = 22f;
    static final float CARD_H = 72f;
    static final float TILES_Y = 100f;
    static final float TILE_W = 60f;
    static final float TILE_H = 40f;
    static final float TILE_GAP = 4f;
    static final float PLANET = 56f;
    static final float CELL = 52f;
    static final float CELL_GAP = 4f;
    static final float CARGO_W = 332f;
    static final float SHIPS_X = 352f;
    static final float SHIPS_W = 164f;
    static final float PICK_W = 120f;
    static final float AREA_H = 26f;
    static final float CADENCE_W = 84f;
    static final float CADENCE_STEP = 88f;
    static final float NEXT_X = 448f;
    static final float SHIP_NOW_W = 110f;
    static final float COST_ROW = 20f;
    static final float COST_ICON = 18f;
    static final float SECTION_GAP = 6f;
    static final int[] CADENCES = {0, 14, 31, 93, 186};
    static final ShippingContract.Scope[] SCOPES = {ShippingContract.Scope.EVERYTHING, ShippingContract.Scope.ALL_CARGO,
            ShippingContract.Scope.ALL_WEAPONS, ShippingContract.Scope.ALL_SHIPS};
    static final String[] SCOPE_LABELS = {"Everything", "All cargo", "All weapons", "All ships", "Selected"};

    private final CourierPanelDelegate panel;
    private final CourierView view;
    private final ShippingContract stored;
    private final ShippingContract draft;
    private TextFieldAPI nameField;
    private UiScroll.Column column;
    private float cw;

    private record CostLine(String sprite, String label, String note, String value, Color color) {
    }

    CourierEditor(CourierPanelDelegate panel, CourierView view, ShippingContract stored) {
        this.panel = panel;
        this.view = view;
        this.stored = stored;
        this.draft = view.draft;
    }

    void build(CustomPanelAPI p, float x, float y, float w, float h) {
        boolean invalid = stored != null && !stored.isValid();

        Ui.text(p, x, y + 5f, NAME_X - 8f, "Name", Ui.gray());
        if (invalid) Ui.text(p, x + NAME_X + 8f, y + 5f, NAME_W - 8f, draft.name);
        else nameField = UiButtons.field(p, x + NAME_X, y, NAME_W, NAME_H, draft.name, 40);
        if (stored != null && !invalid) UiButtons.check(p, x + w - 90f, y + 3f, 90f, "Active", draft.isActive, true, act(() -> {
            draft.isActive = !draft.isActive;
            stored.isActive = draft.isActive;
        }));

        column = UiScroll.list(p, x, y + SCROLL_Y, w, h - SCROLL_Y - ACTION_H - ACTION_GAP, view.scroll, CourierView.EDITOR);
        cw = column.width();

        if (invalid) {
            String reason = "Invalid: " + stored.getInvalidReason() + ". This contract can only be removed.";
            Ui.wrap(column.block(Ui.wrapHeight(reason, Ui.INS15, cw), 0f), 0f, 0f, cw, reason, Ui.neg());
            addRoute(false, SECTION_GAP);
        } else build();

        column.end();
        addActions(p, x, y + h - ACTION_H, w, invalid);
    }

    private void build() {
        boolean fromDone = draft.getFromMarket() != null && draft.getFromSubmarket() != null;
        boolean toDone = fromDone && draft.getToMarket() != null && draft.getToSubmarket() != null;

        addRoute(true, 0f);
        if (!toDone) return;

        addGoods();
        if (!isComplete()) return;

        addSchedule();
        addCost();
    }

    private boolean isComplete() {
        return view.goodsChosen && draft.isValid();
    }

    private Runnable act(Runnable action) {
        return () -> {
            syncName();
            action.run();
            view.confirm.clear();
            panel.refresh();
        };
    }

    private void syncName() {
        if (nameField != null && !nameField.getText().isBlank()) draft.name = nameField.getText().trim();
    }

    private void picker(Runnable open) {
        syncName();
        view.confirm.clear();
        panel.getHost().openPicker(open);
    }

    private void addRoute(boolean editable, float gap) {
        MarketAPI from = draft.getFromMarket();
        MarketAPI to = draft.getToMarket();
        boolean showTo = from != null && draft.getFromSubmarket() != null || !editable;
        float cardW = Math.min(CARD_W, (cw - CARD_MIN_GAP) / 2f);
        float gapW = cw - 2f * cardW;
        int per = Math.max(1, (int) ((cardW + TILE_GAP) / (TILE_W + TILE_GAP)));

        List<SubmarketAPI> fromTiles = editable && from != null ? getOriginSubmarkets(from) : new ArrayList<>();
        List<SubmarketAPI> toTiles = editable && showTo && to != null && draft.getFromSubmarket() != null
                ? new ArrayList<>(ShippingTargetHelper.getValidTargetSubmarkets(to, draft.getFromSubmarket())) : new ArrayList<>();
        int rows = Math.max(tileRows(fromTiles.size(), per, CourierView.FROM), tileRows(toTiles.size(), per, CourierView.TO));
        float tilesH = rows > 0 ? rows * (TILE_H + TILE_GAP) - TILE_GAP + 10f : 0f;

        CustomPanelAPI b = column.block(TILES_Y + tilesH, gap);
        addCard(b, 0f, cardW, "From", from, draft.getFromSubmarket(), editable, true);
        addTiles(b, 0f, cardW, per, fromTiles, draft.fromSubmarketId, CourierView.FROM);
        if (!showTo) return;

        addCard(b, cardW + gapW, cardW, "To", to, draft.getToSubmarket(), editable, false);
        addTiles(b, cardW + gapW, cardW, per, toTiles, draft.toSubmarketId, CourierView.TO);

        boolean both = from != null && to != null;
        Color c = both ? Ui.hl() : Misc.scaleAlpha(Ui.base(), 0.35f);
        float chevronX = cardW + Math.round((gapW - 48f) / 2f);
        Ui.painted(b, chevronX, both ? 30f : 38f, 48f, 40f, null, (pos, a) -> chevrons(pos, c, a));
        if (!both) return;

        float ly = Misc.getDistanceLY(from.getPrimaryEntity(), to.getPrimaryEntity());
        float mult = ShippingCostCalculator.getLYMult(from.getPrimaryEntity(), to.getPrimaryEntity(), false);
        Ui.text(b, cardW, 74f, gapW, Misc.getRoundedValueMaxOneAfterDecimal(ly) + " ly", Ui.text(), Ui.CENTER);
        Ui.text(b, cardW, 92f, gapW, "x" + Misc.getRoundedValueMaxOneAfterDecimal(mult), Ui.hl(), Ui.CENTER);
    }

    private int tileRows(int n, int per, String side) {
        if (n == 0) return 0;
        if (!side.equals(view.expanded) || n <= per) return 1;
        return (n + per - 1) / per;
    }

    private static List<SubmarketAPI> getOriginSubmarkets(MarketAPI market) {
        List<SubmarketAPI> list = new ArrayList<>(ShippingTargetHelper.getValidOriginSubmarkets(market));
        if (market.isPlayerOwned() && market.hasSubmarket(Submarkets.LOCAL_RESOURCES)) list.add(market.getSubmarket(Submarkets.LOCAL_RESOURCES));
        return list;
    }

    private void addCard(CustomPanelAPI b, float x, float w, String title, MarketAPI market, SubmarketAPI storage, boolean editable, boolean origin) {
        if (storage != null) Ui.heading(b, x, 0f, w, title, storage.getNameOneLine());
        else Ui.heading(b, x, 0f, w, title, Ui.LEFT);
        Ui.card(b, x, CARD_Y, w, CARD_H);

        float textX = x + 8f + PLANET + 12f;
        if (market == null || market.getPrimaryEntity() == null) {
            Ui.dashbox(b, x + 8f, CARD_Y + 8f, PLANET, PLANET);
            if (!editable) {
                Ui.text(b, textX, CARD_Y + 27f, x + w - 8f - textX, "Not available", Ui.neg());
                return;
            }

            Ui.text(b, textX, CARD_Y + 27f, x + w - 116f - textX, "No planet picked", Ui.gray());
            UiButtons.button(b, x + w - 108f, CARD_Y + 24f, 100f, 24f, "Pick...", UiButtons.Kind.STD, () -> pickPlanet(origin));
            return;
        }

        UiImages.planet(b, x + 8f, CARD_Y + 8f, PLANET, market.getPrimaryEntity());
        float nameW = x + w - (editable ? 100f : 8f) - textX;
        Ui.text(b, textX, CARD_Y + 10f, nameW, market.getName(), Ui.INS21, Ui.bright(), Ui.LEFT);
        Ui.text(b, textX, CARD_Y + 38f, x + w - 8f - textX, getMarketLine(market), Ui.gray());
        if (editable) UiButtons.button(b, x + w - 92f, CARD_Y + 8f, 84f, 22f, "Change...", UiButtons.Kind.STD, () -> pickPlanet(origin));
    }

    private void addTiles(CustomPanelAPI b, float x, float w, int per, List<SubmarketAPI> tiles, String selectedId, String side) {
        if (tiles.isEmpty()) return;

        boolean all = side.equals(view.expanded) || tiles.size() <= per;
        int shown = all ? tiles.size() : per - 1;
        for (int i = 0; i < shown; i++) {
            float tx = x + (i % per) * (TILE_W + TILE_GAP);
            float ty = TILES_Y + (i / per) * (TILE_H + TILE_GAP);
            addTile(b, tx, ty, tiles.get(i), tiles.get(i).getSpecId().equals(selectedId), side);
        }

        if (all) return;

        List<String> rest = new ArrayList<>();
        for (SubmarketAPI s : tiles.subList(shown, tiles.size())) rest.add(s.getNameOneLine());
        float tx = x + shown * (TILE_W + TILE_GAP);
        ButtonAPI more = UiButtons.area(b, tx, TILES_Y, TILE_W, TILE_H, "+" + rest.size(), false, true, null, Ui.CENTER, act(() -> view.expanded = side));
        Ui.tooltip(b, more, Ui.TOOLTIP_W, TooltipMakerAPI.TooltipLocation.BELOW, t -> {
            for (int i = 0; i < rest.size(); i++) t.addPara(rest.get(i), i == 0 ? 0f : 3f);
        });
    }

    private void addTile(CustomPanelAPI b, float x, float y, SubmarketAPI s, boolean on, String side) {
        ButtonAPI tile = UiButtons.area(b, x, y, TILE_W, TILE_H, null, on, true, null, Ui.CENTER, act(() -> pickStorage(s, side)));
        Ui.tooltip(b, tile, s.getNameOneLine());

        String icon = s.getSpec().getIcon();
        if (isLoaded(icon)) {
            UiImages.img(b, x + 2f, y + 2f, TILE_W - 4f, TILE_H - 4f, icon, null, on ? 1f : 0.75f);
            return;
        }

        String name = s.getNameOneLine().toUpperCase();
        float th = Ui.wrapHeight(name, Ui.VIC10, TILE_W - 4f);
        if (th <= TILE_H - 4f) Ui.wrap(b, x + 2f, y + Math.round((TILE_H - th) / 2f), TILE_W - 4f, name, Ui.VIC10, Ui.base(), Alignment.TMID, new String[0], new Color[0]);
        else Ui.textIn(b, x + 2f, y, TILE_W - 4f, TILE_H, name, Ui.VIC10, Ui.base(), Ui.CENTER, new String[0], new Color[0]);
    }

    // getSprite on a path that was never loaded returns a sprite without texture and zero size
    private static boolean isLoaded(String sprite) {
        return sprite != null && !sprite.isEmpty() && Global.getSettings().getSprite(sprite).getWidth() > 0f;
    }

    private void pickStorage(SubmarketAPI s, String side) {
        if (side.equals(CourierView.FROM)) draft.fromSubmarketId = s.getSpecId();
        else draft.toSubmarketId = s.getSpecId();
        view.expanded = null;
        fixRoute();
    }

    private void pickPlanet(boolean origin) {
        picker(() -> ShippingPlanetPicker.show(panel.getHost().getDialog(), draft, origin, entity -> {
            if (entity != null && entity.getMarket() != null) applyPlanet(entity, origin);
            panel.getHost().reopen();
        }));
    }

    private void applyPlanet(SectorEntityToken entity, boolean origin) {
        MarketAPI market = entity.getMarket();
        view.expanded = null;

        if (origin) {
            draft.fromMarketId = market.getId();
            List<SubmarketAPI> valid = getOriginSubmarkets(market);
            SubmarketAPI storage = MiscIE.getStorage(market);
            if (storage != null && valid.contains(storage)) draft.fromSubmarketId = storage.getSpecId();
            else draft.fromSubmarketId = valid.isEmpty() ? null : valid.get(0).getSpecId();
        } else {
            draft.toMarketId = market.getId();
            draft.toSubmarketId = Submarkets.SUBMARKET_STORAGE;
        }

        fixRoute();
    }

    // keeps the destination storage and the goods valid for the picked storages
    private void fixRoute() {
        SubmarketAPI from = draft.getFromSubmarket();
        MarketAPI to = draft.getToMarket();
        if (from != null && to != null) {
            List<SubmarketAPI> valid = new ArrayList<>(ShippingTargetHelper.getValidTargetSubmarkets(to, from));
            boolean ok = false;
            for (SubmarketAPI s : valid) if (s.getSpecId().equals(draft.toSubmarketId)) ok = true;
            if (!ok && valid.isEmpty()) draft.toMarketId = null;
            else if (!ok) draft.toSubmarketId = valid.get(0).getSpecId();
        }

        if (!canCargo(draft)) draft.clearTargetCargo();
        if (!canShips(draft)) draft.clearTargetShips();

        boolean needsCargo = draft.scope == ShippingContract.Scope.ALL_CARGO || draft.scope == ShippingContract.Scope.ALL_WEAPONS;
        if (isSpecific(draft.scope)) setSelectedScope(ShippingContract.Scope.EVERYTHING);
        else if (needsCargo && !canCargo(draft) || draft.scope == ShippingContract.Scope.ALL_SHIPS && !canShips(draft))
            draft.scope = ShippingContract.Scope.EVERYTHING;
    }

    private void setSelectedScope(ShippingContract.Scope fallback) {
        boolean cargo = !draft.targetCargo.isEmpty();
        boolean ships = !draft.variantList.isEmpty();
        if (cargo && ships) draft.scope = ShippingContract.Scope.SPECIFIC_EVERYTHING;
        else if (cargo) draft.scope = ShippingContract.Scope.SPECIFIC_CARGO;
        else if (ships) draft.scope = ShippingContract.Scope.SPECIFIC_SHIPS;
        else draft.scope = fallback;
    }

    static boolean canCargo(ShippingContract c) {
        SubmarketAPI from = c.getFromSubmarket();
        SubmarketAPI to = c.getToSubmarket();
        return from != null && to != null && from.getPlugin().showInCargoScreen() && to.getPlugin().showInCargoScreen();
    }

    static boolean canShips(ShippingContract c) {
        SubmarketAPI from = c.getFromSubmarket();
        SubmarketAPI to = c.getToSubmarket();
        return from != null && to != null && from.getPlugin().showInFleetScreen() && to.getPlugin().showInFleetScreen();
    }

    static boolean isSpecific(ShippingContract.Scope scope) {
        return scope == ShippingContract.Scope.SPECIFIC_CARGO || scope == ShippingContract.Scope.SPECIFIC_SHIPS || scope == ShippingContract.Scope.SPECIFIC_EVERYTHING;
    }

    private void addGoods() {
        boolean cargo = canCargo(draft);
        boolean ships = canShips(draft);
        String noCargo = cargo ? null : getHolder(true);
        String noShips = ships ? null : getHolder(false);

        Ui.heading(column.block(Ui.HEADING_H, SECTION_GAP), 0f, 0f, cw, "Goods", Ui.LEFT);
        CustomPanelAPI b = column.block(AREA_H, SECTION_GAP);
        float bw = (cw - 4f * 4f) / 5f;
        for (int i = 0; i < 5; i++) {
            boolean selectedArea = i == 4;
            ShippingContract.Scope scope = selectedArea ? null : SCOPES[i];
            boolean enabled = selectedArea ? cargo || ships : scope == ShippingContract.Scope.ALL_SHIPS ? ships
                    : scope == ShippingContract.Scope.EVERYTHING || cargo;
            boolean on = view.goodsChosen && (selectedArea ? isSpecific(draft.scope) : draft.scope == scope);
            Runnable action = !enabled ? null : selectedArea ? () -> pickGoods(cargo) : act(() -> chooseScope(scope));
            ButtonAPI area = UiButtons.area(b, i * (bw + 4f), 0f, bw, AREA_H, SCOPE_LABELS[i], on, enabled, null, Ui.CENTER, action);
            if (!enabled) Ui.tooltip(b, area, null, scope == ShippingContract.Scope.ALL_SHIPS ? noShips : noCargo != null ? noCargo : noShips);
        }

        if (!view.goodsChosen) return;
        if (isSpecific(draft.scope)) addSelectedGoods(cargo, ships);
        else {
            String text = getScopeLine(noCargo, noShips);
            Ui.wrap(column.block(Ui.wrapHeight(text, Ui.INS15, cw), 8f), 0f, 0f, cw, text, Ui.gray());
        }
    }

    // the end that can not hold cargo or ships, in its own words
    private String getHolder(boolean cargo) {
        SubmarketAPI from = draft.getFromSubmarket();
        SubmarketAPI to = draft.getToSubmarket();
        boolean fromHolds = cargo ? from.getPlugin().showInCargoScreen() : from.getPlugin().showInFleetScreen();
        String name = (fromHolds ? to : from).getNameOneLine();
        return name + (name.endsWith("s") ? " hold no " : " holds no ") + (cargo ? "cargo." : "ships.");
    }

    private String getScopeLine(String noCargo, String noShips) {
        String where = draft.getFromSubmarket().getNameOneLine();
        String text = switch (draft.scope) {
            case ALL_CARGO -> "Ships all cargo in the " + where + " on the day.";
            case ALL_WEAPONS -> "Ships all weapons and fighter wings in the " + where + " on the day.";
            case ALL_SHIPS -> "Ships all ships in the " + where + " on the day.";
            default -> "Ships whatever lies in the " + where + " on the day.";
        };

        if (noShips != null && draft.scope != ShippingContract.Scope.ALL_SHIPS) text += " " + noShips;
        if (noCargo != null && draft.scope == ShippingContract.Scope.EVERYTHING) text += " " + noCargo;
        return text;
    }

    private void chooseScope(ShippingContract.Scope scope) {
        draft.scope = scope;
        draft.clearTargetCargo();
        draft.clearTargetShips();
        view.goodsChosen = true;
    }

    private void pickGoods(boolean cargo) {
        if (cargo) pickCargo();
        else pickShips();
    }

    private void pickCargo() {
        ShippingContract.Scope before = view.goodsChosen ? draft.scope : null;
        picker(() -> new SubmarketCargoPicker(panel.getHost().getDialog(), draft, () -> afterPick(before)));
    }

    private void pickShips() {
        ShippingContract.Scope before = view.goodsChosen ? draft.scope : null;
        picker(() -> new SubmarketShipPicker(panel.getHost().getDialog(), draft, () -> afterPick(before)));
    }

    // an empty pick leaves selected goods and goes back to the scope before, or to Everything
    private void afterPick(ShippingContract.Scope before) {
        boolean picked = !draft.targetCargo.isEmpty() || !draft.variantList.isEmpty();
        ShippingContract.Scope fallback = before != null && !isSpecific(before) ? before : ShippingContract.Scope.EVERYTHING;
        setSelectedScope(fallback);
        if (picked || before != null) view.goodsChosen = true;
        panel.getHost().reopen();
    }

    private void addSelectedGoods(boolean cargo, boolean ships) {
        List<CargoStackAPI> stacks = draft.targetCargo.getStacksCopy();
        List<ShipVariantAPI> variants = draft.variantList;

        CustomPanelAPI b = column.block(CELL, 8f);
        if (stacks.isEmpty()) Ui.text(b, 0f, 18f, CARGO_W, "No cargo selected.", Ui.gray());
        else UiImages.iconrow(b, 0f, 0f, CARGO_W, stacks, CELL, CELL, CELL_GAP, 1, CourierEditor::cargoCell, CourierEditor::cargoName);
        Ui.vrule(b, CARGO_W + 10f, 0f, CELL);

        float shipsW = Math.min(SHIPS_W, cw - PICK_W - 10f - SHIPS_X);
        if (variants.isEmpty()) Ui.text(b, SHIPS_X, 18f, shipsW, "No ships selected.", Ui.gray());
        else UiImages.iconrow(b, SHIPS_X, 0f, shipsW, variants, CELL, CELL, CELL_GAP, 1,
                (p, x, y, w, h, v) -> UiImages.hull(p, x, y, w, v.getHullSpec(), true, false), v -> v.getHullSpec().getHullNameWithDashClass());

        ButtonAPI cargoButton = UiButtons.button(b, cw - PICK_W, 0f, PICK_W, 24f, "Cargo...", UiButtons.Kind.STD, CutStyle.TL_BR, cargo ? this::pickCargo : null);
        ButtonAPI shipButton = UiButtons.button(b, cw - PICK_W, 28f, PICK_W, 24f, "Ships...", UiButtons.Kind.STD, CutStyle.TL_BR, ships ? this::pickShips : null);
        if (!cargo) Ui.tooltip(b, cargoButton, null, getHolder(true));
        if (!ships) Ui.tooltip(b, shipButton, null, getHolder(false));

        float space = ShippingCostCalculator.getCargoSpace(draft.targetCargo);
        float dp = ShippingCostCalculator.getDeploymentPoints(variants);
        List<String> parts = new ArrayList<>();
        String text = "";
        if (!stacks.isEmpty()) {
            parts.add(stacks.size() + "");
            parts.add(Misc.getWithDGS(space));
            text += stacks.size() + (stacks.size() == 1 ? " cargo type, " : " cargo types, ") + Misc.getWithDGS(space) + " cargo space.";
        }

        if (!variants.isEmpty()) {
            parts.add(" " + variants.size() + " ");
            parts.add(Misc.getWithDGS(dp) + " deployment");
            text += (text.isEmpty() ? "" : " ") + variants.size() + (variants.size() == 1 ? " ship, " : " ships, ") + Misc.getWithDGS(dp) + " deployment points.";
        }

        Color[] colors = new Color[parts.size()];
        java.util.Arrays.fill(colors, Ui.hl());
        Ui.runs(column.block(COST_ROW, SECTION_GAP), 0f, 0f, cw, Ui.INS15, Ui.gray(), Ui.LEFT, text.trim(), parts.toArray(new String[0]), colors);
    }

    private static void cargoCell(CustomPanelAPI p, float x, float y, float w, float h, CargoStackAPI stack) {
        UiImages.stack(p, x, y, w, getStackSprite(stack), (int) stack.getSize(), 0, stack.getDisplayName());
    }

    private static String cargoName(CargoStackAPI stack) {
        return Misc.getWithDGS(stack.getSize()) + " " + stack.getDisplayName();
    }

    private static String getStackSprite(CargoStackAPI stack) {
        if (stack.getResourceIfResource() != null) return stack.getResourceIfResource().getIconName();
        if (stack.getSpecialItemSpecIfSpecial() != null) return stack.getSpecialItemSpecIfSpecial().getIconName();
        if (stack.getWeaponSpecIfWeapon() != null) return stack.getWeaponSpecIfWeapon().getHardpointSpriteName();
        if (stack.getFighterWingSpecIfWing() != null) return stack.getFighterWingSpecIfWing().getVariant().getHullSpec().getSpriteName();
        return null;
    }

    private void addSchedule() {
        Ui.heading(column.block(Ui.HEADING_H, SECTION_GAP), 0f, 0f, cw, "Schedule", Ui.LEFT);
        CustomPanelAPI b = column.block(AREA_H, SECTION_GAP);
        for (int i = 0; i < CADENCES.length; i++) {
            int days = CADENCES[i];
            UiButtons.area(b, i * CADENCE_STEP, 0f, CADENCE_W, AREA_H, Misc.ucFirst(ShippingTooltipHelper.getCadenceString(days)),
                    draft.getRecurrentDays() == days, true, null, Ui.CENTER, act(() -> draft.setRecurrentDays(days)));
        }

        if (stored == null) return;

        String next = CourierPanelDelegate.getNextRun(draft);
        String line = next.equals("on demand") ? "Inactive, ships on demand" : "Next run " + next;
        Ui.textIn(b, NEXT_X, 0f, cw - NEXT_X - SHIP_NOW_W - 8f, AREA_H, line, Ui.INS15, Ui.gray(), Ui.LEFT,
                new String[]{next}, new Color[]{draft.isActive ? Ui.hl() : Ui.gray()});
        ButtonAPI now = UiButtons.button(b, cw - SHIP_NOW_W, 0f, SHIP_NOW_W, AREA_H, "Ship now", UiButtons.Kind.STD, act(() -> {
            stored.elapsedDays = 999;
            draft.elapsedDays = 999;
        }));
        Ui.tooltip(b, now, null, "The courier leaves the next day, also while the contract is inactive.");
    }

    private void addCost() {
        List<CostLine> lines = getCostLines();
        Ui.heading(column.block(Ui.HEADING_H, 10f), 0f, 0f, cw, "Cost per run", Ui.LEFT);

        CustomPanelAPI b = column.block(lines.size() * COST_ROW, SECTION_GAP);
        for (int i = 0; i < lines.size(); i++) addCostLine(b, i * COST_ROW, lines.get(i));

        CustomPanelAPI t = column.block(6f + UiData.COST_ROW_H, 4f);
        Ui.rule(t, 0f, 0f, cw);
        if (isSpecific(draft.scope)) UiData.creditsRow(t, 0f, 6f, cw, "Total", getSpecificTotal(draft));
        else UiData.value(t, 0f, 6f, cw, UiData.COST_ROW_H, "Total", "by load, known on the day", Ui.gray(), new String[0], new Color[0]);
    }

    private void addCostLine(CustomPanelAPI b, float y, CostLine line) {
        float vw = Math.min(Ui.width(line.value(), Ui.INS15) + 2f, cw * UiData.VALUE_SHARE);
        Ui.text(b, cw - vw, y, vw, line.value(), Ui.INS15, line.color(), Ui.RIGHT);

        float lx = 0f;
        if (line.sprite() != null) {
            UiImages.img(b, 0f, y, COST_ICON, COST_ICON, line.sprite());
            lx = COST_ICON + 4f;
        }

        String text = line.note() == null ? line.label() : line.label() + ", " + line.note();
        Ui.runs(b, lx, y, cw - vw - 8f - lx, Ui.INS15, Ui.text(), Ui.LEFT, text, new String[]{line.note()}, new Color[]{Ui.gray()});
    }

    private List<CostLine> getCostLines() {
        List<CostLine> lines = new ArrayList<>();
        float perSpace = Setting.COURIER_COST_PER_CARGO_SPACE.value();
        float perDp = Setting.COURIER_COST_PER_SHIP_DP.value();
        lines.add(new CostLine(null, "Base fee", null, Misc.getDGSCredits(Setting.COURIER_BASE_FEE.value()), Ui.hl()));

        SubmarketAPI from = draft.getFromSubmarket();
        boolean stockpile = from != null && Submarkets.LOCAL_RESOURCES.equals(from.getSpecId());
        if (isSpecific(draft.scope)) {
            float space = ShippingCostCalculator.getCargoSpace(draft.targetCargo);
            float dp = ShippingCostCalculator.getDeploymentPoints(draft.variantList);
            if (!draft.targetCargo.isEmpty()) lines.add(new CostLine(null, "Cargo", Misc.getWithDGS(space) + " space at " + Misc.getDGSCredits(perSpace),
                    Misc.getDGSCredits(space * perSpace), Ui.hl()));
            if (!draft.variantList.isEmpty()) lines.add(new CostLine(null, "Ships", Misc.getWithDGS(dp) + " deployment points at " + Misc.getDGSCredits(perDp),
                    Misc.getDGSCredits(dp * perDp), Ui.hl()));
            if (stockpile) lines.add(new CostLine(null, "Stockpile item cost", "value of the items taken", Misc.getDGSCredits(getStockpileCost(draft)), Ui.hl()));
        } else {
            boolean cargo = canCargo(draft) && draft.scope != ShippingContract.Scope.ALL_SHIPS;
            boolean ships = canShips(draft) && (draft.scope == ShippingContract.Scope.ALL_SHIPS || draft.scope == ShippingContract.Scope.EVERYTHING);
            if (cargo) lines.add(new CostLine(null, "Cargo cost", "per 1000 items", Misc.getDGSCredits(1000f * perSpace), Ui.hl()));
            if (ships) lines.add(new CostLine(null, "Ship cost", "per 10 DP", Misc.getDGSCredits(10f * perDp), Ui.hl()));
            if (stockpile) lines.add(new CostLine(null, "Stockpile item cost", "value of the items taken, today", Misc.getDGSCredits(getStockpileCost(draft)), Ui.hl()));
        }

        MarketAPI a = draft.getFromMarket();
        MarketAPI b = draft.getToMarket();
        float ly = Misc.getDistanceLY(a.getPrimaryEntity(), b.getPrimaryEntity());
        float mult = ShippingCostCalculator.getLYMult(a.getPrimaryEntity(), b.getPrimaryEntity(), false);
        lines.add(new CostLine(null, "Distance", Misc.getRoundedValueMaxOneAfterDecimal(ly) + " light years", "x" + Misc.getRoundedValueMaxOneAfterDecimal(mult), Ui.hl()));

        String core = ShippingTargetHelper.getMemoryAICoreId();
        if (Commodities.BETA_CORE.equals(core)) lines.add(new CostLine(UiImages.commoditySprite(core), Global.getSettings().getCommoditySpec(core).getName(),
                "on the distance multiplier", "-" + CourierPanelDelegate.percent(ShippingCostCalculator.DISTANCE_MULT_REDUCTION), Ui.pos()));
        if (Commodities.ALPHA_CORE.equals(core)) lines.add(new CostLine(UiImages.commoditySprite(core), Global.getSettings().getCommoditySpec(core).getName(),
                "on cargo and ships", "-" + CourierPanelDelegate.percent(ShippingCostCalculator.TOTAL_FEE_REDUCTION), Ui.pos()));
        return lines;
    }

    // what one run of a contract with selected goods costs today, stockpile items included
    static float getSpecificTotal(ShippingContract c) {
        float total = Setting.COURIER_BASE_FEE.value()
                + ShippingCostCalculator.getSpecificCargoCostForCargo(c.targetCargo, c, false)
                + ShippingCostCalculator.getShipCostForVariants(c.variantList, c);
        SubmarketAPI from = c.getFromSubmarket();
        if (from != null && Submarkets.LOCAL_RESOURCES.equals(from.getSpecId())) total += getStockpileCost(c);
        return total;
    }

    private static float getStockpileCost(ShippingContract c) {
        List<CargoStackAPI> stacks = isSpecific(c.scope) ? c.targetCargo.getStacksCopy() : c.getFromSubmarket().getCargo().getStacksCopy();
        float cost = 0f;
        for (CargoStackAPI stack : stacks) cost += stack.getBaseValuePerUnit() * stack.getSize();
        return cost;
    }

    private void addActions(CustomPanelAPI p, float x, float y, float w, boolean invalid) {
        if (stored != null) UiButtons.confirm(p, x, y, 140f, ACTION_H, "Remove", UiButtons.Kind.STD, view.confirm, "remove", () -> {
            ShippingContractMemory.removeContract(stored);
            view.selected = null;
            view.draft = null;
            view.scroll.reset(CourierView.EDITOR);
        }, panel::refresh);

        if (invalid) return;

        boolean save = isComplete();
        float cancelX = save ? x + w - 354f : x + w - 150f;
        UiButtons.button(p, cancelX, y, 150f, ACTION_H, "Cancel", UiButtons.Kind.STD, CutStyle.ALL, act(() -> {
            view.draft = null;
            view.expanded = null;
            if (view.creating) {
                view.creating = false;
                view.selected = null;
            }
        }));

        if (save) UiButtons.button(p, x + w - 200f, y, 200f, ACTION_H, "Save", UiButtons.Kind.STD, CutStyle.ALL, act(() -> {
            ShippingContractMemory.addOrReplaceContract(draft);
            if (view.creating) view.select(draft.getId());
            else view.draft = null;
        }));
    }

    static String getMarketLine(MarketAPI market) {
        String size = "Size " + market.getSize();
        return market.getStarSystem() != null ? size + " · " + market.getStarSystem().getBaseName() : size;
    }

    // three chevrons pointing right, 48 x 40 units
    private static void chevrons(PositionAPI pos, Color c, float alpha) {
        float x = pos.getX();
        float top = pos.getY() + pos.getHeight();
        float a = c.getAlpha() / 255f * alpha;

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
        GL11.glLineWidth(3f);
        for (int i = 0; i < 3; i++) {
            float ox = x + 4f + i * 14f;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            GL11.glVertex2f(ox, top - 6f);
            GL11.glVertex2f(ox + 14f, top - 20f);
            GL11.glVertex2f(ox, top - 34f);
            GL11.glEnd();
        }
        GL11.glLineWidth(1f);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
}
