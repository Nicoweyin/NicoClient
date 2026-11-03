Add-Type -AssemblyName System.Drawing
$assetDir = Join-Path $PSScriptRoot '..\assets'
if (-not (Test-Path -LiteralPath $assetDir)) { New-Item -ItemType Directory -Path $assetDir | Out-Null }
$bitmap = New-Object System.Drawing.Bitmap 256, 256
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.Color]::Transparent)
$background = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 30, 28, 68))
$graphics.FillEllipse($background, 4, 4, 248, 248)
$handle = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 177, 143, 234)), 24
$handle.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$handle.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$graphics.DrawLine($handle, 69, 194, 165, 94)
$head = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 101, 226, 241))
$points = [System.Drawing.Point[]]@((New-Object System.Drawing.Point 82, 69), (New-Object System.Drawing.Point 108, 48), (New-Object System.Drawing.Point 148, 38), (New-Object System.Drawing.Point 190, 52), (New-Object System.Drawing.Point 217, 84), (New-Object System.Drawing.Point 221, 119), (New-Object System.Drawing.Point 200, 103), (New-Object System.Drawing.Point 180, 88), (New-Object System.Drawing.Point 157, 78), (New-Object System.Drawing.Point 127, 79), (New-Object System.Drawing.Point 100, 91))
$graphics.FillPolygon($head, $points)
$highlight = New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 184, 251, 255)), 6
$graphics.DrawLine($highlight, 110, 59, 148, 49)
$graphics.DrawLine($highlight, 148, 49, 182, 61)
$pngPath = Join-Path $assetDir 'icon.png'
$bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose(); $bitmap.Dispose(); $background.Dispose(); $handle.Dispose(); $head.Dispose(); $highlight.Dispose()
$png = [System.IO.File]::ReadAllBytes($pngPath)
$stream = [System.IO.File]::Create((Join-Path $assetDir 'icon.ico'))
$writer = New-Object System.IO.BinaryWriter($stream)
$writer.Write([uint16]0); $writer.Write([uint16]1); $writer.Write([uint16]1)
$writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0); $writer.Write([byte]0)
$writer.Write([uint16]1); $writer.Write([uint16]32); $writer.Write([uint32]$png.Length); $writer.Write([uint32]22)
$writer.Write($png); $writer.Dispose()
