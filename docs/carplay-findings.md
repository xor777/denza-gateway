# CarPlay findings

Status: **hardware/OEM preparation confirmed; an active OEM Apple CarPlay
implementation is not confirmed**.

This page records the read-only investigation performed on 2026-07-24 against
the author's Denza / BYD DiLink 5.1 head unit and a separate static analysis of
a publicly distributed third-party CarPlay APK. It deliberately separates
evidence found in the car from evidence found in the third-party APK.

No package was installed, no system property or sysfs value was changed, no
Fission command that changes state was called, and no repository APK or
decompiled binary was added. The post-investigation Android crash buffer was
empty.

## Vehicle and firmware context

The inspected ADB target was `127.0.0.1:5555`:

```text
product: IVI
model: DiLink5_1
Android: 13
ro.boot.vm.name: cell2
ro.fission.mode: cell
fingerprint:
BYD-AUTO/IVI/IVI:13/TP1A.220624.014/eng.build20251214.220229:user/release-keys
```

These findings are firmware-specific. Package versions, Fission layout, and
regional feature selection can change independently.

## Result matrix

| Layer | Result | Meaning |
| --- | --- | --- |
| USB/kernel | **Confirmed** | The image has a loaded CarPlay-named extcon driver and two CarPlay USB routes. |
| BYD framework/diagnostics | **Confirmed** | BYD constants, a CarPlay source ID, icon resources, and a CarPlay DTC exist. |
| Visible Android `cell2` | **Blocked / absent** | No active Apple CarPlay/iAP2 service, Binder endpoint, frontend route, or protocol library was found. |
| Fission host/cbox | **Unknown** | Host services and screen-projection APIs exist outside the visible Android cell, but the shell cannot enumerate or inspect the host cell. |
| Third-party no-dongle APK | **Confirmed, separate implementation** | The inspected APK ships its own CarPlay/iAP2/AirPlay stack and remotely proxies MFi authentication. It does not require the OEM stack found in the car. |

The evidence supports a CarPlay-ready/common hardware and framework substrate.
It does not by itself prove that this regional firmware contains a complete,
licensed, activatable OEM CarPlay host.

## Kernel and USB evidence

`/proc/modules` contains:

```text
extcon_carplay_usb 40960 0 - Live
```

Two platform devices are exposed:

```text
/sys/devices/platform/odm/odm:extcon_carplay
/sys/devices/platform/odm/odm:extcon_carplay_acc
```

They are associated with the two USB controllers:

```text
11201000.usb0
11211000.usb1
```

Both devices link to the platform driver:

```text
/sys/bus/platform/drivers/carplay
```

Device-tree properties include:

```text
carplay,extcon-acc
carplay,extcon-pmic-vcdt
carplay,cable-mode
```

`/system/etc/init/ipod.rc` writes the
`odm:extcon_carplay/cmode` sysfs control in response to
`sys.ipo.disable.usb`. The `ipod` service name is likely MediaTek's power-off
(`IPO`) terminology, so the filename is not independent evidence of Apple iPod
support. Its use of the CarPlay-named USB route is still relevant. SELinux
labels the `cmode` node, and the ordinary ADB shell received `Permission denied`
when reading it.

Useful read-only checks:

```bash
export ADB_SERIAL=127.0.0.1:5555

adb -s "$ADB_SERIAL" shell 'cat /proc/modules | grep -i carplay'
adb -s "$ADB_SERIAL" shell \
  'find /sys/devices/platform -maxdepth 4 -iname "*carplay*" -print 2>/dev/null'
adb -s "$ADB_SERIAL" shell \
  'grep -R -a -i -l "carplay" /system/etc/init /vendor/etc/init /odm/etc/init 2>/dev/null'
```

Do not write `cmode` or `sys.ipo.disable.usb` as an exploratory test. Their
rollback semantics and effect on both USB controllers are not established.

## BYD framework and diagnostic evidence

Static inspection of the installed stock `PhoneCar.apk` found:

```text
BYDAutoInstrumentDevice.MUSIC_SOURCE_CARPLAY = 16
BYDAutoInstrumentDevice.MUSIC_SOURCE_ANDROIDAUTO = 17
DiLinkDiagnosticClientConstants.DTC_CARPLAY_TYPE = 100663307
```

`100663307` is `0x0600000B`. The vehicle image also contains this diagnostic
entry in `/system/etc/diagnostic_config.json`:

