package ru.e1173.nookast.testing

import org.junit.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Marks a scenario test that currently fails because of a recorded defect (a FINDINGS.md ID).
 *
 * Scenario tests are written before the fix (EXECUTION_PLAN 3.4). [KnownDefectRule] turns the
 * expected failure into a skipped test, so verification stays green and the report lists the
 * open defect; when the test starts passing it FAILS, so the annotation is removed together with
 * the fix and the scenario becomes a regression test.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class KnownDefect(val findingId: String, val reason: String)

class KnownDefectRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        val defect = description.getAnnotation(KnownDefect::class.java) ?: return base
        return object : Statement() {
            override fun evaluate() {
                val failure = runCatching { base.evaluate() }.exceptionOrNull()
                if (failure == null) {
                    throw AssertionError(
                        "${description.methodName} passes now: ${defect.findingId} looks fixed. " +
                            "Remove @KnownDefect so the scenario guards against regressions.",
                    )
                }
                if (failure is AssumptionViolatedException) throw failure
                throw AssumptionViolatedException(
                    "Known defect ${defect.findingId}: ${defect.reason} (${failure.message})",
                )
            }
        }
    }
}
