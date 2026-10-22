$ErrorActionPreference = 'Stop'
$projectPath = Split-Path $PSScriptRoot -Parent
$testClasses = Join-Path $projectPath '.local/test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null
$sourcePath = Join-Path $projectPath 'launcher/src/io/github/tvhome/universal'
$testSources = @('AppOrder.java','AppMovePolicy.java','GreetingText.java','HomeOverridePolicy.java') | ForEach-Object { Join-Path $sourcePath $_ }
& javac -encoding UTF-8 --release 11 -d $testClasses @testSources (Join-Path $projectPath 'tests/LogicTest.java')
if ($LASTEXITCODE -ne 0) { throw 'Host test compilation failed' }
& java -cp $testClasses io.github.tvhome.universal.LogicTest
if ($LASTEXITCODE -ne 0) { throw 'Host tests failed' }
