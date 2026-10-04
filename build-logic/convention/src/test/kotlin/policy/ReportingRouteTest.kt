package io.github.davidru85.multiverse.buildlogic.policy

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `TEST-UNIT-031` — the vulnerability-reporting route is stated identically in `SECURITY.md` §10 and
 * `CONTRIBUTING.md` §10 (`REQ-SEC-007`, `AC-REQ-SEC-007-1`).
 *
 * `SECURITY.md` owns the route and `CONTRIBUTING.md` reproduces it verbatim. Any drift — a different
 * channel, a missing block, or a paraphrased route — is a finding, and each document's absence fails
 * closed rather than passing on the other one.
 */
class ReportingRouteTest {

    private val route =
        listOf(
            "> ### Reporting a vulnerability",
            ">",
            "> Report a suspected vulnerability **privately**. Do not open a public issue, discussion or pull request.",
            ">",
            "> **Route:** use GitHub's Private Vulnerability Reporting form on this repository — open the **Security** " +
                "tab and choose **Report a vulnerability**.",
            ">",
            "> **Disclosure:** keep the report private until a fix has been released.",
        )

    private val otherRoute =
        listOf(
            "> ### Reporting a vulnerability",
            ">",
            "> Report a suspected vulnerability privately.",
            ">",
            "> **Route:** open a GitHub Discussion on this repository and tag the maintainer.",
            ">",
            "> **Disclosure:** keep the report private until a fix has been released.",
        )

    private fun document(vararg lines: String): String = lines.joinToString("\n") + "\n"

    private fun security(route: List<String> = this.route): String =
        document(
            "## 10. Vulnerability reporting",
            "",
            "The block below is the project's vulnerability-reporting route.",
            "",
            *route.toTypedArray(),
            "",
            "**Assumption A-2**: the route is an assumption until the owner enables it.",
        )

    private fun contributing(route: List<String> = this.route): String =
        document(
            "## 10. Reporting a security vulnerability",
            "",
            "The block below is duplicated deliberately from `SECURITY.md` §10.",
            "",
            *route.toTypedArray(),
        )

    private fun scan(
        securityRoute: List<String> = route,
        contributingRoute: List<String> = route,
    ): List<Violation> {
        val root = kotlin.io.path.createTempDirectory("reporting-route").toFile()
        val securityFile = File(root, ReportingRoutePolicy.SECURITY).apply { parentFile.mkdirs(); writeText(security(securityRoute)) }
        val contributingFile =
            File(root, ReportingRoutePolicy.CONTRIBUTING).apply { parentFile.mkdirs(); writeText(contributing(contributingRoute)) }
        return ReportingRoutePolicy.scan(securityFile, contributingFile, root)
    }

    @Test
    fun `TEST-UNIT-031 identical routes pass`() {
        assertEquals(emptyList(), scan().map { it.toString() })
    }

    @Test
    fun `TEST-UNIT-031 a route that differs in substance is reported`() {
        val findings = scan(contributingRoute = otherRoute)

        assertTrue(findings.isNotEmpty(), "a paraphrased route must be reported")
        assertTrue(findings.any { it.reason.contains("CONTRIBUTING.md") }, findings.map { it.toString() }.toString())
    }

    @Test
    fun `TEST-UNIT-031 a missing block in either document fails closed`() {
        assertTrue(
            scan(contributingRoute = listOf("No route is stated here.")).isNotEmpty(),
            "CONTRIBUTING.md without the block must fail",
        )
        assertTrue(scan(securityRoute = listOf("No route is stated here.")).isNotEmpty(), "SECURITY.md without the block must fail")
    }
}
