# QRCodeScanner For Android

一個簡單、快速的 Android QR 碼掃描器。

## 說明
本專案已轉型為通用型 QR 碼掃描器。

## 分支說明
- **main**: 最新版本的通用型 QR 碼掃描器（移除 1922 功能）。
- **old_android_6**: 支援 Android 6.0 的舊版本 APK。
- **2022_old**: 保留 1922 簡訊實聯制功能的最後版本。
- **darkness_2022_old**: 支援 1922 自動發送簡訊功能的最後版本（暗黑版）。

## 下載
官方版本請至 [Play 商店][1] 下載。

## 使用技術 (Library)
- QR 碼掃描與辨識使用 [Google ML Kit][6]。
- 早期版本曾使用 [zxing-android-embedded][4]。

## License

Licensed under the [Apache License 2.0][5]

	Copyright (C) 2021 YuJhen

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	    http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

[1]: https://play.google.com/store/apps/details?id=com.jack.qrcodefor1922
[4]: https://github.com/journeyapps/zxing-android-embedded
[5]: http://www.apache.org/licenses/LICENSE-2.0
[6]: https://developers.google.com/ml-kit/vision/barcode-scanning
