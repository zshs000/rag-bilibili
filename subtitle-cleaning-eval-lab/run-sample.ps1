param(
    [Parameter(Mandatory = $true)]
    [string]$Url,
    [string]$Sessdata = "5f340aa5%2C1792597618%2C822d9%2A41CjBnpERFidtJwc39a4ar49ujkqR3Dk_65GEKsaOvLM8Qi_fbys_KdqqaoDkBXomk5M4SVjVGTjA2ZWQ0YUpvVnc0bVgycDNYM0IxbU4xYXlDaUotTlIwLWEtOW1nTGVoVmdKbEp5aFpqMmZ4LTZBaEQ0NVNYSjhhUWk0czc2S1FrUUljLWl0XzBBIIEC",
    [string]$BiliJct = "e265d754e697c8f3a893045cf850b5f3",
    [string]$Buvid3 = "8A0DD77C-8B56-04F2-6F51-95457610193736529infoc",
    [string]$OutputDir = "outputs\\latest",
    [string]$Output = "report.txt"
)

if ([string]::IsNullOrWhiteSpace($Sessdata)) {
    throw "Missing Sessdata."
}
if ([string]::IsNullOrWhiteSpace($BiliJct)) {
    throw "Missing BiliJct."
}

mvn -q compile exec:java `
  "-Dexec.mainClass=com.example.subtitleeval.runner.SubtitleCleaningRunner" `
  "-Dexec.args=--url=$Url --sessdata=$Sessdata --bili-jct=$BiliJct --buvid3=$Buvid3 --output-dir=$OutputDir --output=$Output"
