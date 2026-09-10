# Quality strategy

Factweek optimizes for precision: publishing fewer defensible facts is preferable to publishing more uncertain candidates.

## Golden dataset

The first evaluation set will contain 100 manually labelled source items:

- 25 relevant technology facts
- 20 opinions or reactions
- 15 announcements without implementation
- 15 duplicates
- 15 conflicting reports
- 10 ambiguous cases

## Initial measures

- publication precision
- candidate recall
- duplicate reduction rate
- evidence-level accuracy
- percentage requiring human review
- model cost per published fact

The golden dataset must be versioned. Prompt or model changes are accepted only after running the same evaluation set and recording the result.
