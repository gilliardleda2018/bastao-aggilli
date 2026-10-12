# Roda todas as etapas do CI no Docker e mostra um resumo. Uso: .\testar-tudo.ps1 [etapa ...]
# A primeira vez demora (baixa compilador do ESP32 e SDK do Android); depois fica no cache.
param([string[]]$Etapas = @('firmware-testes', 'integracao', 'firmware', 'android'))

Set-Location $PSScriptRoot
$resultados = foreach ($etapa in $Etapas) {
    Write-Host "`n=== $etapa ===" -ForegroundColor Cyan
    $inicio = Get-Date
    # Via cmd: o PowerShell 5.1 mostra como erro tudo o que o docker escreve no stderr (progresso)
    cmd /c "docker compose run --rm --build $etapa 2>&1"
    [pscustomobject]@{
        Etapa     = $etapa
        Resultado = if ($LASTEXITCODE -eq 0) { 'OK' } else { "FALHOU ($LASTEXITCODE)" }
        Tempo     = '{0:mm\:ss}' -f ((Get-Date) - $inicio)
    }
}

$resultados | Format-Table -AutoSize
if (Test-Path saida) { Get-ChildItem saida -Recurse -File | Select-Object FullName, Length | Format-Table -AutoSize }
if ($resultados.Resultado -ne 'OK') { exit 1 }
