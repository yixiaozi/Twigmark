tasks.register("listAntModules") {
    group = "verification"
    description = "List Ant modules that compile at Java 1.8."
    doLast {
        val root = rootProject.projectDir
        val builds = fileTree(root) {
            include("**/ant/build.xml", "freeplane_ant/build.xml")
        }.files.sortedBy { it.path }
        var ok = 0
        builds.forEach { f ->
            val text = f.readText()
            if (text.contains("java_source_version")) {
                check(text.contains("value=\"1.8\"")) {
                    "Expected java_source_version 1.8 in ${f.relativeTo(root)}"
                }
                ok++
                println("OK ${f.relativeTo(root)}")
            }
        }
        println("Checked $ok ant module(s) for Java 1.8.")
    }
}

tasks.register("check") {
    dependsOn("listAntModules")
}
