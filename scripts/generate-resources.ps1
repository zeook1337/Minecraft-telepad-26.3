$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$resources = Join-Path $project 'src/main/resources'
function Write-Json($relative, $value) {
    $path = Join-Path $resources $relative
    New-Item -ItemType Directory -Path (Split-Path -Parent $path) -Force | Out-Null
    [IO.File]::WriteAllText($path, ($value | ConvertTo-Json -Depth 30), [Text.UTF8Encoding]::new($false))
}
$reference = Join-Path (Split-Path -Parent $project) 'Telepad1.19.2/src/main/resources'
New-Item -ItemType Directory -Path (Join-Path $resources 'assets/telepads/textures/item') -Force | Out-Null
Get-ChildItem -LiteralPath (Join-Path $reference 'assets/telepads/textures/item') -Filter '*.png' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $resources 'assets/telepads/textures/item')
}
foreach ($name in @('ender_bead','ender_bead_necklace','transmitter','toggler','creative_rod','creative_rod_public')) {
    Write-Json "assets/telepads/models/item/$name.json" @{parent='minecraft:item/generated';textures=@{layer0="telepads:item/$name"}}
    Write-Json "assets/telepads/items/$name.json" @{model=@{type='minecraft:model';model="telepads:item/$name"}}
}
function Cuboid($from,$to,$texture,$tint=-1) {
    $faces = @{}
    foreach ($face in @('up','down','north','south','east','west')) {
        $faces[$face] = @{texture=$texture}
        if ($tint -ge 0) { $faces[$face]['tintindex'] = $tint }
    }
    return @{from=$from;to=$to;faces=$faces}
}
& (Join-Path $PSScriptRoot 'generate-telepad-model.ps1')
Write-Json 'assets/telepads/models/block/transmitter.json' @{textures=@{all='minecraft:block/lapis_block';particle='#all'};elements=@((Cuboid @(0,2,0) @(3,3.2,3) '#all'),(Cuboid @(13,2,13) @(16,3.2,16) '#all'))}
Write-Json 'assets/telepads/models/block/toggler.json' @{textures=@{all='minecraft:block/redstone_block';particle='#all'};elements=@((Cuboid @(0,2,13) @(3,3.2,16) '#all'))}
Write-Json 'assets/telepads/models/block/disabled.json' @{textures=@{all='minecraft:block/red_concrete';particle='#all'};elements=@((Cuboid @(3,2.1,7) @(13,2.5,9) '#all'))}
Write-Json 'assets/telepads/blockstates/telepad.json' @{multipart=@(@{apply=@{model='telepads:block/telepad'}},@{when=@{transmitter='true'};apply=@{model='telepads:block/transmitter'}},@{when=@{toggler='true'};apply=@{model='telepads:block/toggler'}},@{when=@{disabled='true'};apply=@{model='telepads:block/disabled'}})}
Write-Json 'assets/telepads/items/telepad.json' @{model=@{type='minecraft:model';model='telepads:block/telepad';tints=@(@{type='telepads:colors';part=0},@{type='telepads:colors';part=1})}}
foreach ($name in @('telepad','transmitter','toggler','necklace')) {
    $recipe = Get-Content -LiteralPath (Join-Path $reference "data/telepads/recipes/$name.json") -Raw | ConvertFrom-Json -AsHashtable
    if ($recipe['key']) { foreach ($key in @($recipe['key'].Keys)) { $recipe['key'][$key] = $recipe['key'][$key]['item'] } }
    if ($recipe['ingredients']) { $recipe['ingredients'] = @($recipe['ingredients'] | ForEach-Object { if ($_['item']) { $_['item'] } else { 'minecraft:string' } }) }
    $recipe['result'] = @{id=$recipe['result']['item'];count=1}
    Write-Json "data/telepads/recipe/$name.json" $recipe
}
Write-Json 'data/minecraft/tags/block/mineable/pickaxe.json' @{replace=$false;values=@('telepads:telepad')}
Write-Json 'data/telepads/recipe/telepad_dye.json' @{type='telepads:telepad_dye'}
$translations = @{'name.your.telepad'='screen.telepads.name';'button.forget'='screen.telepads.forget';'button.teleport'='screen.telepads.confirm_missing';'dragon.obstructs'='message.telepads.dragon_blocked';'no.power'='message.telepads.destination_disabled';'no.exp'='message.telepads.insufficient_xp';'pearl.inactive'='message.telepads.portable_disabled';'pearl.bounce'='message.telepads.no_portable_destination'}
Get-ChildItem -LiteralPath (Join-Path $reference 'assets/telepads/lang') -Filter '*.json' | Where-Object { $_.BaseName -ne 'en_us' } | ForEach-Object {
    $old = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json -AsHashtable
    $adapted = @{}
    foreach ($key in $old.Keys) {
        if ($key -like 'item.telepads.*' -or $key -eq 'block.telepads.telepad') { $adapted[$key] = $old[$key] }
        elseif ($translations.ContainsKey($key)) { $adapted[$translations[$key]] = $old[$key] }
    }
    $locale = if ($_.BaseName -eq 'en_uk') { 'en_gb' } else { $_.BaseName }
    Write-Json "assets/telepads/lang/$locale.json" $adapted
}
Write-Host 'Generated seven item definitions, platform models, five recipes and mining tag.'
