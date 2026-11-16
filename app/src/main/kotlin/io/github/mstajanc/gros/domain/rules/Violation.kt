package io.github.mstajanc.gros.domain.rules

/** Porušenie invariantu dátového modelu. Invarianty stráži repozitár, nie UI. */
enum class Violation {
    // Sumy
    AMOUNT_NOT_POSITIVE,
    AMOUNT_TOO_LARGE,

    // Spoločné
    NAME_BLANK,
    NOT_FOUND,

    // Zápis (transakcia alebo opakované pravidlo)
    ACCOUNT_NOT_FOUND,
    ACCOUNT_ARCHIVED,
    CATEGORY_MISSING,
    CATEGORY_NOT_FOUND,
    CATEGORY_KIND_MISMATCH,
    TARGET_ACCOUNT_NOT_ALLOWED,
    TRANSFER_TARGET_MISSING,
    TRANSFER_TARGET_NOT_FOUND,
    TRANSFER_SAME_ACCOUNT,
    TRANSFER_WITH_CATEGORY,
    INTERVAL_NOT_POSITIVE,

    // Účty
    LAST_ACTIVE_ACCOUNT,
    ACCOUNT_IN_USE,

    // Kategórie
    PARENT_NOT_FOUND,
    PARENT_NOT_MAIN,
    PARENT_KIND_MISMATCH,
    COLOR_OUT_OF_RANGE,
    SUBCATEGORY_COLOR_INHERITED,
    LAST_ACTIVE_MAIN_CATEGORY,
    CATEGORY_HAS_SUBCATEGORIES,
    CATEGORY_IN_USE,

    // Rozpočty
    BUDGET_CATEGORY_NOT_MAIN,
    BUDGET_CATEGORY_NOT_EXPENSE,
    BUDGET_ALREADY_EXISTS,
}

/** Vyhodí repozitár, keď by operácia porušila invariant; dáta ostávajú nezmenené. */
class InvariantViolationException(val violation: Violation) :
    IllegalStateException("Porušený invariant: $violation")

/** Pomocník pre repozitáre: `null` znamená „v poriadku“. */
fun Violation?.throwIfViolated() {
    if (this != null) throw InvariantViolationException(this)
}