```text
dtc: 600000B
moduletype: CARPLAY-TYPE
destination: 0x111
description: Carplay
```

`PhoneCar.apk` contains:

```text
res/mipmap/icon_carplay.png
res/mipmap/dynasty_icon_carplay.png
res/mipmap/fs_icon_carplay.png
```

The extracted icon is a generic green `C/play` graphic rather than Apple
branding. No live source reference to those resources was found, so the icons
may be common-platform or unused regional assets.

These constants and resources show that the BYD platform model knows about
CarPlay. They are not an implementation of iAP2, MFi authentication, media
transport, or the CarPlay UI.

## Visible stock applications

### `com.byd.synclink` (`PhoneCar`)

```text
path: /system/app/PhoneCar
versionName: PlatUI_20104518_d6995bddb_251205L
uid: android.uid.system
SHA-256: 2fb59fcfaa1a0a3721ade870911ec22405c03da170777c5797a8cf7db6e12e4e
```

Its visible routing implements:

- ICCOA CarLink;
- Huawei HiCar;
- Honor phone link.

`MainGuideActivity` selects only those three paths.
`ChooseSDKActivity` selects SDK values `1`, `2`, and `3` for
HiCar/CarLink/Honor. The exported `AidlStateService` offers state queries and
actions such as `openApp`, `startConnect`, `gotoSupportAppList`, and `control`,
but none selects or starts Apple CarPlay.

No reference from this package to `extcon_carplay`, `cmode`, or
`sys.ipo.disable.usb` was found. A `persist.sys.auto.protocol.type` reference
belongs to the bundled Honor nearby SDK and is not a CarPlay activation hook.

### `com.byd.phonelink` (`PhoneLink`)

```text
path: /system/priv-app/PhoneLink
versionName: 2.1.0.3db43aa
uid: android.uid.system
SHA-256: ceb6198f893df6aac26f609888ffc7195cd188ad2a25e5d7cb54c64c9960314f
```

This stock package is not the third-party `PhoneScreen` application described
later. It is a persistent BYD MQTT/Binder bridge. Its configured modules are:

```text
DANCING
SKYSCREEN
NFVC
```

No CarPlay, iAP2, AirPlay, or Apple accessory implementation was found in it.

### `com.huiaichang.byd.desktop` (`ThunderCarplay.apk`)

```text
SHA-256: 52b227ceddd95aba6c37b0279d82e4b16e4bcf58306f2fa3e70c15fa04d6d22e
```

Despite the APK filename, this is the `雷石KTV` karaoke application. Its code
and assets use `com.thunder.ktvlite` and `ktvsky.com`. `CARPLAY_FLAVOR` is a
car-product build flavor, not Apple CarPlay. Treat this filename as a false
positive.

## Runtime state in the visible Android cell

The stock `com.byd.synclink` and `com.byd.phonelink` processes were running.
No process or Binder service matching Apple CarPlay, iAP2, or an Apple
accessory host was present. Runtime properties were:

```text
sys.carlink.connection=0
sys.carlink.display=0
sys.carlink.transport=0
persist.sys.auto.protocol.type=<unset>
```

The connected USB host device during the check was a BYD microphone, not an
iPhone. Android reports USB Host and USB Accessory support.

The absence of an Apple process in `cell2` is not proof that no host-side
implementation exists. It is evidence only about the visible Android cell.

## Fission host/cbox boundary

The IVI runs as Fission `cell2`:

```text
ro.boot.vm.name=cell2
ro.fission.mode=cell
ro.build.system.fission_single_os=0
```

The host-side process and Binder services visible from the cell are:

```text
u:r:fission_service:s0 root ... fission_service[ivi]
FissionGeneraySvc
FissionHostSvc
```

The image also contains:

```text
/system/bin/fission_screennproject
/system/lib64/libfission_sdk.so
fission_dpy_screen_projection_start
fission_dpy_screen_projection_stop
fission_cbox_set_property
fission_cbox_prop_event
shellByService
check_permission
```

`fission list --all`, `fission list --running`, and `fission getactive` returned
`Invalid or no response received` from inside `cell2`. `/cluster` is mounted but
not readable by the shell. `dumpsys FissionHostSvc` returned only
`This is FissionHostSvc`.

This makes a CarPlay implementation in the host/cbox layer architecturally
possible, especially because Fission already owns screen-projection plumbing.
No CarPlay/iAP2 string or service was found in the accessible Fission binaries,
so this remains an unverified hypothesis rather than a finding.

