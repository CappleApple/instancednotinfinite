param(
    [Parameter(Mandatory = $true)][string]$Command,
    [string]$HostName = '127.0.0.1',
    [int]$Port = 25575,
    [string]$Password = 'ini-test'
)

$client = [System.Net.Sockets.TcpClient]::new()
try {
    $client.Connect($HostName, $Port)
    $stream = $client.GetStream()

    function Send-RconPacket([int]$RequestId, [int]$Type, [string]$Body) {
        $bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($Body)
        $length = 4 + 4 + $bodyBytes.Length + 2
        $memory = [System.IO.MemoryStream]::new()
        $writer = [System.IO.BinaryWriter]::new($memory, [System.Text.Encoding]::UTF8, $true)
        $writer.Write($length)
        $writer.Write($RequestId)
        $writer.Write($Type)
        $writer.Write($bodyBytes)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Flush()
        $packet = $memory.ToArray()
        $stream.Write($packet, 0, $packet.Length)
        $stream.Flush()
        $writer.Dispose()
        $memory.Dispose()
    }

    function Read-RconPacket {
        $reader = [System.IO.BinaryReader]::new($stream, [System.Text.Encoding]::UTF8, $true)
        $length = $reader.ReadInt32()
        $requestId = $reader.ReadInt32()
        $type = $reader.ReadInt32()
        $payloadLength = $length - 10
        $payload = [System.Text.Encoding]::UTF8.GetString($reader.ReadBytes($payloadLength))
        [void]$reader.ReadByte()
        [void]$reader.ReadByte()
        [pscustomobject]@{ RequestId = $requestId; Type = $type; Payload = $payload }
    }

    Send-RconPacket 1 3 $Password
    $auth = Read-RconPacket
    if ($auth.RequestId -eq -1) {
        throw 'RCON authentication failed'
    }
    Send-RconPacket 2 2 $Command
    $response = Read-RconPacket
    $response.Payload
}
finally {
    $client.Dispose()
}
