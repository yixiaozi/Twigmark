plugins {
    base
}

tasks.register("modernizationGate") {
    group = "verification"
    description = "Run scripts/verify-modernization.sh (no deploy)."
    doLast {
        val script = rootProject.file("scripts/verify-modernization.sh")
        if (!script.exists()) {
            throw GradleException("Missing ${script}")
        }
        exec {
            commandLine("bash", script.absolutePath)
        }
    }
}

tasks.named("check") {
    dependsOn("modernizationGate")
}

tasks.register("dependencyInventory") {
    group = "help"
    description = "Print path to dependency governance doc."
    doLast {
        println(rootProject.file("docs/modernization/DEPENDENCIES.md").absolutePath)
    }
}
