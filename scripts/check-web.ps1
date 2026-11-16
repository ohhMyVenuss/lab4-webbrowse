param([string]$Server = "192.168.1.100", [int]$Port = 8080)
Write-Host "Checking LTM Web Server $Server`:$Port" -ForegroundColor Cyan
$result = Test-NetConnection -ComputerName $Server -Port $Port -WarningAction SilentlyContinue
if (-not $result.TcpTestSucceeded) { Write-Host "TCP connection failed. Check server, IP and Firewall." -ForegroundColor Red; exit 1 }
Write-Host "TCP port reachable. Testing HTTP endpoints..." -ForegroundColor Green
$base = "http://$Server`:$Port"
foreach ($path in @("/", "/files/document.pdf", "/files/music.mp3", "/files/video.mp4", "/download/document.pdf")) {
  try { $r = Invoke-WebRequest -Uri ($base + $path) -Method Head -UseBasicParsing -TimeoutSec 10; [pscustomobject]@{Path=$path; Status=$r.StatusCode; Type=$r.Headers["Content-Type"]} }
  catch { Write-Warning "$path : $($_.Exception.Message)" }
}
