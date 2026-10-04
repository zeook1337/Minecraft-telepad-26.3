$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
function Pad-Cuboid($from, $to, $texture, $tint) {
    $faces = [ordered]@{}
    foreach ($face in @('up','down','north','south','east','west')) {
        $faces[$face] = if ($face -eq 'down') { @{texture='#bottom'} }
            else { @{texture=$texture;tintindex=$tint} }
    }
    return @{from=$from;to=$to;faces=$faces}
}
$model = [ordered]@{
    parent = 'minecraft:block/block'
    textures = [ordered]@{
        particle='#base'
        frame='telepads:block/telepad_frame'
        base='telepads:block/telepad_base'
        top='telepads:block/telepad_top'
        bottom='telepads:block/telepad_bottom'
    }
    elements = @(
        (Pad-Cuboid @(0,0,0) @(16,2,2) '#frame' 0),
        (Pad-Cuboid @(0,0,14) @(16,2,16) '#frame' 0),
        (Pad-Cuboid @(0,0,2) @(2,2,14) '#frame' 0),
        (Pad-Cuboid @(14,0,2) @(16,2,14) '#frame' 0),
        (Pad-Cuboid @(2,0,2) @(14,1.5,14) '#base' 1),
        # Separate the fixed Ender motif from the tintable frame and base rim.
        # The small height offset prevents coplanar flicker over the base.
        @{from=@(3,1.51,3);to=@(13,1.51,13);faces=@{up=@{texture='#top';uv=@(0,0,16,16)}}}
    )
    display = @{
        gui=@{rotation=@(30,225,0);translation=@(0,1,0);scale=@(.7,.7,.7)}
        ground=@{translation=@(0,3,0);scale=@(.4,.4,.4)}
    }
}
$path = Join-Path $project 'src/main/resources/assets/telepads/models/block/telepad.json'
[IO.File]::WriteAllText($path, ($model | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
