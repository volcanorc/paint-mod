package com.artmapcolorassistant;

public record SmartPreview(
        int oldEstimatedTicks,
        int believedManualEstimatedTicks,
        int smartEstimatedTicks,
        int expectedSavingsTicks,
        int manualActions,
        int dragActions,
        int bucketActions,
        int unsafeBucketCandidates,
        int wrongPixels,
        int componentCount,
        ArtMapColor dominantColor,
        ArtMapColor selectedBaseCoatColor,
        boolean baseCoatPlanned,
        String riskLevel
) {
    public String summary() {
        String dominant = dominantColor == null ? "none" : dominantColor.name() + "/" + dominantColor.item();
        String baseCoat = selectedBaseCoatColor == null ? "none" : selectedBaseCoatColor.name() + "/" + selectedBaseCoatColor.item();
        return "Smart preview: oldRowTicks=" + oldEstimatedTicks
                + " believedManualTicks=" + believedManualEstimatedTicks
                + " smartTicks=" + smartEstimatedTicks
                + " savings=" + expectedSavingsTicks
                + " dominant=" + dominant
                + " basecoat=" + baseCoat
                + " buckets=" + bucketActions
                + " dragRuns=" + dragActions
                + " manual=" + manualActions
                + " unsafeBuckets=" + unsafeBucketCandidates
                + " components=" + componentCount
                + " wrongPixels=" + wrongPixels
                + " risk=" + riskLevel + ".";
    }
}
