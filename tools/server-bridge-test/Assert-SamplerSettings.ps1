param([Parameter(Mandatory=$true)][string]$On, [Parameter(Mandatory=$true)][string]$Off)
$ErrorActionPreference='Stop'
function Read-Configuration([string]$Path) {
    if ((Get-Item -LiteralPath $Path).Length -gt 512KB) { throw 'Oversized JFR settings' }
    $settings=[System.Xml.XmlReaderSettings]::new()
    $settings.DtdProcessing=[System.Xml.DtdProcessing]::Prohibit
    $settings.XmlResolver=$null
    $reader=[System.Xml.XmlReader]::Create([System.IO.Path]::GetFullPath($Path),$settings)
    try { $document=[System.Xml.XmlDocument]::new();$document.XmlResolver=$null;$document.Load($reader);return ,$document }
    finally { $reader.Dispose() }
}
$a=Read-Configuration $On
$b=Read-Configuration $Off
foreach($name in @('jdk.ExecutionSample','jdk.NativeMethodSample')) {
    if($a.SelectNodes("/configuration/event[@name='$name']").Count -ne 1 -or
       $b.SelectNodes("/configuration/event[@name='$name']").Count -ne 1) {
        throw 'Missing or duplicate sampler event'
    }
    $x=$a.SelectNodes("/configuration/event[@name='$name']/setting[@name='enabled']")
    $y=$b.SelectNodes("/configuration/event[@name='$name']/setting[@name='enabled']")
    if($x.Count -ne 1 -or $y.Count -ne 1 -or $x[0].InnerText -cne 'true' -or $y[0].InnerText -cne 'false') {
        throw 'Missing, duplicate or wrong sampler setting'
    }
    $x[0].InnerText='false'
}
if($a.OuterXml -cne $b.OuterXml) { throw 'Recorder changed outside two sampler enable bits' }
Write-Output '{"equalExceptTwoSamplerBits":true}'
