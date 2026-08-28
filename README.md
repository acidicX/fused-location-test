# fused-location-test

Minimal Android app that uses the native Android fused location provider (`LocationManager.FUSED_PROVIDER`) to display:

- current latitude
- current longitude
- lock status

The app requests `QUALITY_HIGH_ACCURACY` updates at a 2s interval (capped to 5s) so fused provider implementations that gate GPS usage by request quality/interval can activate GPS.

## Build

```bash
./gradlew assembleDebug
```