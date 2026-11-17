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


## sherpa-onnx Android runtime

- Component: `sherpa-onnx-2.0.1.aar`
- Copyright: k2-fsa contributors
- License: Apache License 2.0
- Source: https://github.com/k2-fsa/sherpa-onnx
- Release: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1
- SHA-256: `a8d09d1d32f3eb8520482c6d3d43fae674681c5b050385967c70a3c2c9bf0ee7`
- The AAR is downloaded and SHA-256 verified only while building the Full variant. It is not packaged in the Lite APK.

## T-one Russian streaming ASR model

- Model: `sherpa-onnx-streaming-t-one-russian-2025-09-08`
- Original project: https://github.com/voicekit-team/T-one
- License: Apache License 2.0 (T-Software DC)
- sherpa-onnx model documentation: https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/t-one-ctc-models.html
- Model URL: https://huggingface.co/csukuangfj/sherpa-onnx-streaming-t-one-russian-2025-09-08
- Expected model SHA-256: `5ded080e2a6c86ecc11bcb0902d77524eb3e8b0844cb0c0754347f5aafb4dabc`
- Model weights are downloaded only after the user explicitly selects T-one captions in the Full variant, kept in app-private storage, and verified before loading.
- The small token table is included in `app/src/full/assets/sherpa-t-one/tokens.txt` and has its own pinned SHA-256 check.
- T-one is trained primarily for telephony speech; accuracy in noisy outdoor or far-field conditions requires testing. It is not a speaker identity system.
