@echo off
set freeplanedir=%~dp0
set xargs=init.xargs
set defines= "-Dorg.freeplane.param1=%~1" "-Dorg.freeplane.param2=%~2" "-Dorg.freeplane.param3=%~3" "-Dorg.freeplane.param4=%~4"
set resdir="-Dorg.freeplane.globalresourcedir=%freeplanedir%resources/"
set java_exe=java
@echo on
"%java_exe%" -Xmx512m -Xss2m "-Dorg.knopflerfish.framework.bundlestorage=memory" "-Dorg.knopflerfish.gosg.jars=reference:file:%freeplanedir%core/" %resdir% %defines% -jar "%freeplanedir%framework.jar" -xargs "%freeplanedir%props.xargs" -xargs "%freeplanedir%%xargs%"
