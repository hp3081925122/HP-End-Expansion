param([string]$LogName = 'gametest-latest.log')

# GameTest 引导崩溃可能仍返回进程零退出码，必须额外检查真实用例完成标记。
$taskRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
if ($LogName -notmatch '^[a-zA-Z0-9_-]+\.log$') { throw 'LogName must be a local log filename' }
$taskLogFolder = Join-Path $taskRoot 'build/prismatic'
New-Item -ItemType Directory -Path $taskLogFolder -Force | Out-Null
$taskLog = Join-Path $taskLogFolder $LogName
$env:JAVA_HOME = 'C:/Users/30819/.jdks/ms-21.0.11'
Push-Location $taskRoot
try {
    & ./gradlew.bat -PprismaticTests runGameTestServer --console=plain *> $taskLog
    $gradleResult = $LASTEXITCODE
    $taskText = Get-Content -LiteralPath $taskLog -Raw -Encoding UTF8
    $passed = [regex]::Matches($taskText, 'All (\d+) required tests passed')
    $failures = [regex]::Matches($taskText, '[1-9]\d* required tests failed')
    # 三组测试至少十九项；注册失败、用例缺失和未完成运行都明确失败。
    if ($gradleResult -ne 0 -or $passed.Count -eq 0 -or $failures.Count -gt 0 -or [int]$passed[$passed.Count - 1].Groups[1].Value -lt 19) {
        Get-Content -LiteralPath $taskLog -Encoding UTF8 | Select-String 'GAME TESTS|tests failed|/ERROR\]|Exception:|^> Task .*FAILED|^FAILURE|^BUILD FAILED' | Select-Object -Last 30
        Write-Output "Runtime validation failed. Inspect $taskLog"
        exit 1
    }
    Get-Content -LiteralPath $taskLog -Encoding UTF8 | Select-String 'GAME TESTS COMPLETE|required tests passed|Prismatic natural|Prismatic terrain parity|Prismatic altar'
    Write-Output "Runtime validation passed. Evidence: $taskLog"
} finally {
    Pop-Location
}
