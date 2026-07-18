param(
    [string]$Voice = "en-US-AndrewNeural"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$buildDir = Join-Path $PSScriptRoot "build"
$narrationDir = Join-Path $PSScriptRoot "narration"
$tts = "C:\Users\wandt\AI_coding\.venv\Scripts\edge-tts.exe"
$ffmpeg = "C:\ffmpeg\bin\ffmpeg.exe"
$ffprobe = "C:\ffmpeg\bin\ffprobe.exe"

foreach ($required in @($tts, $ffmpeg, $ffprobe)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required executable not found: $required"
    }
}

New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

$scenes = @(
    @{ Id = "01-problem"; Image = "docs\assets\today-screen.png" },
    @{ Id = "02-handoff"; Image = "docs\assets\handoff-screen.png" },
    @{ Id = "03-share"; Image = "docs\assets\share-sheet.png" },
    @{ Id = "04-codex"; Image = "docs\assets\github-readme.png" }
)

$clips = @()
foreach ($scene in $scenes) {
    $id = $scene.Id
    $textPath = Join-Path $narrationDir "$id.txt"
    $audioPath = Join-Path $buildDir "$id.mp3"
    $captionPath = Join-Path $buildDir "$id.vtt"
    $clipPath = Join-Path $buildDir "$id.mp4"
    $imagePath = Join-Path $projectRoot $scene.Image

    & $tts --voice $Voice "--rate=-4%" --file $textPath --write-media $audioPath --write-subtitles $captionPath
    if ($LASTEXITCODE -ne 0) { throw "TTS failed for $id" }

    $duration = [double](& $ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $audioPath)
    $targetDuration = $duration + 0.8
    $videoFilter = "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2:color=0xfbf6ed,setsar=1,format=yuv420p"

    & $ffmpeg -y -loop 1 -i $imagePath -i $audioPath -vf $videoFilter -af "apad=pad_dur=0.8" -t $targetDuration -r 30 -c:v libx264 -preset medium -crf 20 -c:a aac -b:a 160k -movflags +faststart $clipPath
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg failed for $id" }
    $clips += $clipPath
}

$concatPath = Join-Path $buildDir "concat.txt"
$clips | ForEach-Object { "file '$($_.Replace("'", "''"))'" } | Set-Content -Path $concatPath -Encoding utf8
$finalPath = Join-Path $PSScriptRoot "build-week-demo.mp4"
& $ffmpeg -y -f concat -safe 0 -i $concatPath -c copy -movflags +faststart $finalPath
if ($LASTEXITCODE -ne 0) { throw "Final concat failed" }

$finalDuration = [double](& $ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 $finalPath)
if ($finalDuration -ge 180) { throw "Final video exceeds three minutes: $finalDuration seconds" }

Write-Output "Video: $finalPath"
Write-Output ("Duration: {0:N1}s" -f $finalDuration)
