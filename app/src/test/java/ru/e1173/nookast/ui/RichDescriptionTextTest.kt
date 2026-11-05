package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RichDescriptionTextTest {
    @Test
    fun `joins wrapped prose but keeps real paragraphs`() {
        val source = "Первая длинная строка абзаца, которую редактор ленты перенёс посреди\nпредложения.\n\nНовый абзац."

        assertEquals(
            "Первая длинная строка абзаца, которую редактор ленты перенёс посреди предложения.\n\nНовый абзац.",
            normalizeDescriptionPlainText(source),
        )
    }

    @Test
    fun `keeps bullets and chapter timestamps as separate blocks`() {
        val source = "Вступление\n• Первый пункт\n• Второй пункт\n01:25 Первая тема\n1:02:03 Финал"

        assertEquals(
            "Вступление\n\n• Первый пункт\n\n• Второй пункт\n\n" +
                "01:25 Первая тема\n\n1:02:03 Финал",
            normalizeDescriptionPlainText(source),
        )
    }

    @Test
    fun `a time inside a chapter label is part of the label`() {
        // «Животные в Студии», episode 240 (user, 5.11.2026).
        val source = "– 01:33:55 «М3ГАН 2.0». Пародия;\n" +
            "– 01:42:58 «Счастливчик Гилмор 2» и золотая эпоха «Кино в 21:00 на СТС»."

        val chapters = extractDescriptionTimestamps(source)

        assertEquals(listOf(5_635_000L, 6_178_000L), chapters.map(DescriptionTimestamp::positionMs))
        assertEquals("«Счастливчик Гилмор 2» и золотая эпоха «Кино в 21:00 на СТС».", chapters[1].label)
    }

    @Test
    fun `several chapters on one line still count`() {
        val chapters = extractDescriptionTimestamps("(00:00 Вступление) (05:10 Новости) 12:00 Итоги")

        assertEquals(listOf(0L, 310_000L, 720_000L), chapters.map(DescriptionTimestamp::positionMs))
    }

    @Test
    fun `extracts ordered chapter timestamps and labels`() {
        val source = "00:45 — Вступление\n12:30 Главная тема\n1:02:03 Финал"

        assertEquals(
            listOf(
                DescriptionTimestamp(45_000L, "Вступление"),
                DescriptionTimestamp(750_000L, "Главная тема"),
                DescriptionTimestamp(3_723_000L, "Финал"),
            ),
            extractDescriptionTimestamps(source),
        )
    }

    @Test
    fun `deduplicates repeated chapter positions`() {
        assertEquals(
            listOf(DescriptionTimestamp(90_000L, "Начало")),
            extractDescriptionTimestamps("01:30 Начало\n01:30 Дубль"),
        )
    }

    @Test
    fun `bracketed timestamps give clean chapter titles`() {
        val chapters = extractDescriptionTimestamps("[00:00] Вступление\n[05:30] — Главная тема (\n[1:02:03] Финал")

        org.junit.Assert.assertEquals(listOf("Вступление", "Главная тема", "Финал"), chapters.map { it.label })
        org.junit.Assert.assertEquals(listOf(0L, 330_000L, 3_723_000L), chapters.map { it.positionMs })
    }

    @Test
    fun `timestamp list leaves the text but prose around it stays`() {
        val text = "Новый выпуск.\n\n[00:00] 01. Intro [00:32] 02. Mars\n\nПишите нам."
        assertEquals("Новый выпуск.\n\nПишите нам.", descriptionWithoutTimestampList(text))
    }

    @Test
    fun `a lone time in prose is not a chapter list`() {
        val text = "Обсуждаем на 12:30 главное."
        assertEquals(text, descriptionWithoutTimestampList(text))
        assertEquals(emptyList<DescriptionTimestamp>(), descriptionChapterList(text))
    }

    @Test
    fun `short lines keep their own line breaks`() {
        val source = "Треклист:\nBobina - Intro\nMars Shadow - Out There"
        assertEquals(source, normalizeDescriptionPlainText(source))
    }

    @Test
    fun `dotted times with three parts are timestamps`() {
        val chapters = extractDescriptionTimestamps("0.00.00 Начало\n0.26.57 Новости\nЦена 12.50")
        assertEquals(listOf(0L, 1_617_000L), chapters.map { it.positionMs })
        assertEquals("Новости", chapters[1].label.substringBefore('\n'))
    }
}
