package ru.e1173.nookast.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** A section title for TalkBack's heading navigation (plan 5.5). */
internal fun Modifier.sectionHeading(): Modifier = semantics { heading() }