Do not call `fission_cbox_set_property`, `fission switch/start/stop`,
`fission_screennproject`, unknown Binder transactions, or host shell methods
without a separately reviewed command schema, rollback, and live-car stop
conditions.

## Third-party no-dongle implementation

A publicly distributed APK advertised as wireless Apple CarPlay, AirPlay, and
Android Auto for Chinese BYD systems was downloaded for static analysis only:

```text
filename: PhoneScreen.apk
package: com.autochips.carplayapp
versionName: 2.64
versionCode: 264
size: approximately 48 MiB
SHA-256: 4949bfae4111f5513c3b02984124bf1d05329d54fb16850c38dd8e98c459871c
```

Source listing:

- <https://modhub24.com/guides/firmware_685acd9d>
- <https://t.me/just_byd/258>

A community report describes the same application as paid, wireless-only,
dependent on the IVI hotspot, and usable on some DiLink 5 vehicles:

- <https://www.reddit.com/r/BYD/comments/1qtudfi/apple_carplay_and_android_auto_on_chinese_spec/>

That report is useful compatibility evidence, not proof about this Denza or
about the unknown seller's artifact.

The APK is signed by a third party rather than BYD:

```text
subject/issuer: CN=q, OU=sz, C=CN
certificate SHA-256:
CD:27:54:26:E2:84:2E:D4:EB:9F:80:8E:D0:E0:E8:53:
FC:B7:39:7A:C8:F2:20:B3:1E:FC:22:FD:52:59:64:EF
```

By comparison, both inspected stock BYD packages use the BYD
`auto_api@byd.com` certificate with SHA-256
`EF:E3:CA:8A:DA:0D:10:C6:55:C3:DF:99:10:AD:2E:BC:12:1A:47:D9:A6:35:84:34:EB:24:07:43:09:93:3E:FC`.

### Self-contained protocol stack

The third-party manifest declares:

```text
com.example.autoservice.carplay.CarplayService
com.example.autoservice.androidauto.AndroidAutoService
com.example.autoservice.airscreen.AirScreenService
```

The APK includes:

```text
lib/arm64-v8a/libcarplay_jni.so
lib/arm64-v8a/libairplayjni.so
lib/arm64-v8a/libgalreceiver_jni.so
lib/arm64-v8a/libmdnssd.so
```

Its Java/JNI boundary exposes `createIap2Link`, `CarPlayStartSession`,
`ChallengeResponse`, audio input/output, touch events, MediaCodec H.264
rendering, and CarPlay status transitions. Native symbols include iAP2
authentication, AirPlay receiver sessions, Bonjour services
`_airplay._tcp`, `_carplay-ctrl._tcp`, and `_mfi-config._tcp`, plus
`MFiPlatform_CopyCertificate` and `MFiPlatform_VerifySignature`.

This is a CarPlay host implemented by the APK. It is not a small launcher that
invokes the stock `PhoneCar` AIDL, and it does not need a hidden OEM CarPlay
frontend to provide its basic wireless path.

### Remote MFi authentication proxy

Apple's documented design uses an MFi Authentication IC: the accessory presents
an Apple certificate, receives a challenge, and returns a response signed by
the IC. CarPlay video uses MFi-SAP; its ephemeral key exchange is signed with
the Authentication IC's RSA key:

- <https://support.apple.com/guide/security/verifying-accessories-sec70a4f377d/web>
- <https://mfi.apple.com/en/faqs>

The inspected APK implements two MFi backends:

1. an optional USB `UKey` path that talks to an attached authentication device;
2. a network backend named `net-mfi`.

The network backend opens a raw TCP socket to `www.iamonroad.com` and tries
ports:

```text
1522, 1525, 1500, 1505, 1510, 1515
```

The observed application protocol is:

| Command | Direction | Meaning in the decompiled client |
| --- | --- | --- |
| `2` | server → APK | MFi certificate data |
| `3` | APK → server | authentication challenge |
| `4` | server → APK | signed challenge response |
| `98` / `99` | server → APK | status/error text |

`CarplayNative.ChallengeResponse()` delegates to this backend, and the network
implementation waits up to five seconds for command `4`. The iPhone therefore
sees a certificate and cryptographically valid response while the signer is
reached over the network. Media rendering remains local to the IVI; this code
does not send every CarPlay video frame through the MFi server.

