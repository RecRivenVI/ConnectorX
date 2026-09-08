# Mishka UI source attribution

Source: https://github.com/RecRivenVI/Mishka

Reference commit: `52f2a7c687fd68fd2617cf6775e26661005e61d2`.

ConnectorX incorporates the appearance/theme screen, theme configuration/labels, localized strings, grouped cards, adaptive layout, blur, navigation and liquid-glass helpers from this revision. The exact source paths, hashes and destinations are recorded in `Mishka-sources.json`.

Mishka's project license is GNU GPL version 3; the complete text is included in `Mishka-GPL-3.0.txt`. Files carrying an explicit Apache-2.0 header retain that header. The liquid-glass files credit compose-miuix-ui contributors and Kyant0/AndroidLiquidGlass as in the original sources; those notices have not been removed. The Apache-2.0 license is included as `Apache-2.0.txt`.

Adaptations: package/import namespace changed to ConnectorX; storage backed by the isolated `connectorx_appearance` preferences; the main shell contains Home and Settings only; ConnectorX device/session actions replace proxy-client actions. The theme settings screen itself retains the original body and resources, apart from namespace substitutions and an attribution comment. Selected application theme/navigation integration code was adapted to ConnectorX's lifecycle.

This notice does not replace applicable source licenses or declare unrelated pre-existing ConnectorX source to have a newly chosen license. Distribution of the combined work must respect the imported source licenses, including applicable GPL source obligations.

Since ConnectorX 0.5.1, shared UI sources and localized resources reside in shared/ui and are compiled for Android and Windows. Resource access uses Compose Resources; storage and device actions are platform adapters. Attribution and license files are included in both distributions.
