# Table Paifu Contract Notes

This page is a frontend contract note for the paifu viewer. The backend game
engine should provide these fields directly in each paifu round; the frontend
must not infer them from visible replay state.

## Honba Count

`PaifuRoundSummary.descriptor.honba` is the number of honba sticks at the start
of that round. It is constant for the whole round and is displayed by the
frontend as-is.

The backend/game engine owns the honba transition rules:

- Abortive draw: next round honba increases by 1.
- Dealer win: next round honba increases by 1.
- Exhaustive draw: next round honba increases by 1, regardless of dealer tenpai.
- Non-dealer win: next round honba resets to 0.

Settlement score deltas must already be calculated with the current round's
`descriptor.honba`. For example, the frontend should not recalculate honba
payment from replay actions.

## Exhaustive Draw Tenpai Players

`PaifuRoundSummary.result.tenpaiPlayerIds` is required when
`result.outcome === "ExhaustiveDraw"`.

The frontend uses it to reveal final hands:

- players listed in `tenpaiPlayerIds` keep their hand visible;
- players not listed are shown as noten and their hand is turned face down.

Do not infer tenpai from score changes. Four-player tenpai and four-player
noten both produce `+0` score changes, so score deltas are not a valid source of
truth.

## Win Hand Value vs Score Changes

`PaifuRoundSummary.result.points` is the fixed value of the winning hand shown
in the win result panel. It is not the same thing as player score changes.

`result.points` should represent the hand's displayed win value, excluding
riichi-stick transfer, table supply bookkeeping, and per-player net settlement
details. The per-player movement belongs in `result.scoreChanges`.

Examples:

- Pure nine gates is always `64000` for a child win.
- Pure nine gates is always `96000` for a dealer win.
- If a retained riichi stick is awarded to the winner, include that transfer in
  `scoreChanges`; do not add it to `result.points`.

If the backend does not currently have this fixed hand value as a separate
field, it must be added to the paifu payload together with `scoreChanges`.

## Win Result Dora Indicators

The win result panel displays two fixed five-slot indicator rows:

- `result.doraIndicators`: the full five face-up dora indicators.
- `result.uraDoraIndicators`: the full five ura-dora indicators.
- `result.uraDoraVisible`: whether ura-dora should be revealed to the viewer.

`doraIndicators` and `uraDoraIndicators` should both be complete five-tile
arrays using tile codes such as `4z`, `0p`, or `5m`.

The frontend calculates how many indicator slots are visible from the complete
replay timeline. This must be a derived value, not mutable UI state: when a
round is loaded, the frontend can determine the visible count for every replay
step from the action sequence. The count starts at `1`. Each kan reveal
increases it by `1`, up to `5`. For open kan and added kan, the additional
indicator is revealed after the kan player discards. For closed kan, the
additional indicator is revealed when the kan player draws the rinshan tile. The
frontend displays only the first currently revealed entries and fills the rest
of the five slots with blue backs.

The ura-dora displayed count is the same as the frontend-calculated visible
dora count, but `uraDoraVisible` controls whether the tiles are shown. Usually
this is true only when the winning player has riichi. If `uraDoraVisible` is
false, the frontend shows five blue backs even if `uraDoraIndicators` is
present.

For demo records the frontend can infer visible dora from `DoraReveal` actions,
but production paifu records should provide the complete five-tile arrays and
ura visibility directly.

## Riichi Sticks

The current demo viewer derives visible riichi-stick movement from replay
actions so the UI can be tested before the engine contract is complete:

- accepted riichi declaration: player immediately pays 1000 and table riichi
  stick count increases by 1;
- if the riichi declaration discard is won by ron, the riichi payment is not
  accepted;
- riichi sticks stay on the table after exhaustive draw;
- riichi sticks are cleared when a later hand is won.

Long term, this should also come from the backend/game engine as explicit paifu
state or settlement metadata. The engine is the correct place to resolve edge
cases and guarantee that `finalStandings`, `scoreChanges`, riichi sticks, and
round transitions stay consistent.
