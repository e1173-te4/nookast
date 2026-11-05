package ru.e1173.nookast.core.network

import org.json.JSONArray
import org.json.JSONObject

/**
 * Null-safe replacement for [JSONObject.optString].
 *
 * Android's org.json returns the literal string "null" for an explicit JSON null (the JVM
 * org.json artifact returns the fallback instead, which hid the difference in unit tests, finding
 * N-02). Missing keys and JSON null both yield [fallback].
 */
fun JSONObject.optText(name: String, fallback: String = ""): String =
    if (!has(name) || isNull(name)) fallback else optString(name, fallback)

/** Like [optText] but returns null for missing, JSON null or blank values. */
fun JSONObject.optTextOrNull(name: String): String? =
    optText(name).takeIf { it.isNotBlank() }

/** Null-safe [JSONArray.optString]: JSON null elements become [fallback]. */
fun JSONArray.optText(index: Int, fallback: String = ""): String =
    if (isNull(index)) fallback else optString(index, fallback)
