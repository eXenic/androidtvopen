# Opsiyonel Windows PC Uyku / Kapatma Dinleyicisi (Port 5050)
# Android TV uygulamasindan "PC'yi Uyut" tusuna basildiginda PC'yi uyku moduna alir.
# Ornek URL (Uygulama Ayarlarina yazilacak): http://10.0.0.132:5050/sleep

param(
    [int]$Port = 5050
)

$listener = New-Object System.Net.HttpListener
$listener.Prefixes.Add("http://+:$Port/")
$listener.Start()
Write-Host "PC Uyku Dinleyicisi calisiyor: http://0.0.0.0:$Port/sleep (veya /shutdown)"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $path = $ctx.Request.Url.AbsolutePath.ToLower()
    $resp = $ctx.Response
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("OK")
    $resp.StatusCode = 200
    $resp.OutputStream.Write($bytes, 0, $bytes.Length)
    $resp.Close()

    if ($path -eq "/sleep") {
        Write-Host "Uyku komutu alindi..."
        Start-Sleep -Milliseconds 300
        Add-Type -AssemblyName System.Windows.Forms
        [System.Windows.Forms.Application]::SetSuspendState("Suspend", $false, $false) | Out-Null
    } elseif ($path -eq "/shutdown") {
        Write-Host "Kapatma komutu alindi..."
        Start-Sleep -Milliseconds 300
        Stop-Computer -Force
    }
}
