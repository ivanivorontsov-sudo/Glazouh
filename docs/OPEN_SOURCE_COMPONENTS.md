# Open-source component research for ГлазУх

Research snapshot: 2026-11-17. This is a component-selection record, not a claim that every capability below is already integrated or validated.

## Recommended architecture

Keep the app native Kotlin/Compose and add optional, separately loaded on-device modules. Keep Tesseract OCR as the known-good baseline until alternatives pass side-by-side tests on Russian print, receipts, medicine packaging, low light, blur and perspective distortion.

| Need | Candidate | License / fit | Decision |
|---|---|---|---|
| General object detection | [MediaPipe Tasks Vision](https://github.com/google-ai-edge/mediapipe) + [EfficientDet-Lite0](https://developers.google.com/edge/mediapipe/solutions/vision/object_detector) | MediaPipe Tasks: Apache-2.0. The EfficientDet-Lite0 model asset used here is separately attributed under Apache-2.0. Android Kotlin API; 80 COCO categories. | First integrated vision module. Start with a single still image, then benchmark CameraX streaming. |
| Russian scene objects | [SightBuddy](https://github.com/DrophouseLtd/SightBuddy) | App code MIT. Third-party runtimes/models keep their own licenses. Active Android/Compose reference with MediaPipe Tasks, sherpa-onnx, model hashing and TalkBack-oriented UX. | Study architecture and tests; do not copy whole app or assume every bundled model is MIT. |
| Offline Russian speech | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) + [T-one Russian streaming model](https://k2-fsa.github.io/sherpa/onnx/pretrained_models/online-ctc/t-one-ctc-models.html) | Runtime Apache-2.0; T-one model Apache-2.0; streaming Kotlin API. T-one expects 8 kHz input and is telephony-domain trained. | Integrated as an opt-in Russian engine; model is downloaded on demand and SHA-256 verified. Outdoor/far-field quality still requires device tests. |
| Russian OCR alternative | [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR) | Apache-2.0. Official Android sample separates an SDK/AAR from the app and uses ONNX Runtime. PP-OCRv5 Cyrillic models include Russian. | Build an optional benchmark adapter; don't remove Tesseract until tests prove a measurable gain. |
| Ruble recognition | [Russian Banknote Dataset](https://huggingface.co/datasets/Dderedor/kupuri) | Dataset card says MIT. 949 images, 9 nominal values (5–5000 RUB), including multiple banknote generations. | Useful seed dataset, but too small for reliable real-world release claims without collecting permissioned test images and measuring false predictions. |
| Multi-currency research | [Microsoft BankNote-Net](https://github.com/microsoft/banknote-net) | Repository code is MIT; dataset of embeddings is CDLA-Permissive-2.0. It contains 24,816 embeddings spanning 17 currencies / 112 denominations; its encoder is MobileNetV2-based. | Useful research baseline. Do not treat the dataset/embeddings as MIT just because the repository code has an MIT file. |
| Traffic-light prototype | [VIA Safe Crosswalk](https://github.com/25-p-project-team-6/VIA-Safe-Crosswalk) | Android prototype, MIT application code; model/data files have separate terms. Korean signal context, TFLite/LiteRT, state stabilisation and conservative policy. | Study state-machine tests only. Do not reuse Korean model assumptions for Russian/European traffic or issue "GO" guidance. |
| Small vision-language model | [SmolVLM-256M](https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct) | Model card states Apache-2.0 for checkpoints. More practical to benchmark as a later opt-in download; mobile runtime, latency, memory and Russian response quality still need validation. | Research track, not a default dependency. |
| Offline scanner / document geometry | [MakeACopy](https://github.com/egdels/makeacopy), [DocScannerPro](https://github.com/REBEL-ROOT/DocScannerPro) | Both describe offline OCR / scanning flows; application and model/dependency licenses must be audited individually. | Useful reference for page-edge detection, perspective correction, OCR review and export. |

## License and provenance rules

1. The license of an app repository does not automatically cover its model files, training data, fonts, audio, native libraries or downloaded weights.
2. Preserve upstream LICENSE/NOTICE files and record the exact model revision, URL and SHA-256 before distributing a model.
3. Never download arbitrary serialized Python/Keras/HDF5 model files and load them as trusted code. Prefer audited TFLite, ONNX or MediaPipe task assets with pinned hashes.
4. For model downloads, use HTTPS, app-private storage, temporary files, size bounds, hash verification and atomic rename. A failed hash must fail closed.
5. Optional models must be downloadable only on request and deletable by the user; do not make large models part of the base APK unless there is a strong reason.

## Safety boundaries

- COCO's "traffic light" category only detects an object that resembles a traffic light. It does not establish which signal is lit, whether it applies to the pedestrian, whether the crossing is occupied, or whether crossing is safe.
- Banknote classification identifies a likely denomination, not authenticity, value condition or legal tender status. Low confidence and unknown designs must produce "не удалось определить", never a guessed value.
- Speaker diarization labels voice segments (for example "speaker 1" / "speaker 2"); it does not identify a real person.
- Image captioning can hallucinate. All outputs remain assistive hints, not sole evidence for medical, financial or road-safety decisions.

## Integration sequence

1. Complete and test the still-image COCO object detector with verified model download.
2. Add bilingual (Russian/English) object labels and accessibility tests.
3. Benchmark PaddleOCR beside Tesseract on a repeatable Russian/English test set.
4. Benchmark the opt-in T-one streaming speech engine on clean speech, phone calls, noisy rooms and far-field audio; keep Android system speech as an alternative.
5. Full now includes an opt-in offline diarization screen for recordings up to 3 minutes. It uses sherpa-onnx Kotlin API with pyannote segmentation and 3D-Speaker embeddings; both model downloads are size-bounded and SHA-256 verified. Output is limited to numbered voice segments, never person names. Real-device accuracy, battery use and memory pressure still require testing.
6. Train a ruble classifier only after reviewing dataset source, class balance, old/new note coverage and independent real-world test images.
7. Treat traffic-light analysis as a separate safety-critical research project with conservative state machines, extensive replay tests and outdoor validation.

## Sources

- MediaPipe Android Object Detector API: https://developers.google.com/edge/mediapipe/solutions/vision/object_detector
- MediaPipe Tasks setup and model loading: https://developers.google.com/edge/mediapipe/solutions/setup_android
- EfficientDet-Lite model files / hashes and model license notes: https://github.com/hugocornellier/object_detection
- sherpa-onnx Kotlin speaker diarization API: https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/kotlin-api-examples/test_offline_speaker_diarization.kt\n- sherpa-onnx speaker diarization models and model-size notes: https://k2-fsa.github.io/sherpa/onnx/speaker-diarization/models.html\n- pyannote segmentation model archive SHA-256: https://huggingface.co/onemira/pyannote-segmentation-3.0-onnx\n- 3D-Speaker embedding model hash and Apache-2.0 provenance: https://huggingface.co/onemira/eres2net-base-3dspeaker-onnx
- PaddleOCR Android deployment: https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/inference_deployment/cross_platform/android_deployment.en.md
- PaddleOCR multilingual PP-OCRv5 model language matrix: https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/algorithm/PP-OCRv5/PP-OCRv5_multi_languages.en.md
- Microsoft BankNote-Net dataset and license notes: https://github.com/microsoft/banknote-net
- Russian banknote dataset and class list: https://huggingface.co/datasets/Dderedor/kupuri
- VIA Safe Crosswalk: https://github.com/25-p-project-team-6/VIA-Safe-Crosswalk
- SightBuddy: https://github.com/DrophouseLtd/SightBuddy
- SmolVLM model card: https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct


## Additional repository audit (17 Nov 2026)

Two visually promising projects were deliberately excluded from code reuse after reading their actual repository files:

- [C Around](https://github.com/Cambric-software/c-around) advertises useful offline functions (YOLO camera awareness, YAMNet 521-class sound monitoring, face recognition and Whisper captions), but its checked-in `LICENSE` explicitly says **proprietary / all rights reserved / not open source**. Its public source is for inspection only; do not copy or derive code from it without written permission. It can be evaluated as a separate user-facing product, not as a source of code for ГлазУх.
- [Atlas Sight](https://github.com/Digitalbrainstem/atlas-sight) describes an offline accessibility assistant and shows an MIT badge in its README, but the repository's root file list and GitHub license metadata did not expose a LICENSE file/license identifier at the time checked. Do not import code until the copyright holder supplies a verifiable license file and the actual mobile/runtime/model provenance is audited.

### Ruble dataset note

[ Dderedor/kupuri ](https://huggingface.co/datasets/Dderedor/kupuri) is listed as MIT on Hugging Face and contains 886 training images plus validation/test splits (949 images total) across 9 denomination classes. It is a useful seed dataset, not a release-ready benchmark: the sample is small, and its coverage of banknote generations, folds, glare, blur, counterfeit lookalikes and worn notes must be independently assessed. Keep "unknown / cannot identify" as a valid outcome. Never claim authenticity detection from denomination classification.
