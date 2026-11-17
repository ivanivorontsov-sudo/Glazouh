# Third-party notices

ГлазУх uses or downloads the following separately licensed components.

## MediaPipe Tasks Vision

- Component: `com.google.mediapipe:tasks-vision:1.2.2`
- Copyright: Google LLC
- License: Apache License 2.0
- Source: https://github.com/google-ai-edge/mediapipe
- License text: https://www.apache.org/licenses/LICENSE-2.0

## EfficientDet-Lite0 float32 model

- Publisher: Google MediaPipe model repository
- File: `efficientdet_lite0.tflite`
- License: Apache License 2.0, as documented for the official MediaPipe model.
- Official download: https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite
- Expected size: 13,836,895 bytes
- Expected SHA-256: `40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58`
- Model is downloaded only after the user opens object detection. It is stored in app-private storage; the input photo is processed locally and is not uploaded.

## Existing components

- Tesseract4Android: upstream project and its own license: https://github.com/adaptech-cz/Tesseract4Android
- Tesseract language data: tessdata_fast project and its license: https://github.com/tesseract-ocr/tessdata_fast

This file is a convenience notice, not a replacement for the full upstream license texts. Each third-party component retains its own copyright and license. Before redistributing model files inside an APK or mirror, re-check the exact upstream model terms and retain all required notices.
