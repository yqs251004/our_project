param(
  [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$sourceRoot = Join-Path $ProjectRoot 'src/main/scala'
if (-not (Test-Path -LiteralPath $sourceRoot)) {
  Write-Error "Scala source root not found: $sourceRoot"
  exit 2
}

$typeDefinitionPattern = '^[ \t]*(?:private(?:\[[^\]]+\])?[ \t]+|protected[ \t]+|final[ \t]+|sealed[ \t]+|abstract[ \t]+|case[ \t]+)*(?:class|trait|enum)[ \t]+[A-Za-z_][A-Za-z0-9_]*'
$functionPathPattern = '[\\/]functions[\\/]'

$results = New-Object System.Collections.Generic.List[object]

$files = Get-ChildItem -Path $sourceRoot -Recurse -Filter *.scala |
  Where-Object { $_.FullName -match $functionPathPattern }

foreach ($file in $files) {
  $lines = Get-Content -Encoding UTF8 -LiteralPath $file.FullName
  $inBlockComment = $false

  for ($index = 0; $index -lt $lines.Count; $index++) {
    $line = $lines[$index]
    $scanLine = $line

    while ($true) {
      if ($inBlockComment) {
        $commentEnd = $scanLine.IndexOf('*/')
        if ($commentEnd -lt 0) {
          $scanLine = ''
          break
        }

        $scanLine = $scanLine.Substring($commentEnd + 2)
        $inBlockComment = $false
        continue
      }

      $commentStart = $scanLine.IndexOf('/*')
      if ($commentStart -lt 0) {
        break
      }

      $commentEnd = $scanLine.IndexOf('*/', $commentStart + 2)
      if ($commentEnd -lt 0) {
        $scanLine = $scanLine.Substring(0, $commentStart)
        $inBlockComment = $true
        break
      }

      $scanLine = $scanLine.Substring(0, $commentStart) + $scanLine.Substring($commentEnd + 2)
    }

    $lineCommentStart = $scanLine.IndexOf('//')
    if ($lineCommentStart -ge 0) {
      $scanLine = $scanLine.Substring(0, $lineCommentStart)
    }

    if ($scanLine -match $typeDefinitionPattern) {
      $relativePath = $file.FullName.Substring($sourceRoot.Length + 1)
      $results.Add([pscustomobject]@{
        File = $relativePath
        Line = $index + 1
        Text = $line.Trim()
      })
    }
  }
}

if ($results.Count -eq 0) {
  Write-Output 'No type definitions found under functions directories.'
  exit 0
}

Write-Output "Found $($results.Count) type definition(s) under functions directories:"
$results |
  Sort-Object File, Line |
  ForEach-Object { "{0}:{1}: {2}" -f $_.File, $_.Line, $_.Text }
exit 1
