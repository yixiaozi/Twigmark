/**
 * Twigmark Gradle entry (phase 3).
 *
 * Full Windows packaging remains Ant-driven. This build provides inventory /
 * verification tasks that Cloud Agents and CI can run without deploying.
 */
rootProject.name = "twigmark"

include("modernization-check")
project(":modernization-check").projectDir = file("gradle/modernization-check")
