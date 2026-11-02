package fr.floran.vraisbonsplans.ui

import java.text.NumberFormat
import java.util.Locale

private val euroFormat = NumberFormat.getCurrencyInstance(Locale.FRANCE)

/** 27838 → "278,38 €" (French grouping with narrow no-break spaces). */
fun formatEuros(cents: Long): String = euroFormat.format(cents / 100.0)