“Cloud MFi” is not an Apple term and is not a documented Apple deployment
model. Static analysis cannot determine whether the remote service uses a real
MFi Authentication IC, extracted credentials, or another emulation. It proves
only that this APK forwards certificate/signature operations to the server.

### Security and lifecycle concerns

The APK requests broad capabilities including overlays, Accessibility access to
`com.android.systemui`, Wi-Fi/network management, log access, external-storage
management, and package installation. Some requested permissions are
signature/system permissions and should not be grantable to an ordinary
third-party APK on Android 13, but the remaining permissions are still
security-sensitive.

The MFi proxy connection is a raw Java TCP socket; no TLS layer was visible in
that client path. The client also constructs a request containing locale and
device/application identifiers. The application is operationally dependent on
the vendor's licensing and MFi service. Server loss, account revocation, or a
protocol change can disable CarPlay even if the APK remains installed.

Do not install this sample on the live vehicle without:

- confirming the exact APK hash and signature;
- reviewing the exact seller artifact rather than assuming it is this build;
- deciding whether Accessibility, overlay, storage, and install permissions
  are acceptable;
- capturing its network behavior in an isolated environment first;
- defining uninstall, data-removal, and hotspot rollback steps.

## What a seller's “application or firmware” may mean

The existence of a no-dongle product does not prove that the car has a dormant
OEM CarPlay implementation. At least three architectures are possible:

1. **Self-contained APK plus remote MFi**, as confirmed in the inspected
   `PhoneScreen.apk`.
2. **Privileged integration package**, where a firmware patch grants autostart,
   background survival, hotspot control, audio focus, steering-wheel controls,
   or full-screen behavior to a third-party CarPlay host.
3. **OEM/regional activation**, where a signed BYD module or host/cbox firmware
   exposes an already licensed implementation. The current car has supporting
   hardware/framework evidence, but this path has not been observed.

The first two explanations fit a product sold as an “app” or “app plus
firmware” without any external dongle. They do not require a hidden OEM CarPlay
binary.

## Safe next checks

### Exact seller artifact

The highest-value next input is the seller's APK, update package, product name,
or download URL. Perform a static comparison before installation:

```bash
shasum -a 256 Seller.apk
keytool -printcert -jarfile Seller.apk
jadx -d reverse/seller-carplay Seller.apk
```

Keep `Seller.apk` and JADX output under the ignored `reverse/` workbench. Compare:

- package and signing certificate;
- native libraries and iAP2/CarPlay symbols;
- server endpoints and licensing code;
- requested privileged permissions;
- references to Fission, cbox, BYD services, `extcon_carplay`, or stock package
  names.

### Passive iPhone USB test

With an unlocked iPhone and no third-party CarPlay app installed, connect each
data-capable USB port separately and capture:

```bash
adb -s "$ADB_SERIAL" shell 'dumpsys usb'
adb -s "$ADB_SERIAL" shell 'logcat -b kernel -d | tail -300'
adb -s "$ADB_SERIAL" shell \
  'logcat -d | grep -i -E "05ac|apple|carplay|iap2|extcon" | tail -300'
adb -s "$ADB_SERIAL" shell \
  'service list | grep -i -E "carplay|iap|projection"'
```

Apple's USB vendor ID `05ac`, an extcon transition, or a new host-side service
would narrow the OEM hypothesis. A lack of response in both visible Android
logs still would not exclude an inaccessible Fission host implementation.

### Regional firmware comparison

If a lawful firmware image from the same hardware platform and an officially
CarPlay-enabled region is available, compare package inventories, native
libraries, init files, SELinux service labels, and Fission/cbox payloads
offline. Do not flash a regional image merely to discover its contents.

## Current conclusion

The car has genuine internal CarPlay-oriented preparation at the
kernel/device-tree and BYD framework/diagnostic layers. The inspected visible
Android firmware does not expose a complete or activatable OEM Apple CarPlay
stack.

A full no-dongle CarPlay experience is nevertheless technically possible
without that OEM stack. The inspected third-party APK demonstrates one concrete
route: it implements the CarPlay host locally and proxies the MFi
certificate/signature operation to a remote service. Until the seller's exact
artifact is inspected or an iPhone produces host-side OEM activity, treat
“activation of built-in CarPlay” and “third-party software CarPlay” as separate
hypotheses.
