# Builds gtceu-modern-cape-fix-1.20.1-<version>.jar without Gradle, against the SRG-named jars in Prism's
# libraries folder. Mojang-named vanilla calls are renamed to SRG in a copy of the source first.
$ErrorActionPreference = 'Stop'
$version = '1.0.1'
$root = $PSScriptRoot
# A JDK 17 unzipped into .jdk\, or JAVA_HOME.
$jdk = if (Test-Path "$root\.jdk") { (Get-ChildItem "$root\.jdk" -Directory)[0].FullName } else { $env:JAVA_HOME }
$lib = "$env:APPDATA\PrismLauncher\libraries"

$classpath = @(
    "net\minecraft\client\1.20.1-20230612.114412\client-1.20.1-20230612.114412-srg.jar"
    "net\minecraftforge\forge\1.20.1-47.4.13\forge-1.20.1-47.4.13-universal.jar"
    "net\minecraftforge\forge\1.20.1-47.4.13\forge-1.20.1-47.4.13-client.jar"
    "net\minecraftforge\fmlcore\1.20.1-47.4.13\fmlcore-1.20.1-47.4.13.jar"
    "net\minecraftforge\fmlloader\1.20.1-47.4.13\fmlloader-1.20.1-47.4.13.jar"
    "net\minecraftforge\javafmllanguage\1.20.1-47.4.13\javafmllanguage-1.20.1-47.4.13.jar"
    "net\minecraftforge\eventbus\6.0.5\eventbus-6.0.5.jar"
    "com\mojang\authlib\4.0.43\authlib-4.0.43.jar"
    "com\mojang\logging\1.1.1\logging-1.1.1.jar"
    "org\slf4j\slf4j-api\2.0.1\slf4j-api-2.0.1.jar"
) | ForEach-Object { Join-Path $lib $_ }
# Dist and other FML API classes live in small jars; add any matching ones by name.
$classpath += Get-ChildItem $lib -Recurse -Filter *.jar |
    Where-Object { $_.Name -match '^(forgespi|mergetool-api|modlauncher)-' } |
    ForEach-Object FullName

# SRG names for the few vanilla methods called by name.
$srg = @{ '.getUUID()' = '.m_20148_()' }

$build = "$root\build"
Remove-Item $build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$build\src", "$build\classes" | Out-Null
Get-ChildItem "$root\src\main\java" -Recurse -Filter *.java | ForEach-Object {
    $text = Get-Content $_.FullName -Raw
    foreach ($k in $srg.Keys) { $text = $text.Replace($k, $srg[$k]) }
    Set-Content "$build\src\$($_.Name)" $text -NoNewline
}

& "$jdk\bin\javac" --release 17 -encoding UTF-8 -proc:none -cp ($classpath -join ';') -d "$build\classes" (Get-ChildItem "$build\src\*.java").FullName
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item "$root\src\main\resources\*" "$build\classes" -Recurse
$toml = "$build\classes\META-INF\mods.toml"
(Get-Content $toml -Raw).Replace('${version}', $version) | Set-Content $toml -NoNewline

$jar = "$build\gtceu-modern-cape-fix-1.20.1-$version.jar"
# Fixed entry timestamps: the jar doesn't record when it was built, and rebuilds are byte-identical.
& "$jdk\bin\jar" --create --date=1980-01-02T00:00:00Z --file $jar -C "$build\classes" .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
Write-Output $jar
