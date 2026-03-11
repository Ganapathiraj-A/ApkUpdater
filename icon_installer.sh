#!/bin/bash
ICON_SRC="/home/ganapathiraj/.gemini/antigravity/brain/4eeb4736-441e-4c4b-bda1-7247e7cb8f86/apk_updater_icon_source_1767234725315.png"
RES_DIR="app/src/main/res"

# Standard sizes - Renamed to force refresh
convert "$ICON_SRC" -resize 48x48 "$RES_DIR/mipmap-mdpi/ic_updater_app_icon.png"
convert "$ICON_SRC" -resize 72x72 "$RES_DIR/mipmap-hdpi/ic_updater_app_icon.png"
convert "$ICON_SRC" -resize 96x96 "$RES_DIR/mipmap-xhdpi/ic_updater_app_icon.png"
convert "$ICON_SRC" -resize 144x144 "$RES_DIR/mipmap-xxhdpi/ic_updater_app_icon.png"
convert "$ICON_SRC" -resize 192x192 "$RES_DIR/mipmap-xxxhdpi/ic_updater_app_icon.png"

# Round icons (apply circle mask)
MASK="/tmp/circle_mask.png"
convert -size 512x512 xc:none -fill white -draw "circle 256,256 256,0" "$MASK"

function make_round {
    local size=$1
    local out=$2
    local tmp_icon="/tmp/tmp_icon_$size.png"
    local tmp_mask="/tmp/tmp_mask_$size.png"
    
    convert "$ICON_SRC" -resize ${size}x${size} "$tmp_icon"
    convert "$MASK" -resize ${size}x${size} "$tmp_mask"
    
    convert "$tmp_icon" "$tmp_mask" -alpha off -compose CopyOpacity -composite "$out"
    rm "$tmp_icon" "$tmp_mask"
}

make_round 48 "$RES_DIR/mipmap-mdpi/ic_updater_app_icon_round.png"
make_round 72 "$RES_DIR/mipmap-hdpi/ic_updater_app_icon_round.png"
make_round 96 "$RES_DIR/mipmap-xhdpi/ic_updater_app_icon_round.png"
make_round 144 "$RES_DIR/mipmap-xxhdpi/ic_updater_app_icon_round.png"
make_round 192 "$RES_DIR/mipmap-xxxhdpi/ic_updater_app_icon_round.png"

rm "$MASK"
# Remove old icons and adaptive XMLs that might override the PNGs
find app/src/main/res -name "ic_launcher*" -delete
rm -f "$RES_DIR/mipmap-anydpi-v26/ic_updater_app_icon.xml"
rm -f "$RES_DIR/mipmap-anydpi-v26/ic_updater_app_icon_round.xml"
echo "Renamed PNG icons installed successfully (adaptive XMLs removed)."
